package com.craneremote.domain.repository

import com.craneremote.domain.model.*
import kotlinx.coroutines.flow.Flow

interface DeviceRepository {
    fun getAllDevices(): Flow<List<Device>>
    fun getDeviceById(id: String): Flow<Device?>
    suspend fun insertDevice(device: Device)
    suspend fun updateDevice(device: Device)
    suspend fun deleteDevice(id: String)
}

interface SubsystemRepository {
    fun getSubsystemsByDevice(deviceId: String): Flow<List<Subsystem>>
    fun getSubsystemById(id: String): Flow<Subsystem?>
    suspend fun insertSubsystem(subsystem: Subsystem)
    suspend fun updateSubsystem(subsystem: Subsystem)
    suspend fun deleteSubsystem(id: String)
    suspend fun deleteSubsystemsByDevice(deviceId: String)
    suspend fun replaceSubsystemsForDevice(deviceId: String, subsystems: List<Subsystem>)
}

interface FixedButtonRepository {
    fun getButtonsByDevice(deviceId: String): Flow<List<FixedButton>>
    suspend fun insertButton(button: FixedButton)
    suspend fun updateButton(button: FixedButton)
    suspend fun deleteButton(id: String)
    suspend fun replaceButtonsForDevice(deviceId: String, buttons: List<FixedButton>)
}

interface LogRepository {
    fun getLogsByDevice(deviceId: String): Flow<List<CommandLog>>
    suspend fun insertLog(log: CommandLog)
    suspend fun clearLogsByDevice(deviceId: String)
    suspend fun clearAllLogs()
}
