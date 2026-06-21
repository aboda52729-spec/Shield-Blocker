package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ShieldDao {
    @Query("SELECT * FROM block_logs ORDER BY timestamp DESC LIMIT 100")
    fun getAllLogs(): Flow<List<BlockLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: BlockLog)

    @Query("DELETE FROM block_logs")
    suspend fun clearLogs()

    @Query("SELECT * FROM shield_settings WHERE id = 1")
    fun getSettingsFlow(): Flow<ShieldSettings?>

    @Query("SELECT * FROM shield_settings WHERE id = 1")
    suspend fun getSettingsDirect(): ShieldSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateSettings(settings: ShieldSettings)
}
