package com.craneremote.ui.screens.control

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.craneremote.data.remote.protocol.AllenBradleyProtocol
import com.craneremote.data.remote.protocol.SiemensS7Protocol
import com.craneremote.domain.model.*
import com.craneremote.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class ControlUiState(
    val device: Device? = null,
    val subsystems: List<Subsystem> = emptyList(),
    val buttons: List<FixedButton> = emptyList(),
    val status: Map<String, SubsystemStatus> = emptyMap(),   // subsystemId → status
    val buttonStates: Map<String, Boolean> = emptyMap(),      // buttonId → isActive (LATCH)
    val connectionStatus: DeviceStatus = DeviceStatus.DISCONNECTED,
    val isLoading: Boolean = true,
    val error: String? = null,
    // Joystick esquerdo — subsistemas VERTICAL (ganchos)
    val leftJoystickSubsystems: List<Subsystem> = emptyList(),
    val leftActiveIndex: Int = 0,
    // Joystick direito — subsistemas HORIZONTAL (bridge/trolley)
    val rightJoystickSubsystems: List<Subsystem> = emptyList(),
    val rightActiveIndex: Int = 0
)

@HiltViewModel
class ControlViewModel @Inject constructor(
    private val getDeviceById: GetDeviceByIdUseCase,
    private val getSubsystems: GetSubsystemsUseCase,
    private val getButtons: GetFixedButtonsUseCase,
    private val sendDirection: SendDirectionUseCase,
    private val setSpeed: SetSpeedUseCase,
    private val readSpeed: ReadSpeedUseCase,
    private val sendButton: SendButtonCommandUseCase,
    private val siemens: SiemensS7Protocol,
    private val ab: AllenBradleyProtocol
) : ViewModel() {

    private val _state = MutableStateFlow(ControlUiState())
    val state: StateFlow<ControlUiState> = _state.asStateFlow()

    private var pollingJob: Job? = null

    // ── Inicialização ──────────────────────────────────────────────────────────

    fun load(deviceId: String) {
        viewModelScope.launch {
            try {
                // Carrega device
                val device = getDeviceById(deviceId).filterNotNull().first()
                _state.update { it.copy(device = device) }

                // Carrega subsistemas e botões em paralelo
                launch {
                    getSubsystems(deviceId).collect { subs ->
                        val vertical   = subs.filter { it.axis == SubsystemAxis.VERTICAL }
                        val horizontal = subs.filter { it.axis == SubsystemAxis.HORIZONTAL }
                        val statusMap  = subs.associate { it.id to SubsystemStatus(it.id, setpointPct = it.defaultSpeedPct) }
                        _state.update { s ->
                            s.copy(
                                subsystems               = subs,
                                leftJoystickSubsystems   = vertical,
                                rightJoystickSubsystems  = horizontal,
                                status                   = s.status + statusMap.filter { (k, _) -> k !in s.status },
                                isLoading                = false
                            )
                        }
                    }
                }

                launch {
                    getButtons(deviceId).collect { btns ->
                        _state.update { it.copy(buttons = btns.sortedBy { b -> b.slot }) }
                    }
                }

                // Conecta ao PLC
                connect(device)

            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    // ── Conexão ───────────────────────────────────────────────────────────────

    private suspend fun connect(device: Device) {
        _state.update { it.copy(connectionStatus = DeviceStatus.CONNECTING) }
        val result = when (device.plcType) {
            PlcType.SIEMENS_S7_1200,
            PlcType.SIEMENS_S7_1500,
            PlcType.SIEMENS_S7_300 -> siemens.connect(device.ipAddress, device.port, device.rack, device.slot)
            else -> ab.connect(device.ipAddress, device.port)
        }
        if (result.isSuccess) {
            _state.update { it.copy(connectionStatus = DeviceStatus.CONNECTED, error = null) }
            startPolling()
        } else {
            _state.update { it.copy(connectionStatus = DeviceStatus.ERROR, error = result.exceptionOrNull()?.message) }
        }
    }

    fun reconnect() {
        val device = _state.value.device ?: return
        viewModelScope.launch { connect(device) }
    }

    // ── Polling de velocidade ─────────────────────────────────────────────────

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(500)
                val device = _state.value.device ?: continue
                val subs   = _state.value.subsystems
                subs.forEach { sub ->
                    if (sub.tagSpeedRead.isBlank()) return@forEach
                    val speedResult = readSpeed(device, sub)
                    val speedPct = speedResult.getOrNull()?.toFloat() ?: return@forEach
                    val isMoving = speedPct > 1f
                    _state.update { s ->
                        val updated = (s.status[sub.id] ?: SubsystemStatus(sub.id))
                            .copy(speedPct = speedPct, isMoving = isMoving)
                        s.copy(status = s.status + (sub.id to updated))
                    }
                }
            }
        }
    }

    // ── Joystick — seleção de subsistema ─────────────────────────────────────

    fun selectLeftSubsystem(index: Int) {
        _state.update { it.copy(leftActiveIndex = index.coerceIn(0, it.leftJoystickSubsystems.lastIndex.coerceAtLeast(0))) }
    }

    fun selectRightSubsystem(index: Int) {
        _state.update { it.copy(rightActiveIndex = index.coerceIn(0, it.rightJoystickSubsystems.lastIndex.coerceAtLeast(0))) }
    }

    // ── Joystick — setpoint de velocidade ────────────────────────────────────

    fun setLeftSpeed(pct: Int) {
        val sub = activeLeftSub() ?: return
        updateSpeed(sub.id, pct)
        viewModelScope.launch {
            val device = _state.value.device ?: return@launch
            setSpeed(device, sub, pct)
        }
    }

    fun setRightSpeed(pct: Int) {
        val sub = activeRightSub() ?: return
        updateSpeed(sub.id, pct)
        viewModelScope.launch {
            val device = _state.value.device ?: return@launch
            setSpeed(device, sub, pct)
        }
    }

    private fun updateSpeed(subsystemId: String, pct: Int) {
        _state.update { s ->
            val updated = (s.status[subsystemId] ?: SubsystemStatus(subsystemId)).copy(setpointPct = pct)
            s.copy(status = s.status + (subsystemId to updated))
        }
    }

    // ── Joystick — comandos de direção ────────────────────────────────────────

    fun leftPress(direction: String) {
        val sub = activeLeftSub() ?: return
        val device = _state.value.device ?: return
        viewModelScope.launch { sendDirection(device, sub, direction, true) }
    }

    fun leftRelease(direction: String) {
        val sub = activeLeftSub() ?: return
        val device = _state.value.device ?: return
        viewModelScope.launch { sendDirection(device, sub, direction, false) }
    }

    fun rightPress(direction: String) {
        val sub = activeRightSub() ?: return
        val device = _state.value.device ?: return
        viewModelScope.launch { sendDirection(device, sub, direction, true) }
    }

    fun rightRelease(direction: String) {
        val sub = activeRightSub() ?: return
        val device = _state.value.device ?: return
        viewModelScope.launch { sendDirection(device, sub, direction, false) }
    }

    // ── Botões fixos ──────────────────────────────────────────────────────────

    fun onButtonPress(button: FixedButton) {
        val device = _state.value.device ?: return
        viewModelScope.launch {
            when (button.behavior) {
                ButtonBehavior.PULSE, ButtonBehavior.MOMENTARY -> {
                    sendButton(device, button, true)
                }
                ButtonBehavior.LATCH -> {
                    val current = _state.value.buttonStates[button.id] ?: false
                    val newVal = !current
                    _state.update { it.copy(buttonStates = it.buttonStates + (button.id to newVal)) }
                    sendButton(device, button, newVal)
                }
            }
        }
    }

    fun onButtonRelease(button: FixedButton) {
        val device = _state.value.device ?: return
        if (button.behavior == ButtonBehavior.PULSE || button.behavior == ButtonBehavior.MOMENTARY) {
            viewModelScope.launch { sendButton(device, button, false) }
        }
    }

    // ── Emergency Stop ────────────────────────────────────────────────────────

    fun emergencyStop() {
        val device = _state.value.device ?: return
        viewModelScope.launch {
            _state.value.subsystems.forEach { sub ->
                sendDirection(device, sub, "UP", false)
                sendDirection(device, sub, "DOWN", false)
                sendDirection(device, sub, "LEFT", false)
                sendDirection(device, sub, "RIGHT", false)
            }
        }
    }

    // ── Disconnect ────────────────────────────────────────────────────────────

    fun disconnect() {
        pollingJob?.cancel()
        val device = _state.value.device ?: return
        when (device.plcType) {
            PlcType.SIEMENS_S7_1200,
            PlcType.SIEMENS_S7_1500,
            PlcType.SIEMENS_S7_300 -> siemens.disconnect()
            else -> ab.disconnect()
        }
        _state.update { it.copy(connectionStatus = DeviceStatus.DISCONNECTED) }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    override fun onCleared() { super.onCleared(); disconnect() }

    // ── Helpers privados ──────────────────────────────────────────────────────

    private fun activeLeftSub(): Subsystem? {
        val s = _state.value
        return s.leftJoystickSubsystems.getOrNull(s.leftActiveIndex)
    }

    private fun activeRightSub(): Subsystem? {
        val s = _state.value
        return s.rightJoystickSubsystems.getOrNull(s.rightActiveIndex)
    }
}
