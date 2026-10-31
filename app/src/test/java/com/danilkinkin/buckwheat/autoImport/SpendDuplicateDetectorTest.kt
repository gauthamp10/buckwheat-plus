package com.danilkinkin.buckwheat.autoImport

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class SpendDuplicateDetectorTest {
    private val minute = 60 * 1000L
    private val start = 1_793_000_000_000L

    private fun footprint(
        amount: String = "45.50",
        timestamp: Long = start,
        channel: SpendChannel = SpendChannel.SMS,
        sourcePackage: String? = null,
        text: String = "Purchase of AED 45.50 with Card ending 1234 at CARREFOUR",
        cardLast4: String? = "1234",
        referenceId: String? = null,
    ) = SpendFootprint(
        amount = BigDecimal(amount).setScale(2),
        timestamp = timestamp,
        channel = channel,
        sourcePackage = sourcePackage,
        normalizedText = SpendDuplicateDetector.normalizeText(text),
        cardLast4 = cardLast4,
        referenceId = referenceId,
    )

    @Test
    fun sameSmsFromInboxAndNotificationIsDuplicate() {
        val inbox = footprint()
        val notification = footprint(
            timestamp = start + 2_000,
            sourcePackage = "com.google.android.apps.messaging",
        )

        assertTrue(SpendDuplicateDetector.isSameSpend(inbox, notification))
    }

    @Test
    fun walletAndBankSmsForSameSpendAreDuplicates() {
        val wallet = footprint(
            channel = SpendChannel.WALLET,
            sourcePackage = "com.google.android.apps.walletnfcrel",
            text = "Carrefour AED 45.50 with Mastercard •••• 9999",
            cardLast4 = "9999",
        )
        val sms = footprint(timestamp = start + 4 * minute)

        assertTrue(SpendDuplicateDetector.isSameSpend(wallet, sms))
    }

    @Test
    fun bankAppAndSmsWithinWindowAreDuplicates() {
        val app = footprint(
            channel = SpendChannel.APP,
            sourcePackage = "com.emiratesnbd.android",
            text = "You spent AED 45.50 at Carrefour using card 1234",
        )
        val sms = footprint(timestamp = start + 10 * minute)

        assertTrue(SpendDuplicateDetector.isSameSpend(app, sms))
    }

    @Test
    fun sameAmountOutsideWindowIsNotDuplicate() {
        val first = footprint(channel = SpendChannel.APP, sourcePackage = "bank.app", text = "first")
        val second = footprint(timestamp = start + 40 * minute)

        assertFalse(SpendDuplicateDetector.isSameSpend(first, second))
    }

    @Test
    fun twoDifferentSmsFromSameSourceAreTwoSpends() {
        val first = footprint(text = "Purchase of AED 15.00 card 1234 at CAFE on 31/10 09:10", amount = "15.00")
        val second = footprint(
            text = "Purchase of AED 15.00 card 1234 at CAFE on 31/10 09:14",
            amount = "15.00",
            timestamp = start + 4 * minute,
        )

        assertFalse(SpendDuplicateDetector.isSameSpend(first, second))
    }

    @Test
    fun differentCardsAreNotDuplicates() {
        val app = footprint(channel = SpendChannel.APP, sourcePackage = "bank.app", text = "x", cardLast4 = "1111")
        val sms = footprint(cardLast4 = "2222")

        assertFalse(SpendDuplicateDetector.isSameSpend(app, sms))
    }

    @Test
    fun differentAmountsAreNotDuplicates() {
        val app = footprint(channel = SpendChannel.APP, sourcePackage = "bank.app", text = "x")
        val sms = footprint(amount = "45.51")

        assertFalse(SpendDuplicateDetector.isSameSpend(app, sms))
    }

    @Test
    fun referenceIdDecides() {
        assertTrue(
            SpendDuplicateDetector.isSameSpend(
                footprint(referenceId = "430512345678", text = "a"),
                footprint(referenceId = "430512345678", text = "b", timestamp = start + 60 * minute),
            )
        )
        assertFalse(
            SpendDuplicateDetector.isSameSpend(
                footprint(referenceId = "430512345678", channel = SpendChannel.APP, text = "a"),
                footprint(referenceId = "430512349999", text = "b"),
            )
        )
    }

    @Test
    fun truncatedNotificationTextIsSameMessage() {
        val full = SpendDuplicateDetector.normalizeText(
            "Purchase of AED 45.50 with Card ending 1234 at CARREFOUR DUBAI on 31/10/2026. Avl limit AED 10,000"
        )
        val truncated = SpendDuplicateDetector.normalizeText("Purchase of AED 45.50 with Card ending 1234 at…")

        assertTrue(SpendDuplicateDetector.isSameMessage(full, truncated))
    }

    @Test
    fun manualEntryNearbyIsDuplicate() {
        val spend = footprint()

        assertTrue(SpendDuplicateDetector.isLikelyManualEntry(spend, BigDecimal("45.5"), start + 5 * minute))
        assertFalse(SpendDuplicateDetector.isLikelyManualEntry(spend, BigDecimal("45.5"), start + 30 * minute))
    }
}
