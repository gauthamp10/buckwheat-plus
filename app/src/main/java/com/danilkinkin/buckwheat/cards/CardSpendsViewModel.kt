package com.danilkinkin.buckwheat.cards

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.asLiveData
import com.danilkinkin.buckwheat.R
import com.danilkinkin.buckwheat.autoImport.SpendChannel
import com.danilkinkin.buckwheat.data.dao.DetectedSpendDao
import com.danilkinkin.buckwheat.data.dao.TransactionDao
import com.danilkinkin.buckwheat.data.entities.DetectedSpend
import com.danilkinkin.buckwheat.data.entities.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

@HiltViewModel
class CardSpendsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    transactionDao: TransactionDao,
    detectedSpendDao: DetectedSpendDao,
) : ViewModel() {
    companion object {
        // Readable names even when the app is not visible to the package manager
        private val KNOWN_APPS = mapOf(
            "com.emiratesnbd.android" to "Emirates NBD",
            "com.vipera.ts.starter.MashreqAE" to "Mashreq",
            "com.google.android.apps.walletnfcrel" to "Google Wallet",
            "com.google.android.apps.nbu.paisa.user" to "Google Pay",
            "com.google.android.gms" to "Google Wallet",
            "com.samsung.android.spay" to "Samsung Wallet",
        )
    }

    private val appNames = mutableMapOf<String, String>()

    val groups = combine(
        transactionDao.getAll(TransactionType.SPENT).asFlow(),
        detectedSpendDao.observeMatched(),
    ) { spends, detected ->
        CardSpendsGrouper.group(spends, detected, ::sourceName)
    }
        .flowOn(Dispatchers.Default)
        .asLiveData()

    private fun sourceName(detected: DetectedSpend): String {
        if (detected.channel == SpendChannel.SMS) return context.getString(R.string.card_spends_source_sms)

        val packageName = detected.sourcePackage ?: return context.getString(R.string.card_spends_source_app)

        return appNames.getOrPut(packageName) {
            KNOWN_APPS[packageName] ?: try {
                val info = context.packageManager.getApplicationInfo(packageName, 0)
                context.packageManager.getApplicationLabel(info).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                packageName.substringAfterLast('.')
            }
        }
    }
}
