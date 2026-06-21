package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "block_logs")
data class BlockLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val appName: String,
    val scannedUrlOrText: String,
    val detectedKeyword: String,
    val actionTaken: String,
    val isViolation: Boolean
)

@Entity(tableName = "shield_settings")
data class ShieldSettings(
    @PrimaryKey val id: Int = 1,
    val isShieldActive: Boolean = false,
    val shieldEndTimestampMs: Long = 0L,
    val customKeywords: String = "",
    val language: String = "en",
    val isAdminLockActive: Boolean = false,
    val adminLockEndTimestampMs: Long = 0L,
    val adminLockDurationDays: Int = 0
)
