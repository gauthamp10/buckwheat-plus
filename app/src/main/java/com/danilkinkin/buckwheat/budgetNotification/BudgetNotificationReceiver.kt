package com.danilkinkin.buckwheat.budgetNotification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Restores the notification after reboot or app update and refreshes it at midnight
class BudgetNotificationReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_REFRESH = "com.gauthamp10.buckwheatplus.budgetNotification.REFRESH"
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ReceiverEntryPoint {
        fun budgetNotificationManager(): BudgetNotificationManager
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(
                ACTION_REFRESH,
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
            )
        ) return

        val manager = EntryPointAccessors
            .fromApplication(context.applicationContext, ReceiverEntryPoint::class.java)
            .budgetNotificationManager()

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.Default).launch {
            try {
                manager.update()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
