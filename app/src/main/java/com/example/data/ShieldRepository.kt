package com.example.data

import kotlinx.coroutines.flow.Flow

class ShieldRepository(private val shieldDao: ShieldDao) {
    val allLogs: Flow<List<BlockLog>> = shieldDao.getAllLogs()
    val settingsFlow: Flow<ShieldSettings?> = shieldDao.getSettingsFlow()

    suspend fun insertLog(log: BlockLog) {
        shieldDao.insertLog(log)
    }

    suspend fun clearLogs() {
        shieldDao.clearLogs()
    }

    suspend fun getSettings(): ShieldSettings {
        return shieldDao.getSettingsDirect() ?: ShieldSettings(id = 1).also {
            shieldDao.updateSettings(it)
        }
    }

    suspend fun updateSettings(settings: ShieldSettings) {
        shieldDao.updateSettings(settings)
    }
}
