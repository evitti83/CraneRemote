package com.craneremote.ui.screens.device

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.craneremote.domain.model.Device
import com.craneremote.domain.usecase.DeleteDeviceUseCase
import com.craneremote.domain.usecase.GetAllDevicesUseCase
import com.craneremote.ui.navigation.Screen
import com.craneremote.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

data class DeviceListState(
    val devices: List<Device> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class DeviceListViewModel @Inject constructor(
    private val getAll: GetAllDevicesUseCase,
    private val delete: DeleteDeviceUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(DeviceListState())
    val state: StateFlow<DeviceListState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            getAll().collect { list ->
                _state.update { it.copy(devices = list, isLoading = false) }
            }
        }
    }

    fun delete(id: String) = viewModelScope.launch {
        runCatching { delete.invoke(id) }
            .onFailure { _state.update { s -> s.copy(error = it.message) } }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreen(
    navController: NavController,
    viewModel: DeviceListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); viewModel.clearError() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title  = { Text("Crane Remote", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Primary, titleContentColor = Color.White)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick        = { navController.navigate(Screen.DeviceConfig.createRoute(Screen.DeviceConfig.NEW)) },
                containerColor = Primary
            ) { Icon(Icons.Default.Add, null, tint = Color.White) }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Primary)
                state.devices.isEmpty() -> Column(
                    Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Devices, null, Modifier.size(64.dp), tint = Secondary)
                    Spacer(Modifier.height(16.dp))
                    Text("Nenhum dispositivo", color = Secondary)
                    Text("Toque em + para adicionar", style = MaterialTheme.typography.bodySmall, color = Secondary)
                }
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.devices, key = { it.id }) { device ->
                        DeviceCard(
                            device   = device,
                            onClick  = { navController.navigate(Screen.Control.createRoute(device.id)) },
                            onEdit   = { navController.navigate(Screen.DeviceConfig.createRoute(device.id)) },
                            onDelete = { viewModel.delete(device.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(device: Device, onClick: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    var showDel  by remember { mutableStateOf(false) }

    if (showDel) AlertDialog(
        onDismissRequest = { showDel = false },
        title   = { Text("Excluir?") },
        text    = { Text("Excluir \"${device.name}\"? Todos os subsistemas e logs serão apagados.") },
        confirmButton = { TextButton(onClick = { showDel = false; onDelete() }) { Text("Excluir", color = Disconnected) } },
        dismissButton = { TextButton(onClick = { showDel = false }) { Text("Cancelar") } }
    )

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors   = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PrecisionManufacturing, null, Modifier.size(40.dp), tint = Primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(device.name, style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text("${device.ipAddress}:${device.port}", style = MaterialTheme.typography.bodyMedium, color = Secondary)
                Text(device.plcType.name.replace("_", " "), style = MaterialTheme.typography.bodySmall, color = Secondary)
            }
            Box {
                IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, null, tint = Color.White) }
                DropdownMenu(showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem({ Text("Controle") }, { showMenu = false; onClick() }, leadingIcon = { Icon(Icons.Default.Gamepad, null) })
                    DropdownMenuItem({ Text("Configurar") }, { showMenu = false; onEdit() }, leadingIcon = { Icon(Icons.Default.Settings, null) })
                    DropdownMenuItem({ Text("Excluir", color = Disconnected) }, { showMenu = false; showDel = true }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = Disconnected) })
                }
            }
        }
    }
}
