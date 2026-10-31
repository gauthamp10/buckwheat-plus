package com.danilkinkin.buckwheat.autoImport

import java.math.BigDecimal
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs

enum class SpendChannel {
    SMS,
    WALLET,
    APP,
}

data class SpendFootprint(
    val amount: BigDecimal,
    val timestamp: Long,
    val channel: SpendChannel,
    val sourcePackage: String?,
    val normalizedText: String,
    val cardLast4: String?,
    val referenceId: String?,
)

object SpendDuplicateDetector {
    // Bank SMS can lag the Wallet tap notification by several minutes
    const val CROSS_SOURCE_WINDOW_MS = 15 * 60 * 1000L

    // Notifications get re-posted and the SMS inbox is re-read on every sync
    const val SAME_MESSAGE_WINDOW_MS = 24 * 60 * 60 * 1000L

    private const val MIN_PARTIAL_MESSAGE_LENGTH = 24

    fun normalizeText(text: String): String = text
        .lowercase(Locale.ROOT)
        .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
        .trim()

    fun hash(value: String): String = MessageDigest
        .getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
        .take(32)

    fun isSameMessage(a: String, b: String): Boolean {
        if (a == b) return true

        val (shorter, longer) = if (a.length <= b.length) a to b else b to a

        // Notifications often truncate the SMS body or prefix it with the sender
        return shorter.length >= MIN_PARTIAL_MESSAGE_LENGTH && longer.contains(shorter)
    }

    fun isSameSpend(a: SpendFootprint, b: SpendFootprint): Boolean {
        if (a.amount.compareTo(b.amount) != 0) return false

        val timeDelta = abs(a.timestamp - b.timestamp)

        if (a.referenceId != null && b.referenceId != null) {
            return a.referenceId == b.referenceId && timeDelta <= SAME_MESSAGE_WINDOW_MS
        }

        if (isSameMessage(a.normalizedText, b.normalizedText)) {
            return timeDelta <= SAME_MESSAGE_WINDOW_MS
        }

        if (timeDelta > CROSS_SOURCE_WINDOW_MS) return false

        // One source never reports a single spend twice with different wording,
        // so two different texts from the same source are two separate spends
        if (a.channel == b.channel && a.sourcePackage == b.sourcePackage) return false

        // Wallet may show the last digits of a virtual card number instead of the plastic one
        val cardsComparable = a.channel != SpendChannel.WALLET && b.channel != SpendChannel.WALLET
        if (cardsComparable && a.cardLast4 != null && b.cardLast4 != null && a.cardLast4 != b.cardLast4) {
            return false
        }

        return true
    }

    fun isLikelyManualEntry(spend: SpendFootprint, manualAmount: BigDecimal, manualTimestamp: Long): Boolean {
        return spend.amount.compareTo(manualAmount) == 0
                && abs(spend.timestamp - manualTimestamp) <= CROSS_SOURCE_WINDOW_MS
    }
}
