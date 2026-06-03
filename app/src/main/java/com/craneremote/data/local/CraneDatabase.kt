package com.craneremote.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ─── Entities ─────────────────────────────────────────────────────────────────

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val ipAddress: String,
    val port: Int,
    val plcType: String,
    val rack: Int,
    val slot: Int,
    val path: String,
    val description: String,
    val createdAt: Long
)

@Entity(
    tableName = "subsystems",
    foreignKeys = [ForeignKey(
        entity = DeviceEntity::class,
        parentColumns = ["id"],
        childColumns = ["deviceId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("deviceId")]
)
data class SubsystemEntity(
    @PrimaryKey val id: String,
    val deviceId: String,
    val name: String,
    val axis: String,           // SubsystemAxis.name
    val order: Int,
    val tagPositive: String,
    val tagNegative: String,
    val tagSpeedRead: String,
    val tagSpeedWrite: String,
    val defaultSpeedPct: Int
)

@Entity(
    tableName = "fixed_buttons",
    foreignKeys = [ForeignKey(
        entity = DeviceEntity::class,
        parentColumns = ["id"],
        childColumns = ["deviceId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("deviceId")]
)
data class FixedButtonEntity(
    @PrimaryKey val id: String,
    val deviceId: String,
    val slot: Int,
    val label: String,
    val icon: String,
    val tag: String,
    val behavior: String,       // ButtonBehavior.name
    val activeColor: String,
    val isEnabled: Boolean
)

@Entity(tableName = "command_logs")
data class CommandLogEntity(
    @PrimaryKey val id: String,
    val deviceId: String,
    val deviceName: String,
    val subsystemName: String,
    val direction: String,
    val tagAddress: String,
    val value: String,
    val success: Boolean,
    val response: String,
    val timestamp: Long
)

// ─── DAOs ─────────────────────────────────────────────────────────────────────

@Dao
interface DeviceDao {
    @Query("SELECT * FROM devices ORDER BY name ASC")
    fun getAll(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE id = :id")
    fun getById(id: String): Flow<DeviceEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(device: DeviceEntity)

    @Update
    suspend fun update(device: DeviceEntity)

    @Query("DELETE FROM devices WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface SubsystemDao {
    @Query("SELECT * FROM subsystems WHERE deviceId = :deviceId ORDER BY `order` ASC")
    fun getByDevice(deviceId: String): Flow<List<SubsystemEntity>>

    @Query("SELECT * FROM subsystems WHERE id = :id")
    fun getById(id: String): Flow<SubsystemEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(subsystem: SubsystemEntity)

    @Update
    suspend fun update(subsystem: SubsystemEntity)

    @Query("DELETE FROM subsystems WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM subsystems WHERE deviceId = :deviceId")
    suspend fun deleteByDevice(deviceId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(subsystems: List<SubsystemEntity>)
}

@Dao
interface FixedButtonDao {
    @Query("SELECT * FROM fixed_buttons WHERE deviceId = :deviceId ORDER BY slot ASC")
    fun getByDevice(deviceId: String): Flow<List<FixedButtonEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(button: FixedButtonEntity)

    @Update
    suspend fun update(button: FixedButtonEntity)

    @Query("DELETE FROM fixed_buttons WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM fixed_buttons WHERE deviceId = :deviceId")
    suspend fun deleteByDevice(deviceId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(buttons: List<FixedButtonEntity>)
}

@Dao
interface CommandLogDao {
    @Query("SELECT * FROM command_logs WHERE deviceId = :deviceId ORDER BY timestamp DESC LIMIT 500")
    fun getByDevice(deviceId: String): Flow<List<CommandLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: CommandLogEntity)

    @Query("DELETE FROM command_logs WHERE deviceId = :deviceId")
    suspend fun clearByDevice(deviceId: String)

    @Query("DELETE FROM command_logs")
    suspend fun clearAll()
}

// ─── Database ──────────────────────────────────────────────────────────────────

@Database(
    entities = [
        DeviceEntity::class,
        SubsystemEntity::class,
        FixedButtonEntity::class,
        CommandLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class CraneDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
    abstract fun subsystemDao(): SubsystemDao
    abstract fun fixedButtonDao(): FixedButtonDao
    abstract fun commandLogDao(): CommandLogDao
}
