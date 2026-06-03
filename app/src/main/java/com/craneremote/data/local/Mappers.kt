package com.craneremote.data.local

import com.craneremote.domain.model.*

// ─── Device ───────────────────────────────────────────────────────────────────

fun DeviceEntity.toDomain() = Device(
    id, name, ipAddress, port,
    PlcType.valueOf(plcType), rack, slot, path, description, createdAt
)

fun Device.toEntity() = DeviceEntity(
    id, name, ipAddress, port,
    plcType.name, rack, slot, path, description, createdAt
)

// ─── Subsystem ────────────────────────────────────────────────────────────────

fun SubsystemEntity.toDomain() = Subsystem(
    id, deviceId, name,
    SubsystemAxis.valueOf(axis), order,
    tagPositive, tagNegative,
    tagSpeedRead, tagSpeedWrite,
    defaultSpeedPct
)

fun Subsystem.toEntity() = SubsystemEntity(
    id, deviceId, name,
    axis.name, order,
    tagPositive, tagNegative,
    tagSpeedRead, tagSpeedWrite,
    defaultSpeedPct
)

// ─── FixedButton ──────────────────────────────────────────────────────────────

fun FixedButtonEntity.toDomain() = FixedButton(
    id, deviceId, slot, label, icon, tag,
    ButtonBehavior.valueOf(behavior), activeColor, isEnabled
)

fun FixedButton.toEntity() = FixedButtonEntity(
    id, deviceId, slot, label, icon, tag,
    behavior.name, activeColor, isEnabled
)

// ─── CommandLog ───────────────────────────────────────────────────────────────

fun CommandLogEntity.toDomain() = CommandLog(
    id, deviceId, deviceName, subsystemName,
    direction, tagAddress, value, success, response, timestamp
)

fun CommandLog.toEntity() = CommandLogEntity(
    id, deviceId, deviceName, subsystemName,
    direction, tagAddress, value, success, response, timestamp
)
