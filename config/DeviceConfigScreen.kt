package com.craneremote.ui.screens.config

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.craneremote.domain.model.*
import com.craneremote.domain.usecase.*
import com.craneremote.ui.navigation.Screen
import com.craneremote.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

data class DeviceConfigState(
    val device: Device? = null,
    val subsystems: List<Subsystem> = emptyList(),
    val buttons: List<FixedButton> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class DeviceConfigViewModel @Inject constructor(
    private val getDeviceById: GetDeviceByIdUseCase,
    private val saveDevice: SaveDeviceUseCase,
    private val updateDevice: UpdateDeviceUseCase,
    private val getSubsystems: GetSubsystemsUseCase,
    private val replaceSubsystems: ReplaceSubsystemsUseCase,
    private val getButtons: GetFixedButtonsUseCase,
    private val replaceButtons: ReplaceButtonsUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(DeviceConfigState())
    val state: StateFlow<DeviceConfigState> = _state.asStateFlow()

    fun load(deviceId: String) {
        if (deviceId == Screen.DeviceConfig.NEW) {
            _state.update { it.copy(isLoading = false) }
            return
        }
        viewModelScope.launch {
            try {
                val device = getDeviceById(deviceId).filterNotNull().first()
                val subs   = getSubsystems(deviceId).first()
                val btns   = getButtons(deviceId).first()
                _state.update { it.copy(device = device, subsystems = subs, buttons = btns, isLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun saveAll(
        device: Device,
        subsystems: List<Subsystem>,
        buttons: List<FixedButton>
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            try {
                if (_state.value.device == null) {
                    saveDevice(device, subsystems, buttons)
                } else {
                    updateDevice(device)
                    replaceSubsystems(device.id, subsystems)
                    replaceButtons(device.id, buttons)
                }
                _state.update { it.copy(isSaving = false, saved = true) }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceConfigScreen(
    navController: NavController,
    deviceId: String,
    viewModel: DeviceConfigViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val isNew = deviceId == Screen.DeviceConfig.NEW

    // Campos do dispositivo
    var name    by remember { mutableStateOf("") }
    var ip      by remember { mutableStateOf("") }
    var port    by remember { mutableStateOf("102") }
    var plcType by remember { mutableStateOf(PlcType.SIEMENS_S7_1200) }
    var rack    by remember { mutableStateOf("0") }
    var slot    by remember { mutableStateOf("1") }
    var desc    by remember { mutableStateOf("") }
    var plcExpanded by remember { mutableStateOf(false) }

    // Subsistemas (editáveis)
    var subsystems by remember { mutableStateOf(listOf<Subsystem>()) }
    // Botões fixos
    var buttons by remember { mutableStateOf(listOf<FixedButton>()) }

    val tempId = remember { UUID.randomUUID().toString() }

    LaunchedEffect(deviceId) { viewModel.load(deviceId) }

    LaunchedEffect(state.device) {
        state.device?.let { d ->
            name = d.name; ip = d.ipAddress; port = d.port.toString()
            plcType = d.plcType; rack = d.rack.toString(); slot = d.slot.toString(); desc = d.description
        }
    }
    LaunchedEffect(state.subsystems) { subsystems = state.subsystems }
    LaunchedEffect(state.buttons) {
        buttons = if (state.buttons.isEmpty()) DefaultButtons.defaults(state.device?.id ?: tempId)
                  else state.buttons
    }
    LaunchedEffect(state.saved) {
        if (state.saved) navController.popBackStack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "Novo Dispositivo" else "Configurar Dispositivo") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Primary, titleContentColor = Color.White)
            )
        }
    ) { pad ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // ══════════════════════════════════════════════════
            // SEÇÃO 1 — Dispositivo
            // ══════════════════════════════════════════════════
            SectionHeader("1. DISPOSITIVO / PLC")

            OutlinedTextField(name, { name = it }, label = { Text("Nome *") }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(ip,   { ip   = it }, label = { Text("IP *") },   modifier = Modifier.weight(2f), placeholder = { Text("192.168.1.100") })
                OutlinedTextField(port, { port = it }, label = { Text("Porta") }, modifier = Modifier.weight(1f))
            }

            ExposedDropdownMenuBox(plcExpanded, { plcExpanded = it }) {
                OutlinedTextField(
                    plcType.name.replace("_", " "), {},
                    readOnly = true, label = { Text("Tipo PLC") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(plcExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(plcExpanded, { plcExpanded = false }) {
                    PlcType.entries.forEach { t ->
                        DropdownMenuItem({ Text(t.name.replace("_", " ")) }, { plcType = t; plcExpanded = false })
                    }
                }
            }

            if (plcType.name.startsWith("SIEMENS")) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(rack, { rack = it }, label = { Text("Rack") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(slot, { slot = it }, label = { Text("Slot") }, modifier = Modifier.weight(1f))
                }
            }

            OutlinedTextField(desc, { desc = it }, label = { Text("Descrição") }, modifier = Modifier.fillMaxWidth())

            // ══════════════════════════════════════════════════
            // SEÇÃO 2 — Subsistemas
            // ══════════════════════════════════════════════════
            SectionHeader("2. SUBSISTEMAS (GANCHOS / TRANSLAÇÃO)")

            Text(
                "Ganchos (VERTICAL) → joystick esquerdo\nBridge/Trolley (HORIZONTAL) → joystick direito",
                style = MaterialTheme.typography.bodySmall,
                color = Secondary
            )

            subsystems.forEachIndexed { idx, sub ->
                SubsystemEditor(
                    subsystem = sub,
                    onUpdate  = { updated -> subsystems = subsystems.toMutableList().also { it[idx] = updated } },
                    onDelete  = { subsystems = subsystems.toMutableList().also { it.removeAt(idx) } }
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick  = {
                        val devId = state.device?.id ?: tempId
                        subsystems = subsystems + Subsystem(deviceId = devId, name = "MH${subsystems.count { it.axis == SubsystemAxis.VERTICAL } + 1}", axis = SubsystemAxis.VERTICAL, order = subsystems.size)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text("+ Gancho")
                }
                OutlinedButton(
                    onClick  = {
                        val devId = state.device?.id ?: tempId
                        val horCount = subsystems.count { it.axis == SubsystemAxis.HORIZONTAL }
                        val names = listOf("Bridge", "Trolley 1", "Trolley 2", "Trolley 3")
                        subsystems = subsystems + Subsystem(deviceId = devId, name = names.getOrElse(horCount) { "Horiz ${horCount+1}" }, axis = SubsystemAxis.HORIZONTAL, order = subsystems.size)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text("+ Translação")
                }
            }

            // ══════════════════════════════════════════════════
            // SEÇÃO 3 — Botões Fixos
            // ══════════════════════════════════════════════════
            SectionHeader("3. BOTÕES FIXOS")

            Text(
                "Configure até 8 botões permanentes (sirene, luzes, etc.)",
                style = MaterialTheme.typography.bodySmall,
                color = Secondary
            )

            buttons.forEachIndexed { idx, btn ->
                FixedButtonEditor(
                    button   = btn,
                    onUpdate = { updated -> buttons = buttons.toMutableList().also { it[idx] = updated } }
                )
            }

            // ══════════════════════════════════════════════════
            // SALVAR
            // ══════════════════════════════════════════════════
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = {
                    val devId = state.device?.id ?: tempId
                    val device = Device(
                        id          = devId,
                        name        = name.trim(),
                        ipAddress   = ip.trim(),
                        port        = port.toIntOrNull() ?: 102,
                        plcType     = plcType,
                        rack        = rack.toIntOrNull() ?: 0,
                        slot        = slot.toIntOrNull() ?: 1,
                        description = desc
                    )
                    viewModel.saveAll(
                        device,
                        subsystems.mapIndexed { i, s -> s.copy(deviceId = devId, order = i) },
                        buttons.map { it.copy(deviceId = devId) }
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled  = name.isNotBlank() && ip.isNotBlank() && !state.isSaving,
                colors   = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                if (state.isSaving) { CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                Text("SALVAR CONFIGURAÇÃO", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ─── SubsystemEditor ──────────────────────────────────────────────────────────

@Composable
private fun SubsystemEditor(subsystem: Subsystem, onUpdate: (Subsystem) -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = SurfaceVariant),
        shape    = RoundedCornerShape(8.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(if (subsystem.axis == SubsystemAxis.VERTICAL) HoistGreen else MoveBlue, shape = RoundedCornerShape(4.dp))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (subsystem.axis == SubsystemAxis.VERTICAL) "GANCHO" else "TRANSLAÇÃO",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (subsystem.axis == SubsystemAxis.VERTICAL) HoistGreen else MoveBlue
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(28.dp)) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = Secondary)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, null, tint = Disconnected)
                }
            }

            // Nome
            OutlinedTextField(
                value         = subsystem.name,
                onValueChange = { onUpdate(subsystem.copy(name = it)) },
                label         = { Text("Nome") },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true
            )

            if (expanded) {
                val posLabel = if (subsystem.axis == SubsystemAxis.VERTICAL) "Tag SUBIR"   else "Tag DIREITA"
                val negLabel = if (subsystem.axis == SubsystemAxis.VERTICAL) "Tag DESCER"  else "Tag ESQUERDA"
                val phPositive = if (subsystem.axis == SubsystemAxis.VERTICAL) "M10.0" else "M12.0"
                val phNegative = if (subsystem.axis == SubsystemAxis.VERTICAL) "M10.1" else "M12.1"

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        subsystem.tagPositive, { onUpdate(subsystem.copy(tagPositive = it)) },
                        label = { Text(posLabel) }, placeholder = { Text(phPositive) },
                        modifier = Modifier.weight(1f), singleLine = true
                    )
                    OutlinedTextField(
                        subsystem.tagNegative, { onUpdate(subsystem.copy(tagNegative = it)) },
                        label = { Text(negLabel) }, placeholder = { Text(phNegative) },
                        modifier = Modifier.weight(1f), singleLine = true
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        subsystem.tagSpeedRead, { onUpdate(subsystem.copy(tagSpeedRead = it)) },
                        label = { Text("Tag Velocidade (leitura %)") }, placeholder = { Text("MW102") },
                        modifier = Modifier.weight(1f), singleLine = true
                    )
                    OutlinedTextField(
                        subsystem.tagSpeedWrite, { onUpdate(subsystem.copy(tagSpeedWrite = it)) },
                        label = { Text("Tag Setpoint (escrita %)") }, placeholder = { Text("MW200") },
                        modifier = Modifier.weight(1f), singleLine = true
                    )
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Vel. padrão: ${subsystem.defaultSpeedPct}%", style = MaterialTheme.typography.bodySmall, color = Secondary)
                    Spacer(Modifier.width(8.dp))
                    Slider(
                        value         = subsystem.defaultSpeedPct.toFloat(),
                        onValueChange = { onUpdate(subsystem.copy(defaultSpeedPct = it.toInt())) },
                        valueRange    = 5f..100f,
                        steps         = 18,
                        modifier      = Modifier.weight(1f),
                        colors        = SliderDefaults.colors(thumbColor = JoystickActive, activeTrackColor = JoystickActive)
                    )
                }
            }
        }
    }
}

// ─── FixedButtonEditor ────────────────────────────────────────────────────────

@Composable
private fun FixedButtonEditor(button: FixedButton, onUpdate: (FixedButton) -> Unit) {
    var behExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = SurfaceVariant),
        shape    = RoundedCornerShape(8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Slot número
            Text("${button.slot + 1}", style = MaterialTheme.typography.labelMedium, color = Secondary, modifier = Modifier.width(16.dp))

            // Habilitado
            Checkbox(
                checked         = button.isEnabled,
                onCheckedChange = { onUpdate(button.copy(isEnabled = it)) },
                modifier        = Modifier.size(20.dp)
            )

            // Label
            OutlinedTextField(
                button.label, { onUpdate(button.copy(label = it)) },
                label    = { Text("Label") },
                modifier = Modifier.weight(1.5f),
                singleLine = true
            )

            // Tag
            OutlinedTextField(
                button.tag, { onUpdate(button.copy(tag = it)) },
                label       = { Text("Tag") },
                placeholder = { Text("M0.0") },
                modifier    = Modifier.weight(2f),
                singleLine  = true
            )

            // Behavior
            ExposedDropdownMenuBox(behExpanded, { behExpanded = it }, modifier = Modifier.weight(1.5f)) {
                OutlinedTextField(
                    button.behavior.name, {},
                    readOnly     = true,
                    label        = { Text("Tipo") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(behExpanded) },
                    modifier     = Modifier.menuAnchor()
                )
                ExposedDropdownMenu(behExpanded, { behExpanded = false }) {
                    ButtonBehavior.entries.forEach { b ->
                        DropdownMenuItem({ Text(b.name) }, { onUpdate(button.copy(behavior = b)); behExpanded = false })
                    }
                }
            }
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Primary, fontWeight = FontWeight.Bold)
        Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(1.dp).background(SurfaceVariant))
    }
}