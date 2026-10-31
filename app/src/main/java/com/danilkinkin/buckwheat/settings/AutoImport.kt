package com.danilkinkin.buckwheat.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.danilkinkin.buckwheat.LocalWindowInsets
import com.danilkinkin.buckwheat.R
import com.danilkinkin.buckwheat.autoImport.AutoImportViewModel
import com.danilkinkin.buckwheat.autoImport.SpendNotificationListenerService
import com.danilkinkin.buckwheat.base.ButtonRow
import com.danilkinkin.buckwheat.base.CheckedRow
import com.danilkinkin.buckwheat.base.LocalBottomSheetScrollState
import com.danilkinkin.buckwheat.data.AppViewModel
import com.danilkinkin.buckwheat.data.PathState

const val SETTINGS_AUTO_IMPORT_SHEET = "settings.autoImport"

@Composable
fun AutoImport(
    appViewModel: AppViewModel = hiltViewModel(),
    autoImportViewModel: AutoImportViewModel = hiltViewModel(),
) {
    val isEnabled by autoImportViewModel.isEnabled.observeAsState(false)

    ButtonRow(
        icon = painterResource(R.drawable.ic_balance_wallet),
        text = stringResource(R.string.auto_import_label),
        onClick = {
            appViewModel.openSheet(PathState(SETTINGS_AUTO_IMPORT_SHEET))
        },
        endCaption = if (isEnabled) {
            stringResource(R.string.auto_import_on)
        } else {
            stringResource(R.string.auto_import_off)
        },
    )
}

@Composable
fun AutoImportDialog(
    activityResultRegistryOwner: ActivityResultRegistryOwner?,
    autoImportViewModel: AutoImportViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val localBottomSheetScrollState = LocalBottomSheetScrollState.current
    val navigationBarHeight = androidx.compose.ui.unit.max(
        LocalWindowInsets.current.calculateBottomPadding(),
        16.dp,
    )

    val isEnabled by autoImportViewModel.isEnabled.observeAsState(false)
    val hasNotificationAccess by autoImportViewModel.hasNotificationAccess.observeAsState(false)
    val hasSmsAccess by autoImportViewModel.hasSmsAccess.observeAsState(false)
    val hasBatteryExemption by autoImportViewModel.hasBatteryExemption.observeAsState(false)
    val isSyncing by autoImportViewModel.isSyncing.observeAsState(false)
    val lastSyncSummary by autoImportViewModel.lastSyncSummary.observeAsState()

    LifecycleResumeEffect(Unit) {
        autoImportViewModel.refreshAccess()

        onPauseOrDispose { }
    }

    val requestSmsPermission: () -> Unit = if (activityResultRegistryOwner !== null) {
        var launch: () -> Unit = {}

        CompositionLocalProvider(
            LocalActivityResultRegistryOwner provides activityResultRegistryOwner
        ) {
            val launcher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                autoImportViewModel.refreshAccess()

                if (granted) autoImportViewModel.syncNow()
            }

            launch = { launcher.launch(Manifest.permission.READ_SMS) }
        }

        launch
    } else {
        {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                )
            )
        }
    }

    fun openNotificationAccessSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                SpendNotificationListenerService.componentName(context).flattenToString(),
            )
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    // OEM autostart and "sleeping apps" switches live on the app details page
    fun openAppDetailsSettings() {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            )
        )
    }

    fun requestBatteryExemption() {
        try {
            context.startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.fromParts("package", context.packageName, null),
                )
            )
        } catch (e: Exception) {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    Surface(Modifier.padding(top = localBottomSheetScrollState.topPadding)) {
        Column(modifier = Modifier.padding(bottom = navigationBarHeight)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.auto_import_label),
                    style = MaterialTheme.typography.titleLarge,
                )
            }

            CheckedRow(
                checked = isEnabled,
                onValueChange = { autoImportViewModel.setEnabled(it) },
                text = stringResource(R.string.auto_import_enable),
                description = stringResource(R.string.auto_import_enable_description),
            )

            if (isEnabled) {
                ButtonRow(
                    icon = painterResource(R.drawable.ic_info),
                    text = stringResource(R.string.auto_import_notification_access),
                    description = stringResource(R.string.auto_import_notification_access_description),
                    endCaption = if (hasNotificationAccess) {
                        stringResource(R.string.auto_import_access_granted)
                    } else {
                        stringResource(R.string.auto_import_access_grant)
                    },
                    onClick = { openNotificationAccessSettings() },
                )

                if (hasNotificationAccess) {
                    ButtonRow(
                        icon = painterResource(R.drawable.ic_do_not_disturb),
                        text = stringResource(R.string.auto_import_background),
                        description = stringResource(R.string.auto_import_background_description),
                        endCaption = if (hasBatteryExemption) {
                            stringResource(R.string.auto_import_access_granted)
                        } else {
                            stringResource(R.string.auto_import_access_grant)
                        },
                        onClick = {
                            if (hasBatteryExemption) openAppDetailsSettings() else requestBatteryExemption()
                        },
                    )
                }

                ButtonRow(
                    icon = painterResource(R.drawable.ic_comment),
                    text = stringResource(R.string.auto_import_sms_access),
                    description = stringResource(R.string.auto_import_sms_access_description),
                    endCaption = if (hasSmsAccess) {
                        stringResource(R.string.auto_import_access_granted)
                    } else {
                        stringResource(R.string.auto_import_access_grant)
                    },
                    onClick = { if (!hasSmsAccess) requestSmsPermission() },
                )

                ButtonRow(
                    icon = painterResource(R.drawable.ic_autorenew),
                    text = if (isSyncing) {
                        stringResource(R.string.auto_import_syncing)
                    } else {
                        stringResource(R.string.auto_import_sync_now)
                    },
                    description = lastSyncSummary?.let {
                        stringResource(
                            R.string.auto_import_sync_summary,
                            it.imported,
                            it.duplicates,
                            it.pending,
                            it.skipped,
                        )
                    },
                    onClick = { autoImportViewModel.syncNow() },
                )
            }

            Text(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                text = stringResource(R.string.auto_import_privacy),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
                    .copy(color = LocalContentColor.current.copy(alpha = 0.6f)),
            )
        }
    }
}
