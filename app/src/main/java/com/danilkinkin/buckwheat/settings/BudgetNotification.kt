package com.danilkinkin.buckwheat.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.danilkinkin.buckwheat.R
import com.danilkinkin.buckwheat.base.ButtonRow
import com.danilkinkin.buckwheat.budgetNotification.BudgetNotificationViewModel

@Composable
fun BudgetNotification(
    activityResultRegistryOwner: ActivityResultRegistryOwner?,
    budgetNotificationViewModel: BudgetNotificationViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val isEnabled by budgetNotificationViewModel.isEnabled.observeAsState(false)

    fun openNotificationSettings() {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        )
    }

    var requestPermission: () -> Unit = { openNotificationSettings() }

    if (activityResultRegistryOwner !== null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        CompositionLocalProvider(
            LocalActivityResultRegistryOwner provides activityResultRegistryOwner
        ) {
            val launcher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted) {
                    budgetNotificationViewModel.setEnabled(true)
                } else {
                    openNotificationSettings()
                }
            }

            requestPermission = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
    }

    ButtonRow(
        icon = painterResource(R.drawable.ic_money),
        text = stringResource(R.string.budget_notification_label),
        endCaption = if (isEnabled) {
            stringResource(R.string.auto_import_on)
        } else {
            stringResource(R.string.auto_import_off)
        },
        onClick = {
            when {
                isEnabled -> budgetNotificationViewModel.setEnabled(false)
                budgetNotificationViewModel.hasPermission() -> budgetNotificationViewModel.setEnabled(true)
                else -> requestPermission()
            }
        },
    )
}
