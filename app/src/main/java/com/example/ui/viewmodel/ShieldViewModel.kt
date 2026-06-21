package com.example.ui.viewmodel

import android.app.Application
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BlockerAccessibilityService
import com.example.ShieldDeviceAdminReceiver
import com.example.data.*
import com.example.ui.translation.AppStrings
import com.example.ui.translation.LocalizedStrings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

class ShieldViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ShieldRepository
    val settingsFlow: StateFlow<ShieldSettings?>

    private val _isAccessibilityEnabled = MutableStateFlow(false)
    val isAccessibilityEnabled = _isAccessibilityEnabled.asStateFlow()

    private val _isDeviceAdminEnabled = MutableStateFlow(false)
    val isDeviceAdminEnabled = _isDeviceAdminEnabled.asStateFlow()

    private val _timerRemainingMs = MutableStateFlow(0L)
    val timerRemainingMs = _timerRemainingMs.asStateFlow()

    private val _adminLockRemainingMs = MutableStateFlow(0L)
    val adminLockRemainingMs = _adminLockRemainingMs.asStateFlow()

    private val _activeStrings = MutableStateFlow(LocalizedStrings.English)
    val activeStrings = _activeStrings.asStateFlow()

    private val _currentLanguage = MutableStateFlow("en")
    val currentLanguage = _currentLanguage.asStateFlow()

    private var shieldCountdownJob: Job? = null
    private var adminLockCountdownJob: Job? = null

    init {
        val dao = ShieldDatabase.getDatabase(application).shieldDao()
        repository = ShieldRepository(dao)

        settingsFlow = repository.settingsFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

        // Initialize state-tracking coroutine
        viewModelScope.launch {
            // Check settings status initially
            val currentSettings = repository.getSettings()
            _currentLanguage.value = currentSettings.language
            _activeStrings.value = if (currentSettings.language == "ar") {
                LocalizedStrings.Arabic
            } else {
                LocalizedStrings.English
            }

            repository.settingsFlow.collect { settings ->
                if (settings != null) {
                    _currentLanguage.value = settings.language
                    _activeStrings.value = if (settings.language == "ar") {
                        LocalizedStrings.Arabic
                    } else {
                        LocalizedStrings.English
                    }

                    manageShieldCountdown(settings)
                    manageAdminLockCountdown(settings)
                }
            }
        }
    }

    fun checkSecurityPerms(context: Context) {
        // Check accessibility
        val serviceName = ComponentName(context, BlockerAccessibilityService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        val isEnabled = enabledServices.contains(serviceName) || BlockerAccessibilityService.isServiceRunning
        _isAccessibilityEnabled.value = isEnabled

        // Check device admin status
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, ShieldDeviceAdminReceiver::class.java)
        _isDeviceAdminEnabled.value = dpm.isAdminActive(adminComponent)
    }

    private fun manageShieldCountdown(settings: ShieldSettings) {
        shieldCountdownJob?.cancel()
        if (settings.isShieldActive) {
            shieldCountdownJob = viewModelScope.launch(Dispatchers.Default) {
                while (isActive) {
                    val now = System.currentTimeMillis()
                    val remaining = settings.shieldEndTimestampMs - now
                    if (remaining <= 0) {
                        _timerRemainingMs.value = 0L
                        withContext(Dispatchers.IO) {
                            repository.updateSettings(
                                settings.copy(isShieldActive = false, shieldEndTimestampMs = 0L)
                            )
                        }
                        break
                    } else {
                        _timerRemainingMs.value = remaining
                    }
                    delay(1000)
                }
            }
        } else {
            _timerRemainingMs.value = 0L
        }
    }

    private fun manageAdminLockCountdown(settings: ShieldSettings) {
        adminLockCountdownJob?.cancel()
        if (settings.isAdminLockActive) {
            adminLockCountdownJob = viewModelScope.launch(Dispatchers.Default) {
                while (isActive) {
                    val now = System.currentTimeMillis()
                    val remaining = settings.adminLockEndTimestampMs - now
                    if (remaining <= 0) {
                        _adminLockRemainingMs.value = 0L
                        withContext(Dispatchers.IO) {
                            repository.updateSettings(
                                settings.copy(isAdminLockActive = false, adminLockEndTimestampMs = 0L, adminLockDurationDays = 0)
                            )
                        }
                        break
                    } else {
                        _adminLockRemainingMs.value = remaining
                    }
                    delay(1000)
                }
            }
        } else {
            _adminLockRemainingMs.value = 0L
        }
    }

    fun toggleLanguage() {
        viewModelScope.launch(Dispatchers.IO) {
            val settings = repository.getSettings()
            val nextLang = if (settings.language == "ar") "en" else "ar"
            repository.updateSettings(settings.copy(language = nextLang))
        }
    }

    fun activateShield(durationMs: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val settings = repository.getSettings()
            val endTimestamp = System.currentTimeMillis() + durationMs
            repository.updateSettings(
                settings.copy(
                    isShieldActive = true,
                    shieldEndTimestampMs = endTimestamp
                )
            )
        }
    }

    fun deactivateShield() {
        viewModelScope.launch(Dispatchers.IO) {
            val settings = repository.getSettings()
            repository.updateSettings(
                settings.copy(isShieldActive = false, shieldEndTimestampMs = 0L)
            )
        }
    }

    fun activateAdminLock(daysCount: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val settings = repository.getSettings()
            val durationMs = daysCount * 24L * 60 * 60 * 1000L
            val endTimestamp = System.currentTimeMillis() + durationMs
            repository.updateSettings(
                settings.copy(
                    isAdminLockActive = true,
                    adminLockEndTimestampMs = endTimestamp,
                    adminLockDurationDays = daysCount
                )
            )
        }
    }

    fun updateCustomKeywords(words: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val settings = repository.getSettings()
            repository.updateSettings(settings.copy(customKeywords = words))
        }
    }
}
