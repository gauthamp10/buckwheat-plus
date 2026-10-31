package com.danilkinkin.buckwheat.budgetNotification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BudgetNotificationViewModel @Inject constructor(
    private val budgetNotificationManager: BudgetNotificationManager,
) : ViewModel() {
    val isEnabled = budgetNotificationManager.isEnabled().asLiveData()

    fun hasPermission() = budgetNotificationManager.hasPermission()

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            budgetNotificationManager.setEnabled(enabled)
            budgetNotificationManager.refresh()
        }
    }
}
