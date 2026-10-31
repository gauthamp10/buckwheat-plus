package com.danilkinkin.buckwheat.autoImport

import android.os.Process
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.danilkinkin.buckwheat.budgetDataStore
import com.danilkinkin.buckwheat.data.dao.DetectedSpendDao
import com.danilkinkin.buckwheat.data.dao.TransactionDao
import com.danilkinkin.buckwheat.data.entities.DetectedSpendStatus
import com.danilkinkin.buckwheat.data.entities.Transaction
import com.danilkinkin.buckwheat.data.entities.TransactionType
import com.danilkinkin.buckwheat.di.SpendsRepository
import com.danilkinkin.buckwheat.di.lastChangeDailyBudgetDateStoreKey
import com.danilkinkin.buckwheat.util.DAY
import com.danilkinkin.buckwheat.util.roundToDay
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.util.Date
import javax.inject.Inject

/**
 * Drives the real notification listener, database and budget code,
 * the way Android would deliver notifications on a phone.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [35])
class NotificationAutoDeductionTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var spendsRepository: SpendsRepository

    @Inject
    lateinit var autoImportRepository: AutoImportRepository

    @Inject
    lateinit var detectedSpendDao: DetectedSpendDao

    @Inject
    lateinit var transactionDao: TransactionDao

    private val context get() = ApplicationProvider.getApplicationContext<HiltTestApplication>()
    private lateinit var service: SpendNotificationListenerService
    private var notificationId = 0
    private val now = System.currentTimeMillis()

    @Before
    fun setUp() = runBlocking {
        hiltRule.inject()

        // 1000 for 10 days, so the daily budget is 100
        spendsRepository.setBudget(BigDecimal("1000"), Date(now + 9 * DAY))
        autoImportRepository.setEnabled(true)

        service = Robolectric.buildService(SpendNotificationListenerService::class.java).create().get()
        service.onListenerConnected()
        awaitListenerEnabled(true)
    }

    @Test
    fun deductsSpendsFromNotificationsAndIgnoresDuplicates() = runBlocking {
        assertEquals(amount("100.00"), spendsRepository.getDailyBudget().first())

        // Bank app notification
        post("com.emiratesnbd.android", "Card purchase", "Purchase of AED 45.50 with Card ending 1234 at CARREFOUR DUBAI on 31/10")
        awaitDetected(1)
        assertSpentToday("45.50")
        assertEquals("Carrefour Dubai", spends().single().comment)

        // The same message posted again under a new notification id
        post("com.emiratesnbd.android", "Card purchase", "Purchase of AED 45.50 with Card ending 1234 at CARREFOUR DUBAI on 31/10")
        awaitDetected(2)
        assertSpentToday("45.50")

        // Google Wallet reports the same tap a few minutes later
        post("com.google.android.apps.walletnfcrel", "Carrefour", "AED 45.50 with Mastercard •••• 9999", now + 3 * 60 * 1000)
        awaitDetected(3)
        assertSpentToday("45.50")
        assertEquals(1, spends().size)

        // Notifications that are not spends never reach the database
        post("com.example.weather", "Dubai", "31°C, sunny")
        post("com.emiratesnbd.android", "Security", "OTP 445566 for txn of AED 99.00 on card XX1234")
        post("com.emiratesnbd.android", "Card", "Your card ending 1234 was declined for AED 30.00 at NOON")

        // A second, different spend
        post("com.emiratesnbd.android", "Card purchase", "You spent AED 20.00 at TALABAT using card 1234")
        awaitDetected(4)
        assertSpentToday("65.50")

        // Spend already entered by hand, then the bank notification arrives
        spendsRepository.addSpent(Transaction(TransactionType.SPENT, amount("12.00"), Date(now)))
        post("com.emiratesnbd.android", "Card purchase", "Purchase of AED 12.00 with Card ending 1234 at CAFE NERO")
        awaitDetected(5)
        assertSpentToday("77.50")
        assertEquals(DetectedSpendStatus.DUPLICATE, detected().last().status)

        assertEquals(amount("922.50"), spendsRepository.howMuchBudgetRest())
    }

    @Test
    fun trustsShortAlertsFromBankApps() = runBlocking {
        // Known bank app, trusted from its first alert
        post("com.vipera.ts.starter.MashreqAE", "Transaction alert", "AED 40.00 spent at VOX CINEMAS")
        awaitDetected(1)
        assertSpentToday("40.00")

        // Unknown app: the short alert alone is not enough
        post("com.example.bank", "Transaction alert", "AED 7.00 spent at METRO")
        Thread.sleep(1000)
        assertEquals(1, detected().size)

        // Its full card alert proves it is a bank, after that short alerts count too
        post("com.example.bank", "Card purchase", "Purchase of AED 15.00 with Card ending 4321 at LULU")
        awaitDetected(2)
        post("com.example.bank", "Transaction alert", "AED 9.00 spent at NOON")
        awaitDetected(3)

        assertSpentToday("64.00")
    }

    @Test
    fun ignoresEverythingWhileSwitchedOff() = runBlocking {
        autoImportRepository.setEnabled(false)
        awaitListenerEnabled(false)

        post("com.emiratesnbd.android", "Card purchase", "Purchase of AED 45.50 with Card ending 1234 at CARREFOUR")
        Thread.sleep(1000)

        assertEquals(0, detected().size)
        assertSpentToday("0.00")
    }

    @Test
    fun waitsForNewDayBeforeDeducting() = runBlocking {
        // The app has not been opened yet today
        setLastChangeDailyBudgetDate(now - DAY)

        post("com.emiratesnbd.android", "Card purchase", "Purchase of AED 5.00 with Card ending 1234 at METRO")
        awaitDetected(1)
        assertEquals(DetectedSpendStatus.PENDING, detected().single().status)
        assertEquals(0, spends().size)

        // The app is opened and starts the day
        setLastChangeDailyBudgetDate(now)
        autoImportRepository.importPending()

        assertEquals(DetectedSpendStatus.IMPORTED, detected().single().status)
        assertEquals(1, spends().size)
        assertSpentToday("5.00")
    }

    private fun post(packageName: String, title: String, text: String, postTime: Long = now) {
        val notification = NotificationCompat.Builder(context, "test")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setWhen(postTime)
            .build()

        val statusBarNotification = StatusBarNotification(
            packageName,
            packageName,
            ++notificationId,
            null,
            0,
            0,
            0,
            notification,
            Process.myUserHandle(),
            postTime,
        )

        service.onNotificationPosted(statusBarNotification)
    }

    private fun detected() = detectedSpendDao.getBetween(Date(0), Date(Long.MAX_VALUE))

    private fun spends() = transactionDao.getBetween(TransactionType.SPENT, Date(0), Date(Long.MAX_VALUE))

    private fun awaitDetected(count: Int) = await("$count detected spends") { detected().size >= count }

    private fun awaitListenerEnabled(enabled: Boolean) {
        val field = SpendNotificationListenerService::class.java.getDeclaredField("isEnabled")
        field.isAccessible = true

        await("listener enabled = $enabled") { field.getBoolean(service) == enabled }
    }

    private fun await(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000

        while (!condition()) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("Timed out waiting for $what")
            Thread.sleep(50)
        }

        // Let the deduction that follows the database record finish
        Thread.sleep(300)
    }

    private suspend fun assertSpentToday(expected: String) {
        assertEquals(amount(expected), spendsRepository.getSpentFromDailyBudget().first())
    }

    private suspend fun setLastChangeDailyBudgetDate(time: Long) {
        context.budgetDataStore.edit {
            it[lastChangeDailyBudgetDateStoreKey] = roundToDay(Date(time)).time
        }
    }

    private fun amount(value: String) = BigDecimal(value).setScale(2)
}
