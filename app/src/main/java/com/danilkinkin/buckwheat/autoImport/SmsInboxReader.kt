package com.danilkinkin.buckwheat.autoImport

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class SmsInboxReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_SMS,
    ) == PackageManager.PERMISSION_GRANTED

    fun readSince(since: Long): List<SpendCandidate> {
        if (!hasPermission()) return emptyList()

        val candidates = mutableListOf<SpendCandidate>()

        try {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                ),
                "${Telephony.Sms.DATE} >= ?",
                arrayOf(since.toString()),
                "${Telephony.Sms.DATE} ASC",
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addressColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)

                while (cursor.moveToNext()) {
                    val body = cursor.getString(bodyColumn) ?: continue

                    candidates += SpendCandidate(
                        fingerprint = "sms:${cursor.getLong(idColumn)}",
                        channel = SpendChannel.SMS,
                        sourcePackage = null,
                        title = cursor.getString(addressColumn),
                        text = body,
                        timestamp = cursor.getLong(dateColumn),
                    )
                }
            }
        } catch (e: SecurityException) {
            Log.w("SmsInboxReader", "No access to SMS inbox", e)
        }

        return candidates
    }
}
