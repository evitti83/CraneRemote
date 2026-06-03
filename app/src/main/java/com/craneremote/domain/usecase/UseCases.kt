package com.craneremote.domain.usecase

import com.craneremote.data.remote.protocol.AllenBradleyProtocol
import com.craneremote.data.remote.protocol.SiemensS7Protocol
import com.craneremote.domain.model.*
import com.craneremote.domain.repository.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

// ── Device ────────────────────────────────────────────────────────────────────

class GetAllDevicesUseCase @Inject constructor(private val repo: DeviceRepository) {
    operator fun invoke(): Flow<List<Device>> = repo.getAllDevices()
}

class GetDeviceByIdUseCase @Inject constructor(private val repo: DeviceRepository) {
    operator fun invoke(id: String): Flow<Device?> = repo.getDeviceById(id)
}

class SaveDeviceUseCase @Inject constructor(
    private val deviceRepo: DeviceRepository,
    private val subsystemRepo: SubsystemRepository,
    private val buttonRepo: FixedButtonRepository
) {
    suspend operator fun invoke(
        device: Device,
        subsystems: List<Subsystem> = emptyList(),
        buttons: List<FixedButton> = emptyList()
    ) {
        deviceRepo.insertDevice(device)
        if (subsystems.isNotEmpty()) subsystemRepo.replaceSubsystemsForDevice(device.id, subsystems)
        if (buttons.isNotEmpty()) buttonRepo.replaceButtonsForDevice(device.id, buttons)
        else buttonRepo.replaceButtonsForDevice(device.id, DefaultButtons.defaults(device.id))
    }
}

class UpdateDeviceUseCase @Inject constructor(private val repo: DeviceRepository) {
    suspend operator fun invoke(device: Device) = repo.updateDevice(device)
}

class DeleteDeviceUseCase @Inject constructor(
    private val deviceRepo: DeviceRepository,
    private val subsystemRepo: SubsystemRepository,
    private val logRepo: LogRepository
) {
    suspend operator fun invoke(deviceId: String) {
        logRepo.clearLogsByDevice(deviceId)
        subsystemRepo.deleteSubsystemsByDevice(deviceId)
        deviceRepo.deleteDevice(deviceId)
    }
}

// ── Subsystem ─────────────────────────────────────────────────────────────────

class GetSubsystemsUseCase @Inject constructor(private val repo: SubsystemRepository) {
    operator fun invoke(deviceId: String): Flow<List<Subsystem>> = repo.getSubsystemsByDevice(deviceId)
}

class SaveSubsystemUseCase @Inject constructor(private val repo: SubsystemRepository) {
    suspend operator fun invoke(subsystem: Subsystem) = repo.insertSubsystem(subsystem)
}

class UpdateSubsystemUseCase @Inject constructor(private val repo: SubsystemRepository) {
    suspend operator fun invoke(subsystem: Subsystem) = repo.updateSubsystem(subsystem)
}

class DeleteSubsystemUseCase @Inject constructor(private val repo: SubsystemRepository) {
    suspend operator fun invoke(id: String) = repo.deleteSubsystem(id)
}

class ReplaceSubsystemsUseCase @Inject constructor(private val repo: SubsystemRepository) {
    suspend operator fun invoke(deviceId: String, subsystems: List<Subsystem>) =
        repo.replaceSubsystemsForDevice(deviceId, subsystems)
}

// ── FixedButton ───────────────────────────────────────────────────────────────

class GetFixedButtonsUseCase @Inject constructor(private val repo: FixedButtonRepository) {
    operator fun invoke(deviceId: String): Flow<List<FixedButton>> = repo.getButtonsByDevice(deviceId)
}

class UpdateFixedButtonUseCase @Inject constructor(private val repo: FixedButtonRepository) {
    suspend operator fun invoke(button: FixedButton) = repo.updateButton(button)
}

class ReplaceButtonsUseCase @Inject constructor(private val repo: FixedButtonRepository) {
    suspend operator fun invoke(deviceId: String, buttons: List<FixedButton>) =
        repo.replaceButtonsForDevice(deviceId, buttons)
}

// ── PLC Commands ──────────────────────────────────────────────────────────────

class SendDirectionUseCase @Inject constructor(
    private val logRepo: LogRepository,
    private val siemens: SiemensS7Protocol,
    private val ab: AllenBradleyProtocol
) {
    /**
     * Escreve BOOL numa tag de direção.
     * direction: "UP" | "DOWN" | "LEFT" | "RIGHT"
     */
    suspend operator fun invoke(
        device: Device,
        subsystem: Subsystem,
        direction: String,
        value: Boolean
    ): Result<Unit> {
        val tag = when (direction) {
            "UP", "RIGHT"   -> subsystem.tagPositive
            "DOWN", "LEFT"  -> subsystem.tagNegative
            else -> return Result.failure(Exception("Direção inválida"))
        }
        if (tag.isBlank()) return Result.failure(Exception("Tag não configurada para $direction"))

        val result = runCatching {
            when (device.plcType) {
                PlcType.SIEMENS_S7_1200,
                PlcType.SIEMENS_S7_1500,
                PlcType.SIEMENS_S7_300 -> siemens.writeBool(tag, value).getOrThrow()
                else -> ab.writeBool(tag, value).getOrThrow()
            }
        }

        logRepo.insertLog(CommandLog(
            deviceId      = device.id,
            deviceName    = device.name,
            subsystemName = subsystem.name,
            direction     = direction,
            tagAddress    = tag,
            value         = if (value) "1" else "0",
            success       = result.isSuccess,
            response      = result.exceptionOrNull()?.message ?: "OK"
        ))
        return result
    }
}

class SetSpeedUseCase @Inject constructor(
    private val siemens: SiemensS7Protocol,
    private val ab: AllenBradleyProtocol
) {
    /** Escreve setpoint de velocidade (0-100%) na tag configurada. */
    suspend operator fun invoke(device: Device, subsystem: Subsystem, speedPct: Int): Result<Unit> {
        val tag = subsystem.tagSpeedWrite
        if (tag.isBlank()) return Result.success(Unit) // sem tag configurada, ignora
        return runCatching {
            when (device.plcType) {
                PlcType.SIEMENS_S7_1200,
                PlcType.SIEMENS_S7_1500,
                PlcType.SIEMENS_S7_300 -> siemens.writeInt(tag, speedPct).getOrThrow()
                else -> ab.writeInt(tag, speedPct).getOrThrow()
            }
        }
    }
}

class ReadSpeedUseCase @Inject constructor(
    private val siemens: SiemensS7Protocol,
    private val ab: AllenBradleyProtocol
) {
    /** Lê velocidade atual em % do subsistema. */
    suspend operator fun invoke(device: Device, subsystem: Subsystem): Result<Int> {
        val tag = subsystem.tagSpeedRead
        if (tag.isBlank()) return Result.success(0)
        return runCatching {
            when (device.plcType) {
                PlcType.SIEMENS_S7_1200,
                PlcType.SIEMENS_S7_1500,
                PlcType.SIEMENS_S7_300 -> siemens.readInt(tag).getOrThrow()
                else -> ab.readInt(tag).getOrThrow()
            }
        }
    }
}

class SendButtonCommandUseCase @Inject constructor(
    private val logRepo: LogRepository,
    private val siemens: SiemensS7Protocol,
    private val ab: AllenBradleyProtocol
) {
    suspend operator fun invoke(device: Device, button: FixedButton, value: Boolean): Result<Unit> {
        if (button.tag.isBlank()) return Result.failure(Exception("Tag não configurada para ${button.label}"))
        val result = runCatching {
            when (device.plcType) {
                PlcType.SIEMENS_S7_1200,
                PlcType.SIEMENS_S7_1500,
                PlcType.SIEMENS_S7_300 -> siemens.writeBool(button.tag, value).getOrThrow()
                else -> ab.writeBool(button.tag, value).getOrThrow()
            }
        }
        logRepo.insertLog(CommandLog(
            deviceId      = device.id,
            deviceName    = device.name,
            subsystemName = "BUTTON",
            direction     = "BUTTON:${button.label}",
            tagAddress    = button.tag,
            value         = if (value) "1" else "0",
            success       = result.isSuccess,
            response      = result.exceptionOrNull()?.message ?: "OK"
        ))
        return result
    }
}

// ── Logs ──────────────────────────────────────────────────────────────────────

class GetLogsUseCase @Inject constructor(private val repo: LogRepository) {
    operator fun invoke(deviceId: String): Flow<List<CommandLog>> = repo.getLogsByDevice(deviceId)
}

class ClearLogsUseCase @Inject constructor(private val repo: LogRepository) {
    suspend operator fun invoke(deviceId: String) = repo.clearLogsByDevice(deviceId)
}
