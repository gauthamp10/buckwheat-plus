package com.danilkinkin.buckwheat.autoImport

import android.app.Notification
import android.content.ComponentCallbacks2
import android.content.ComponentName
import android.content.Context
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SpendNotificationListenerService : NotificationListenerService() {
    companion object {
        private const val TAG = "SpendNotificationListener"
        private const val RECENT_FINGERPRINTS_LIMIT = 200

        private val WALLET_PACKAGES = setOf(
            "com.google.android.apps.walletnfcrel",
            "com.google.android.apps.nbu.paisa.user",
            "com.google.android.gms",
            "com.samsung.android.spay",
            "com.samsung.android.samsungpay.gear",
        )

        private val SMS_PACKAGES = setOf(
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.android.mms",
            "com.android.messaging",
        )

        // Personal chats mention amounts too, but they are never a bank alert
        private val IGNORED_PACKAGES = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "org.telegram.messenger",
            "org.thoughtcrime.securesms",
            "com.facebook.orca",
            "com.facebook.katana",
            "com.instagram.android",
            "com.discord",
            "com.Slack",
            "com.viber.voip",
            "com.tencent.mm",
            "jp.naver.line.android",
            "com.skype.raider",
            "com.snapchat.android",
            "com.twitter.android",
        )

        fun hasAccess(context: Context): Boolean = NotificationManagerCompat
            .getEnabledListenerPackages(context)
            .contains(context.packageName)

        fun componentName(context: Context) =
            ComponentName(context, SpendNotificationListenerService::class.java)
    }

    @Inject
    lateinit var autoImportRepository: AutoImportRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var enabledWatcher: Job? = null

    // Read on the binder thread for every notification, so kept in memory
    @Volatile
    private var isEnabled = false

    @Volatile
    private var defaultSmsPackage: String? = null

    // Apps re-post the same notification on every progress or badge update
    private val recentFingerprints = object : LinkedHashMap<String, Unit>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?) =
            size > RECENT_FINGERPRINTS_LIMIT
    }

    override fun onListenerConnected() {
        super.onListenerConnected()

        defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(applicationContext)

        autoImportRepository.activeNotificationsProvider = {
            activeNotifications.orEmpty().mapNotNull { it.toSpendCandidate() }
        }

        enabledWatcher?.cancel()
        enabledWatcher = scope.launch {
            autoImportRepository.isEnabled().distinctUntilChanged().collect { enabled ->
                isEnabled = enabled

                Log.d(TAG, "Listener connected, auto-import enabled: $enabled")

                if (enabled) {
                    // First connection right after access is granted picks up what is already in the shade
                    autoImportRepository.syncAll()
                } else {
                    // Stops the system from waking the app for every notification while the feature is off
                    requestUnbind()
                }
            }
        }
    }

    override fun onListenerDisconnected() {
        Log.d(TAG, "Listener disconnected")

        isEnabled = false
        enabledWatcher?.cancel()
        autoImportRepository.activeNotificationsProvider = null

        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!isEnabled) {
            Log.v(TAG, "Notification from ${sbn.packageName} skipped, auto-import is off")
            return
        }

        val candidate = sbn.toSpendCandidate() ?: return

        synchronized(recentFingerprints) {
            if (recentFingerprints.put(candidate.fingerprint, Unit) != null) return
        }

        scope.launch {
            val outcome = autoImportRepository.process(candidate)

            Log.d(TAG, "Notification from ${sbn.packageName} processed: $outcome")
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        // Only a shortcut, the database still catches repeated notifications
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            synchronized(recentFingerprints) { recentFingerprints.clear() }
        }
    }

    override fun onDestroy() {
        autoImportRepository.activeNotificationsProvider = null
        scope.cancel()

        super.onDestroy()
    }

    private fun StatusBarNotification.toSpendCandidate(): SpendCandidate? {
        if (packageName == applicationContext.packageName) return null
        if (packageName in IGNORED_PACKAGES) return null

        val notification = notification ?: return null

        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) return null

        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val plainText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

        val lastMessage = if (notification.extras.containsKey(Notification.EXTRA_MESSAGES)) {
            NotificationCompat.MessagingStyle
                .extractMessagingStyleFromNotification(notification)
                ?.messages
                ?.lastOrNull()
        } else {
            null
        }

        val text = lastMessage?.text?.toString() ?: plainText ?: return null

        if (!SpendMessageParser.mightBeSpend(text)) return null

        val channel = when (packageName) {
            in WALLET_PACKAGES -> SpendChannel.WALLET
            in SMS_PACKAGES, defaultSmsPackage -> SpendChannel.SMS
            else -> SpendChannel.APP
        }

        val timestamp = lastMessage?.timestamp?.takeIf { it > 0 }
            ?: notification.`when`.takeIf { it in 1..postTime }
            ?: postTime

        return SpendCandidate(
            fingerprint = "notification:$key:" + SpendDuplicateDetector.hash(
                SpendDuplicateDetector.normalizeText(text)
            ),
            channel = channel,
            sourcePackage = packageName,
            title = title,
            text = text,
            timestamp = timestamp,
        )
    }
}
