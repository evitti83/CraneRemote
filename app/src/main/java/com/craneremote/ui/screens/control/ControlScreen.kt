package com.craneremote.ui.screens.control

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.craneremote.domain.model.*
import com.craneremote.ui.navigation.Screen
import com.craneremote.ui.theme.*

@Composable
fun ControlScreen(
    navController: NavController,
    deviceId: String,
    viewModel: ControlViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(deviceId) { viewModel.load(deviceId) }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); viewModel.clearError() }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { pad ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .background(Background)
        ) {
            // ── Top Bar ───────────────────────────────────────────────────────
            TopBar(
                state        = state,
                onBack       = { viewModel.disconnect(); navController.popBackStack() },
                onLogs       = { navController.navigate(Screen.Logs.createRoute(deviceId)) },
                onConfig     = { navController.navigate(Screen.DeviceConfig.createRoute(deviceId)) },
                onReconnect  = { viewModel.reconnect() }
            )

            // ── Área principal — joysticks ─────────────────────────────────
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ── Joystick Esquerdo (Ganchos — VERTICAL) ────────────────
                JoystickPanel(
                    modifier         = Modifier.weight(1f).fillMaxHeight(),
                    title            = "GANCHOS",
                    subsystems       = state.leftJoystickSubsystems,
                    activeIndex      = state.leftActiveIndex,
                    statusMap        = state.status,
                    positiveLabel    = "▲ SUBIR",
                    negativeLabel    = "▼ DESCER",
                    positiveDir      = "UP",
                    negativeDir      = "DOWN",
                    isConnected      = state.connectionStatus == DeviceStatus.CONNECTED,
                    onSelectSub      = { viewModel.selectLeftSubsystem(it) },
                    onSpeedChange    = { viewModel.setLeftSpeed(it) },
                    onPress          = { viewModel.leftPress(it) },
                    onRelease        = { viewModel.leftRelease(it) }
                )

                // ── Centro — E-Stop e status ──────────────────────────────
                CenterPanel(
                    state   = state,
                    onEStop = { viewModel.emergencyStop() }
                )

                // ── Joystick Direito (Bridge/Trolley — HORIZONTAL) ────────
                JoystickPanel(
                    modifier         = Modifier.weight(1f).fillMaxHeight(),
                    title            = "TRANSLAÇÃO",
                    subsystems       = state.rightJoystickSubsystems,
                    activeIndex      = state.rightActiveIndex,
                    statusMap        = state.status,
                    positiveLabel    = "▶ DIREITA",
                    negativeLabel    = "◀ ESQUERDA",
                    positiveDir      = "RIGHT",
                    negativeDir      = "LEFT",
                    isConnected      = state.connectionStatus == DeviceStatus.CONNECTED,
                    onSelectSub      = { viewModel.selectRightSubsystem(it) },
                    onSpeedChange    = { viewModel.setRightSpeed(it) },
                    onPress          = { viewModel.rightPress(it) },
                    onRelease        = { viewModel.rightRelease(it) }
                )
            }

            // ── Barra de botões fixos ──────────────────────────────────────
            FixedButtonBar(
                buttons      = state.buttons,
                buttonStates = state.buttonStates,
                isConnected  = state.connectionStatus == DeviceStatus.CONNECTED,
                onPress      = { viewModel.onButtonPress(it) },
                onRelease    = { viewModel.onButtonRelease(it) }
            )
        }
    }
}

// ─── Top Bar ──────────────────────────────────────────────────────────────────

@Composable
private fun TopBar(
    state: ControlUiState,
    onBack: () -> Unit,
    onLogs: () -> Unit,
    onConfig: () -> Unit,
    onReconnect: () -> Unit
) {
    val barColor by animateColorAsState(
        targetValue = when (state.connectionStatus) {
            DeviceStatus.CONNECTED    -> Color(0xFF1B2A1B)
            DeviceStatus.CONNECTING   -> Color(0xFF2A2A1B)
            DeviceStatus.ERROR        -> Color(0xFF2A1B1B)
            DeviceStatus.DISCONNECTED -> Color(0xFF1A1A1A)
        }, label = "barColor"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(barColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Esquerda
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.ArrowBack, null, tint = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    state.device?.name ?: "Crane Remote",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    state.device?.ipAddress ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = Secondary
                )
            }
        }

        // Status central
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val (statusText, statusColor) = when (state.connectionStatus) {
                DeviceStatus.CONNECTED    -> "CONECTADO" to Connected
                DeviceStatus.CONNECTING   -> "CONECTANDO..." to Warning
                DeviceStatus.ERROR        -> "ERRO" to Disconnected
                DeviceStatus.DISCONNECTED -> "DESCONECTADO" to Secondary
            }
            if (state.connectionStatus == DeviceStatus.CONNECTING) {
                CircularProgressIndicator(Modifier.size(14.dp), color = Warning, strokeWidth = 2.dp)
            } else {
                Box(Modifier.size(10.dp).clip(CircleShape).background(statusColor))
            }
            Text(statusText, style = MaterialTheme.typography.labelMedium, color = statusColor)
            if (state.connectionStatus == DeviceStatus.ERROR || state.connectionStatus == DeviceStatus.DISCONNECTED) {
                TextButton(onClick = onReconnect, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                    Text("Reconectar", style = MaterialTheme.typography.labelSmall, color = Warning)
                }
            }
        }

        // Direita
        Row {
            IconButton(onClick = onLogs, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.List, null, tint = Color.White)
            }
            IconButton(onClick = onConfig, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Settings, null, tint = Color.White)
            }
        }
    }
}

// ─── Joystick Panel ───────────────────────────────────────────────────────────

@Composable
private fun JoystickPanel(
    modifier: Modifier,
    title: String,
    subsystems: List<Subsystem>,
    activeIndex: Int,
    statusMap: Map<String, SubsystemStatus>,
    positiveLabel: String,
    negativeLabel: String,
    positiveDir: String,
    negativeDir: String,
    isConnected: Boolean,
    onSelectSub: (Int) -> Unit,
    onSpeedChange: (Int) -> Unit,
    onPress: (String) -> Unit,
    onRelease: (String) -> Unit
) {
    val activeSub = subsystems.getOrNull(activeIndex)
    val activeStatus = activeSub?.let { statusMap[it.id] }
    val speedPct = activeStatus?.speedPct ?: 0f
    val setpoint = activeStatus?.setpointPct ?: 50
    val isMoving = activeStatus?.isMoving ?: false

    Card(
        modifier = modifier,
        colors   = CardDefaults.cardColors(containerColor = JoystickBg),
        shape    = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // ── Título ────────────────────────────────────────────────────
            Text(title, style = MaterialTheme.typography.labelLarge, color = Secondary)

            // ── Seletor de subsistema ─────────────────────────────────────
            if (subsystems.isEmpty()) {
                Text("Nenhum subsistema configurado",
                    style = MaterialTheme.typography.bodySmall, color = Secondary,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                return@Column
            }

            SubsystemSelector(
                subsystems  = subsystems,
                activeIndex = activeIndex,
                statusMap   = statusMap,
                onSelect    = onSelectSub
            )

            // ── Velocidade lida ───────────────────────────────────────────
            SpeedIndicator(speedPct = speedPct, isMoving = isMoving)

            // ── Setpoint de velocidade ────────────────────────────────────
            SpeedSetpoint(
                value     = setpoint,
                onChange  = onSpeedChange,
                enabled   = isConnected
            )

            Spacer(Modifier.weight(1f))

            // ── Botão positivo (Subir / Direita) ──────────────────────────
            JoystickButton(
                label     = positiveLabel,
                color     = if (positiveDir == "UP") HoistGreen else MoveBlue,
                enabled   = isConnected && activeSub != null,
                modifier  = Modifier.fillMaxWidth().height(64.dp),
                onPress   = { onPress(positiveDir) },
                onRelease = { onRelease(positiveDir) }
            )

            Spacer(Modifier.height(4.dp))

            // ── Botão negativo (Descer / Esquerda) ────────────────────────
            JoystickButton(
                label     = negativeLabel,
                color     = if (negativeDir == "DOWN") HoistGreen else MoveBlue,
                enabled   = isConnected && activeSub != null,
                modifier  = Modifier.fillMaxWidth().height(64.dp),
                onPress   = { onPress(negativeDir) },
                onRelease = { onRelease(negativeDir) }
            )
        }
    }
}

// ─── Seletor de Subsistema ────────────────────────────────────────────────────

@Composable
private fun SubsystemSelector(
    subsystems: List<Subsystem>,
    activeIndex: Int,
    statusMap: Map<String, SubsystemStatus>,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        subsystems.forEachIndexed { idx, sub ->
            val isActive = idx == activeIndex
            val status   = statusMap[sub.id]
            val isMoving = status?.isMoving ?: false

            val bgColor by animateColorAsState(
                if (isActive) JoystickActive else JoystickTrack, label = "subBg"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(bgColor)
                    .border(
                        width = if (isMoving) 2.dp else 0.dp,
                        color = if (isMoving) Connected else Color.Transparent,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .pointerInput(Unit) { detectTapGestures { onSelect(idx) } }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        sub.name,
                        style      = MaterialTheme.typography.labelMedium,
                        color      = if (isActive) Color.Black else Color.White,
                        textAlign  = TextAlign.Center,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                    )
                    if (isMoving) {
                        Text("●", color = Connected, fontSize = 8.sp)
                    }
                }
            }
        }
    }
}

// ─── Indicador de Velocidade ──────────────────────────────────────────────────

@Composable
private fun SpeedIndicator(speedPct: Float, isMoving: Boolean) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
        label = "alpha"
    )
    val alpha = if (isMoving) pulse else 1f

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("VEL:", style = MaterialTheme.typography.labelSmall, color = Secondary)
            Text(
                "${speedPct.toInt()}%",
                style = MaterialTheme.typography.headlineSmall.copy(
                    color = if (isMoving) Connected.copy(alpha = alpha) else Color.White,
                    fontWeight = FontWeight.Bold
                )
            )
            if (isMoving) {
                Text("▶", color = Connected.copy(alpha = alpha), fontSize = 12.sp)
            }
        }
        // Barra de velocidade
        LinearProgressIndicator(
            progress   = (speedPct / 100f).coerceIn(0f, 1f),
            modifier   = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color      = if (isMoving) Connected else Secondary,
            trackColor = JoystickTrack
        )
    }
}

// ─── Setpoint de Velocidade ───────────────────────────────────────────────────

@Composable
private fun SpeedSetpoint(value: Int, onChange: (Int) -> Unit, enabled: Boolean) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("SET:", style = MaterialTheme.typography.labelSmall, color = Secondary)
            Text("$value%", style = MaterialTheme.typography.labelMedium, color = JoystickActive)
            Row {
                SmallAdjBtn("-", enabled && value > 5)  { onChange((value - 5).coerceAtLeast(5)) }
                Spacer(Modifier.width(4.dp))
                SmallAdjBtn("+", enabled && value < 100) { onChange((value + 5).coerceAtMost(100)) }
            }
        }
        Slider(
            value         = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange    = 5f..100f,
            steps         = 18,
            enabled       = enabled,
            colors        = SliderDefaults.colors(
                thumbColor       = JoystickActive,
                activeTrackColor = JoystickActive,
                inactiveTrackColor = JoystickTrack
            ),
            modifier = Modifier.fillMaxWidth().height(28.dp)
        )
    }
}

@Composable
private fun SmallAdjBtn(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (enabled) JoystickTrack else Color(0xFF222222))
            .pointerInput(enabled) { if (enabled) detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (enabled) Color.White else Secondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

// ─── Botão de Joystick (Hold) ─────────────────────────────────────────────────

@Composable
private fun JoystickButton(
    label: String,
    color: Color,
    enabled: Boolean,
    modifier: Modifier,
    onPress: () -> Unit,
    onRelease: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val bgColor by animateColorAsState(
        if (pressed && enabled) color else color.copy(alpha = 0.4f), label = "btnColor"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .pointerInput(enabled) {
                detectTapGestures(
                    onPress = {
                        if (enabled) {
                            pressed = true
                            onPress()
                            tryAwaitRelease()
                            pressed = false
                            onRelease()
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color      = if (enabled) Color.White else Color.White.copy(alpha = 0.4f),
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign  = TextAlign.Center
        )
    }
}

// ─── Centro — E-Stop e info ───────────────────────────────────────────────────

@Composable
private fun CenterPanel(state: ControlUiState, onEStop: () -> Unit) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .fillMaxHeight()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // PLC info
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Factory, null, tint = Secondary, modifier = Modifier.size(24.dp))
            Text(
                state.device?.plcType?.name?.replace("_", "\n") ?: "",
                style     = MaterialTheme.typography.labelSmall,
                color     = Secondary,
                textAlign = TextAlign.Center
            )
        }

        // ESTOP
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(EmergencyRed)
                .pointerInput(Unit) { detectTapGestures { onEStop() } },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Stop, null, tint = Color.White, modifier = Modifier.size(32.dp))
                Text("E-STOP", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            }
        }

        // Subsistemas ativos
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val leftSub  = state.leftJoystickSubsystems.getOrNull(state.leftActiveIndex)
            val rightSub = state.rightJoystickSubsystems.getOrNull(state.rightActiveIndex)
            Text("Ativo:", style = MaterialTheme.typography.labelSmall, color = Secondary)
            Text(leftSub?.name  ?: "-", style = MaterialTheme.typography.labelMedium, color = HoistGreen.copy(0.9f))
            Text(rightSub?.name ?: "-", style = MaterialTheme.typography.labelMedium, color = MoveBlue.copy(0.9f))
        }
    }
}

// ─── Barra de Botões Fixos ────────────────────────────────────────────────────

@Composable
private fun FixedButtonBar(
    buttons: List<FixedButton>,
    buttonStates: Map<String, Boolean>,
    isConnected: Boolean,
    onPress: (FixedButton) -> Unit,
    onRelease: (FixedButton) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceVariant)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        buttons.filter { it.isEnabled }.forEach { btn ->
            val isActive = buttonStates[btn.id] ?: false
            val btnColor = runCatching {
                Color(android.graphics.Color.parseColor(btn.activeColor))
            }.getOrDefault(Primary)

            val bgColor by animateColorAsState(
                if (isActive) btnColor else btnColor.copy(alpha = 0.3f), label = "btnBg"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(bgColor)
                    .border(
                        1.dp,
                        if (isActive) btnColor else btnColor.copy(0.5f),
                        RoundedCornerShape(8.dp)
                    )
                    .pointerInput(isConnected, btn.id) {
                        detectTapGestures(
                            onPress = {
                                if (isConnected) {
                                    onPress(btn)
                                    tryAwaitRelease()
                                    onRelease(btn)
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        btn.label,
                        style     = MaterialTheme.typography.labelSmall,
                        color     = if (isConnected) Color.White else Color.White.copy(0.4f),
                        textAlign = TextAlign.Center,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                    )
                    if (btn.behavior == ButtonBehavior.LATCH && isActive) {
                        Text("●", color = Color.White, fontSize = 6.sp)
                    }
                }
            }
        }
    }
}
