package com.craneremote.data.repository

import com.craneremote.data.local.*
import com.craneremote.domain.model.*
import com.craneremote.domain.repository.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// ─── Device ───────────────────────────────────────────────────────────────────

@Singleton
class DeviceRepositoryImpl @Inject constructor(private val dao: DeviceDao) : DeviceRepository {
    override fun getAllDevices(): Flow<List<Device>> = dao.getAll().map { it.map { e -> e.toDomain() } }
    override fun getDeviceById(id: String): Flow<Device?> = dao.getById(id).map { it?.toDomain() }
    override suspend fun insertDevice(device: Device) = dao.insert(device.toEntity())
    override suspend fun updateDevice(device: Device) = dao.update(device.toEntity())
    override suspend fun deleteDevice(id: String) = dao.delete(id)
}

// ─── Subsystem ────────────────────────────────────────────────────────────────

@Singleton
class SubsystemRepositoryImpl @Inject constructor(private val dao: SubsystemDao) : SubsystemRepository {
    override fun getSubsystemsByDevice(deviceId: String): Flow<List<Subsystem>> =
        dao.getByDevice(deviceId).map { it.map { e -> e.toDomain() } }
    override fun getSubsystemById(id: String): Flow<Subsystem?> = dao.getById(id).map { it?.toDomain() }
    override suspend fun insertSubsystem(subsystem: Subsystem) = dao.insert(subsystem.toEntity())
    override suspend fun updateSubsystem(subsystem: Subsystem) = dao.update(subsystem.toEntity())
    override suspend fun deleteSubsystem(id: String) = dao.delete(id)
    override suspend fun deleteSubsystemsByDevice(deviceId: String) = dao.deleteByDevice(deviceId)
    override suspend fun replaceSubsystemsForDevice(deviceId: String, subsystems: List<Subsystem>) {
        dao.deleteByDevice(deviceId)
        dao.insertAll(subsystems.map { it.copy(deviceId = deviceId).toEntity() })
    }
}

// ─── FixedButton ──────────────────────────────────────────────────────────────

@Singleton
class FixedButtonRepositoryImpl @Inject constructor(private val dao: FixedButtonDao) : FixedButtonRepository {
    override fun getButtonsByDevice(deviceId: String): Flow<List<FixedButton>> =
        dao.getByDevice(deviceId).map { it.map { e -> e.toDomain() } }
    override suspend fun insertButton(button: FixedButton) = dao.insert(button.toEntity())
    override suspend fun updateButton(button: FixedButton) = dao.update(button.toEntity())
    override suspend fun deleteButton(id: String) = dao.delete(id)
    override suspend fun replaceButtonsForDevice(deviceId: String, buttons: List<FixedButton>) {
        dao.deleteByDevice(deviceId)
        dao.insertAll(buttons.map { it.copy(deviceId = deviceId).toEntity() })
    }
}

// ─── Log ──────────────────────────────────────────────────────────────────────

@Singleton
class LogRepositoryImpl @Inject constructor(private val dao: CommandLogDao) : LogRepository {
    override fun getLogsByDevice(deviceId: String): Flow<List<CommandLog>> =
        dao.getByDevice(deviceId).map { it.map { e -> e.toDomain() } }
    override suspend fun insertLog(log: CommandLog) = dao.insert(log.toEntity())
    override suspend fun clearLogsByDevice(deviceId: String) = dao.clearByDevice(deviceId)
    override suspend fun clearAllLogs() = dao.clearAll()
}
