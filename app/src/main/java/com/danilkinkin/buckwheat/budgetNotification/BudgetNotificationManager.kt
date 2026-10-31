package com.danilkinkin.buckwheat.budgetNotification

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.danilkinkin.buckwheat.MainActivity
import com.danilkinkin.buckwheat.R
import com.danilkinkin.buckwheat.budgetDataStore
import com.danilkinkin.buckwheat.di.SpendsRepository
import com.danilkinkin.buckwheat.settingsDataStore
import com.danilkinkin.buckwheat.util.DAY
import com.danilkinkin.buckwheat.util.countDays
import com.danilkinkin.buckwheat.util.isToday
import com.danilkinkin.buckwheat.util.numberFormat
import com.danilkinkin.buckwheat.util.roundToDay
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

val budgetNotificationEnabledStoreKey = booleanPreferencesKey("budgetNotificationEnabled")

@Singleton
class BudgetNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val spendsRepository: SpendsRepository,
) {
    companion object {
        private const val CHANNEL_ID = "daily_budget"
        private const val NOTIFICATION_ID = 1001
        private const val MIDNIGHT_REQUEST_CODE = 1001
    }

    private data class Content(
        val title: String,
        val text: String,
        val stats: Stats? = null,
    )

    private data class Stats(
        // Amount overspent when isOver
        val leftToday: String,
        val isOver: Boolean,
        val spentToday: String,
        val leftTotal: String,
        val dailyBudget: String,
        val daysLeft: Int,
        val spentPercent: Int,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val refreshRequests = MutableStateFlow(0L)
    private var observer: Job? = null

    fun isEnabled() = context.settingsDataStore.data.map {
        it[budgetNotificationEnabledStoreKey] ?: false
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.settingsDataStore.edit {
            it[budgetNotificationEnabledStoreKey] = enabled
        }
    }

    fun hasPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true

        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    // Every budget change (manual or auto-imported spend) is written to the budget store,
    // so following it keeps the notification in sync without any polling
    @Synchronized
    fun start() {
        if (observer != null) return

        observer = scope.launch {
            combine(
                isEnabled(),
                context.budgetDataStore.data,
                refreshRequests,
            ) { enabled, _, _ -> enabled }.collect { enabled ->
                if (enabled) show() else hide()
            }
        }
    }

    fun refresh() {
        refreshRequests.value = System.nanoTime()
    }

    suspend fun update() {
        if (isEnabled().first()) show() else hide()
    }

    private suspend fun show() {
        if (!hasPermission()) return

        createChannel()

        val content = buildContent()
        val openAppIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_balance_wallet)
            .setContentTitle(context.getString(R.string.app_name))
            .build()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_balance_wallet)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setLocalOnly(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .apply {
                content.stats?.let { stats ->
                    // Title and text stay for accessibility and for watches that skip custom views
                    setStyle(NotificationCompat.DecoratedCustomViewStyle())
                    setCustomContentView(buildView(R.layout.notification_budget, stats, expanded = false))
                    setCustomBigContentView(buildView(R.layout.notification_budget_big, stats, expanded = true))
                }
            }
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            return
        }

        scheduleMidnightRefresh()
    }

    private fun buildView(layout: Int, stats: Stats, expanded: Boolean) =
        RemoteViews(context.packageName, layout).apply {
            val leftColor = ContextCompat.getColor(
                context,
                if (stats.isOver) R.color.notification_budget_over else R.color.notification_budget_left,
            )

            setImageViewResource(
                R.id.left_icon,
                if (stats.isOver) R.drawable.ic_warning else R.drawable.ic_balance_wallet,
            )
            setInt(R.id.left_icon, "setColorFilter", leftColor)
            setTextViewText(R.id.left_value, stats.leftToday)
            if (stats.isOver) setTextColor(R.id.left_value, leftColor)
            setTextViewText(R.id.spent_value, stats.spentToday)

            setProgressBar(R.id.progress, 100, stats.spentPercent.coerceIn(0, 100), false)
            setViewVisibility(R.id.progress, if (stats.isOver) View.GONE else View.VISIBLE)
            setViewVisibility(R.id.progress_over, if (stats.isOver) View.VISIBLE else View.GONE)

            if (expanded) {
                setTextViewText(
                    R.id.left_label,
                    context.getString(
                        if (stats.isOver) R.string.budget_notification_over_label else R.string.budget_notification_left_label
                    ),
                )
                setTextViewText(R.id.total_value, stats.leftTotal)
                setTextViewText(
                    R.id.footer,
                    context.getString(
                        R.string.budget_notification_footer,
                        stats.dailyBudget,
                        context.resources.getQuantityString(R.plurals.days_count, stats.daysLeft, stats.daysLeft),
                    ),
                )
            }
        }

    private fun hide() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        context.getSystemService(AlarmManager::class.java).cancel(midnightIntent())
    }

    private suspend fun buildContent(): Content {
        val now = Date()
        val finishPeriodDate = spendsRepository.getFinishPeriodDate().first()
            ?: return Content(
                title = context.getString(R.string.budget_not_set),
                text = context.getString(R.string.budget_notification_set_budget),
            )
        val finishPeriodActualDate = spendsRepository.getFinishPeriodActualDate().first()

        if (finishPeriodDate.time <= now.time || (finishPeriodActualDate?.time ?: Long.MAX_VALUE) <= now.time) {
            return Content(
                title = context.getString(R.string.budget_notification_period_finished),
                text = context.getString(R.string.budget_notification_period_finished_description),
            )
        }

        val lastChangeDailyBudgetDate = spendsRepository.getLastChangeDailyBudgetDate().first()

        // Today's budget is calculated only after the app is opened on a new day
        if (lastChangeDailyBudgetDate === null || !isToday(lastChangeDailyBudgetDate)) {
            return Content(
                title = context.getString(R.string.budget_notification_new_day),
                text = context.getString(R.string.budget_notification_new_day_description),
            )
        }

        val currency = spendsRepository.getCurrency().first()
        val dailyBudget = spendsRepository.getDailyBudget().first()
        val spentFromDailyBudget = spendsRepository.getSpentFromDailyBudget().first()
        val restOfDay = dailyBudget - spentFromDailyBudget
        val restOfPeriod = spendsRepository.howMuchBudgetRest()

        fun format(value: BigDecimal) = numberFormat(context, value, currency).trim()

        val spentPercent = if (dailyBudget > BigDecimal.ZERO) {
            spentFromDailyBudget
                .multiply(BigDecimal(100))
                .divide(dailyBudget, 0, RoundingMode.HALF_UP)
                .toInt()
        } else {
            100
        }

        val isOver = restOfDay < BigDecimal.ZERO
        val stats = Stats(
            leftToday = format(restOfDay.abs()),
            isOver = isOver,
            spentToday = format(spentFromDailyBudget),
            leftTotal = format(restOfPeriod),
            dailyBudget = format(dailyBudget),
            daysLeft = countDays(finishPeriodDate, now).coerceAtLeast(1),
            spentPercent = if (isOver) 100 else spentPercent,
        )

        return if (!isOver) {
            Content(
                title = context.getString(R.string.budget_notification_left_today, format(restOfDay)),
                text = context.getString(
                    R.string.budget_notification_spent_of,
                    format(spentFromDailyBudget),
                    format(dailyBudget),
                    format(restOfPeriod),
                ),
                stats = stats,
            )
        } else {
            Content(
                title = context.getString(R.string.budget_notification_over_today, format(restOfDay.negate())),
                text = context.getString(R.string.budget_notification_rest_of_period, format(restOfPeriod)),
                stats = stats,
            )
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.budget_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.budget_notification_channel_description)
            setShowBadge(false)
        }

        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    // Inexact on purpose: a few minutes late is fine and needs no exact alarm permission
    private fun scheduleMidnightRefresh() {
        val nextMidnight = roundToDay(Date()).time + DAY + 60 * 1000L

        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(
            AlarmManager.RTC,
            nextMidnight,
            midnightIntent(),
        )
    }

    private fun midnightIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        MIDNIGHT_REQUEST_CODE,
        Intent(context, BudgetNotificationReceiver::class.java)
            .setAction(BudgetNotificationReceiver.ACTION_REFRESH),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
