package com.danilkinkin.buckwheat.autoImport

import android.app.Application
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AutoImportViewModel @Inject constructor(
    application: Application,
    private val autoImportRepository: AutoImportRepository,
    private val smsInboxReader: SmsInboxReader,
) : AndroidViewModel(application) {
    val isEnabled = autoImportRepository.isEnabled().asLiveData()
    val hasNotificationAccess = MutableLiveData(false)
    val hasSmsAccess = MutableLiveData(false)
    val hasBatteryExemption = MutableLiveData(false)
    val isSyncing = MutableLiveData(false)
    val lastSyncSummary: MutableLiveData<AutoImportSummary?> = MutableLiveData(null)

    init {
        refreshAccess()
    }

    fun refreshAccess() {
        hasNotificationAccess.value = SpendNotificationListenerService.hasAccess(getApplication())
        hasSmsAccess.value = smsInboxReader.hasPermission()

        val application = getApplication<Application>()
        val powerManager = application.getSystemService(PowerManager::class.java)
        hasBatteryExemption.value = powerManager.isIgnoringBatteryOptimizations(application.packageName)
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            autoImportRepository.setEnabled(enabled)

            if (enabled) syncNow()
        }
    }

    fun syncNow() {
        if (isSyncing.value == true) return

        viewModelScope.launch {
            isSyncing.value = true

            if (SpendNotificationListenerService.hasAccess(getApplication())) {
                NotificationListenerService.requestRebind(
                    SpendNotificationListenerService.componentName(getApplication())
                )
            }

            lastSyncSummary.value = autoImportRepository.syncAll()
            isSyncing.value = false
        }
    }
}
