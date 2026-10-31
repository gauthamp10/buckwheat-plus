package com.danilkinkin.buckwheat.autoImport

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.danilkinkin.buckwheat.data.ExtendCurrency
import com.danilkinkin.buckwheat.data.dao.DetectedSpendDao
import com.danilkinkin.buckwheat.data.dao.TransactionDao
import com.danilkinkin.buckwheat.data.entities.DetectedSpend
import com.danilkinkin.buckwheat.data.entities.DetectedSpendStatus
import com.danilkinkin.buckwheat.data.entities.Transaction
import com.danilkinkin.buckwheat.data.entities.TransactionType
import com.danilkinkin.buckwheat.di.GetCurrentDateUseCase
import com.danilkinkin.buckwheat.di.SpendsRepository
import com.danilkinkin.buckwheat.settingsDataStore
import com.danilkinkin.buckwheat.util.isSameDay
import com.danilkinkin.buckwheat.util.roundToDay
import com.danilkinkin.buckwheat.widget.extend.ExtendWidgetReceiver
import com.danilkinkin.buckwheat.widget.minimal.MinimalWidgetReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

val autoImportEnabledStoreKey = booleanPreferencesKey("autoImportEnabled")
val autoImportSinceStoreKey = longPreferencesKey("autoImportSince")
val autoImportSmsSyncedUntilStoreKey = longPreferencesKey("autoImportSmsSyncedUntil")
val autoImportBankAppsStoreKey = stringSetPreferencesKey("autoImportBankApps")

enum class ProcessOutcome {
    DISABLED,
    ALREADY_SEEN,
    OUT_OF_RANGE,
    NOT_A_SPEND,
    IMPORTED,
    PENDING,
    DUPLICATE,
    SKIPPED,
}

data class AutoImportSummary(
    val imported: Int = 0,
    val pending: Int = 0,
    val duplicates: Int = 0,
    val skipped: Int = 0,
) {
    fun with(outcome: ProcessOutcome) = when (outcome) {
        ProcessOutcome.IMPORTED -> copy(imported = imported + 1)
        ProcessOutcome.PENDING -> copy(pending = pending + 1)
        ProcessOutcome.DUPLICATE -> copy(duplicates = duplicates + 1)
        ProcessOutcome.SKIPPED -> copy(skipped = skipped + 1)
        else -> this
    }
}

@Singleton
class AutoImportRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val detectedSpendDao: DetectedSpendDao,
    private val transactionDao: TransactionDao,
    private val spendsRepository: SpendsRepository,
    private val smsInboxReader: SmsInboxReader,
    private val getCurrentDateUseCase: GetCurrentDateUseCase,
) {
    companion object {
        private const val TAG = "AutoImportRepository"

        // Re-read a bit of already synced inbox in case an SMS was written with an older date
        private const val SMS_SYNC_OVERLAP_MS = 10 * 60 * 1000L
        private const val FUTURE_TOLERANCE_MS = 5 * 60 * 1000L

        // Trusted from the first alert, any other bank app is learned from its first full card alert
        private val KNOWN_BANK_PACKAGES = setOf(
            "com.emiratesnbd.android",
            "com.vipera.ts.starter.MashreqAE",
        )
    }

    private val mutex = Mutex()

    // Set by the notification listener while it is connected
    @Volatile
    var activeNotificationsProvider: (() -> List<SpendCandidate>)? = null

    fun isEnabled() = context.settingsDataStore.data.map { it[autoImportEnabledStoreKey] ?: false }

    suspend fun setEnabled(enabled: Boolean) {
        context.settingsDataStore.edit {
            val wasEnabled = it[autoImportEnabledStoreKey] ?: false

            it[autoImportEnabledStoreKey] = enabled

            // Each fresh start picks up everything spent since the beginning of today
            if (enabled && !wasEnabled) {
                it[autoImportSinceStoreKey] = roundToDay(getCurrentDateUseCase()).time
                it.remove(autoImportSmsSyncedUntilStoreKey)
            }
        }
    }

    suspend fun syncAll(): AutoImportSummary = withContext(Dispatchers.IO) {
        if (!isEnabled().first()) return@withContext AutoImportSummary()

        val syncStartedAt = getCurrentDateUseCase().time
        val floor = syncFloor()
        val smsSyncedUntil = context.settingsDataStore.data.first()[autoImportSmsSyncedUntilStoreKey]
        val smsSince = maxOf(floor, (smsSyncedUntil ?: floor) - SMS_SYNC_OVERLAP_MS)

        val candidates = smsInboxReader.readSince(smsSince) + readActiveNotifications()

        val summary = processAll(candidates)

        if (smsInboxReader.hasPermission()) {
            context.settingsDataStore.edit {
                it[autoImportSmsSyncedUntilStoreKey] = syncStartedAt
            }
        }

        Log.d(TAG, "Sync finished $summary")

        summary
    }

    suspend fun processAll(candidates: List<SpendCandidate>): AutoImportSummary {
        return candidates
            .sortedBy { it.timestamp }
            .fold(AutoImportSummary()) { summary, candidate -> summary.with(process(candidate)) }
    }

    suspend fun process(candidate: SpendCandidate): ProcessOutcome = withContext(Dispatchers.IO) {
        val outcome = mutex.withLock { processLocked(candidate) }

        if (outcome == ProcessOutcome.IMPORTED) requestWidgetsUpdate()

        outcome
    }

    suspend fun importPending(): Int = withContext(Dispatchers.IO) {
        val imported = mutex.withLock {
            if (!isEnabled().first() || !isDayStarted()) return@withLock 0

            var count = 0

            detectedSpendDao.getByStatus(DetectedSpendStatus.PENDING).forEach { pending ->
                if (canImport(pending.currency, pending.date.time)) {
                    commitSpend(pending)
                    count += 1
                } else {
                    detectedSpendDao.update(
                        pending.copy(status = DetectedSpendStatus.SKIPPED).also { it.uid = pending.uid }
                    )
                }
            }

            count
        }

        if (imported > 0) requestWidgetsUpdate()

        imported
    }

    private suspend fun isBankApp(packageName: String) = packageName in KNOWN_BANK_PACKAGES
            || packageName in context.settingsDataStore.data.first()[autoImportBankAppsStoreKey].orEmpty()

    private suspend fun rememberBankApp(packageName: String) {
        Log.d(TAG, "Learned bank app $packageName")

        context.settingsDataStore.edit {
            it[autoImportBankAppsStoreKey] = it[autoImportBankAppsStoreKey].orEmpty() + packageName
        }
    }

    private fun readActiveNotifications(): List<SpendCandidate> = try {
        activeNotificationsProvider?.invoke() ?: emptyList()
    } catch (e: Exception) {
        Log.w(TAG, "Unable to read active notifications", e)
        emptyList()
    }

    private suspend fun processLocked(candidate: SpendCandidate): ProcessOutcome {
        if (!isEnabled().first()) return ProcessOutcome.DISABLED
        if (detectedSpendDao.getByFingerprint(candidate.fingerprint) != null) {
            return ProcessOutcome.ALREADY_SEEN
        }

        val now = getCurrentDateUseCase().time
        if (candidate.timestamp < syncFloor() || candidate.timestamp > now + FUTURE_TOLERANCE_MS) {
            return ProcessOutcome.OUT_OF_RANGE
        }

        val appPackage = candidate.sourcePackage.takeIf { candidate.channel == SpendChannel.APP }
        val isBankApp = appPackage != null && isBankApp(appPackage)

        val parsed = SpendMessageParser.parse(
            text = candidate.text,
            title = candidate.title,
            isWalletSource = candidate.channel == SpendChannel.WALLET,
            isBankSource = isBankApp,
        ) ?: return ProcessOutcome.NOT_A_SPEND

        // Passed the strict rules, so the app sends real card alerts
        if (appPackage != null && !isBankApp) rememberBankApp(appPackage)

        val footprint = SpendFootprint(
            amount = parsed.amount,
            timestamp = candidate.timestamp,
            channel = candidate.channel,
            sourcePackage = candidate.sourcePackage,
            normalizedText = SpendDuplicateDetector.normalizeText(candidate.text),
            cardLast4 = parsed.cardLast4,
            referenceId = parsed.referenceId,
        )

        val status = when {
            isAlreadyDetected(footprint) || isEnteredManually(footprint) -> DetectedSpendStatus.DUPLICATE
            !canImport(parsed.currency, candidate.timestamp) -> DetectedSpendStatus.SKIPPED
            else -> DetectedSpendStatus.PENDING
        }

        val detectedSpend = DetectedSpend(
            fingerprint = candidate.fingerprint,
            channel = candidate.channel,
            sourcePackage = candidate.sourcePackage,
            value = parsed.amount,
            currency = parsed.currency,
            merchant = parsed.merchant,
            cardLast4 = parsed.cardLast4,
            referenceId = parsed.referenceId,
            date = Date(candidate.timestamp),
            normalizedText = footprint.normalizedText,
            status = status,
        )

        // Recorded before touching the budget so a crash can't import the same message twice
        val uid = detectedSpendDao.insert(detectedSpend)
        if (uid == -1L) return ProcessOutcome.ALREADY_SEEN
        detectedSpend.uid = uid.toInt()

        Log.d(TAG, "Detected spend $detectedSpend")

        return when (status) {
            DetectedSpendStatus.DUPLICATE -> ProcessOutcome.DUPLICATE
            DetectedSpendStatus.SKIPPED -> ProcessOutcome.SKIPPED
            else -> if (isDayStarted()) {
                commitSpend(detectedSpend)
                ProcessOutcome.IMPORTED
            } else {
                ProcessOutcome.PENDING
            }
        }
    }

    private suspend fun commitSpend(detectedSpend: DetectedSpend) {
        val transactionUid = spendsRepository.addSpent(
            Transaction(
                type = TransactionType.SPENT,
                value = detectedSpend.value,
                date = detectedSpend.date,
                comment = detectedSpend.merchant ?: "",
            )
        )

        detectedSpendDao.update(
            detectedSpend.copy(
                status = DetectedSpendStatus.IMPORTED,
                transactionUid = transactionUid,
            ).also { it.uid = detectedSpend.uid }
        )
    }

    private fun isAlreadyDetected(footprint: SpendFootprint): Boolean {
        val window = SpendDuplicateDetector.SAME_MESSAGE_WINDOW_MS

        return detectedSpendDao
            .getBetween(Date(footprint.timestamp - window), Date(footprint.timestamp + window))
            .any { SpendDuplicateDetector.isSameSpend(it.toFootprint(), footprint) }
    }

    private fun isEnteredManually(footprint: SpendFootprint): Boolean {
        val window = SpendDuplicateDetector.CROSS_SOURCE_WINDOW_MS
        val importedUids = detectedSpendDao.getLinkedTransactionUids().toSet()

        return transactionDao
            .getBetween(
                TransactionType.SPENT,
                Date(footprint.timestamp - window),
                Date(footprint.timestamp + window),
            )
            .filter { it.uid !in importedUids }
            .any { SpendDuplicateDetector.isLikelyManualEntry(footprint, it.value, it.date.time) }
    }

    private suspend fun canImport(currency: String?, timestamp: Long): Boolean {
        if (spendsRepository.getLastChangeDailyBudgetDate().first() == null) return false

        val now = getCurrentDateUseCase().time
        val startPeriodDate = spendsRepository.getStartPeriodDate().first()
        val finishPeriodDate = spendsRepository.getFinishPeriodActualDate().first()
            ?: spendsRepository.getFinishPeriodDate().first()
            ?: return false

        if (finishPeriodDate.time <= now) return false
        if (timestamp < startPeriodDate.time || timestamp > finishPeriodDate.time) return false

        val appCurrency = spendsRepository.getCurrency().first()

        // A spend abroad would be deducted with the wrong value
        return !(appCurrency.type == ExtendCurrency.Type.FROM_LIST
                && currency != null
                && currency != appCurrency.value)
    }

    // The app rolls the daily budget over to a new day only when it is opened,
    // until then today's spends would be counted against yesterday
    private suspend fun isDayStarted(): Boolean {
        val lastChangeDailyBudgetDate = spendsRepository.getLastChangeDailyBudgetDate().first()
            ?: return false

        return isSameDay(lastChangeDailyBudgetDate, getCurrentDateUseCase())
    }

    private suspend fun syncFloor(): Long {
        val since = context.settingsDataStore.data.first()[autoImportSinceStoreKey]
            ?: roundToDay(getCurrentDateUseCase()).time

        return maxOf(since, spendsRepository.getStartPeriodDate().first().time)
    }

    private fun requestWidgetsUpdate() {
        ExtendWidgetReceiver.requestUpdateData(context)
        MinimalWidgetReceiver.requestUpdateData(context)
    }
}
