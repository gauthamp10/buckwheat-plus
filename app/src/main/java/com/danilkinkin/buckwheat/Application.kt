package com.danilkinkin.buckwheat

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks2
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import com.danilkinkin.buckwheat.budgetNotification.BudgetNotificationManager
import com.danilkinkin.buckwheat.widget.extend.ExtendWidgetReceiver
import com.danilkinkin.buckwheat.widget.minimal.MinimalWidgetReceiver
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class Application : Application() {
    @Inject
    lateinit var budgetNotificationManager: BudgetNotificationManager

    override fun onCreate() {
        super.onCreate()

        budgetNotificationManager.start()

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {

            }

            override fun onActivityStarted(activity: Activity) {

            }

            override fun onActivityResumed(activity: Activity) {

            }

            override fun onActivityPaused(activity: Activity) {
                ExtendWidgetReceiver.requestUpdateData(activity.applicationContext)
                MinimalWidgetReceiver.requestUpdateData(activity.applicationContext)
                budgetNotificationManager.refresh()
            }

            override fun onActivityStopped(activity: Activity) {

            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {

            }

            override fun onActivityDestroyed(activity: Activity) {

            }
        })
    }

    // The process stays alive in the background for the notification listener,
    // so give back what is cheap to rebuild to stay cached instead of being killed
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            SQLiteDatabase.releaseMemory()
        }
    }
}
