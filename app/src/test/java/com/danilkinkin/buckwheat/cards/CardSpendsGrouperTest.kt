package com.danilkinkin.buckwheat.cards

import com.danilkinkin.buckwheat.autoImport.SpendChannel
import com.danilkinkin.buckwheat.data.entities.DetectedSpend
import com.danilkinkin.buckwheat.data.entities.DetectedSpendStatus
import com.danilkinkin.buckwheat.data.entities.Transaction
import com.danilkinkin.buckwheat.data.entities.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.util.Date

class CardSpendsGrouperTest {
    private val now = 1_800_000_000_000L
    private val minute = 60 * 1000L

    private fun spend(uid: Int, value: String, at: Long = now) =
        Transaction(TransactionType.SPENT, BigDecimal(value), Date(at)).also { it.uid = uid }

    private fun detected(
        value: String,
        card: String?,
        status: DetectedSpendStatus,
        transactionUid: Int? = null,
        channel: SpendChannel = SpendChannel.APP,
        pkg: String? = "com.emiratesnbd.android",
        at: Long = now,
    ) = DetectedSpend(
        fingerprint = "$pkg:$value:$at:$status",
        channel = channel,
        sourcePackage = pkg,
        value = BigDecimal(value),
        currency = "AED",
        merchant = null,
        cardLast4 = card,
        referenceId = null,
        date = Date(at),
        normalizedText = "",
        status = status,
        transactionUid = transactionUid,
    )

    private fun group(spends: List<Transaction>, detected: List<DetectedSpend>) =
        CardSpendsGrouper.group(spends, detected) { it.sourcePackage ?: it.channel.name }

    @Test
    fun groupsByCardAndPutsSpendsWithoutCardLast() {
        val groups = group(
            spends = listOf(
                spend(1, "32.40"),
                spend(2, "95.00"),
                spend(3, "156.75"),
                spend(4, "13.00"),
            ),
            detected = listOf(
                detected("32.40", "0956", DetectedSpendStatus.IMPORTED, transactionUid = 1),
                detected("95.00", "2839", DetectedSpendStatus.IMPORTED, transactionUid = 2, pkg = "com.vipera.ts.starter.MashreqAE"),
                detected("156.75", "0956", DetectedSpendStatus.IMPORTED, transactionUid = 3),
            ),
        )

        assertEquals(listOf("0956", "2839", null), groups.map { it.cardLast4 })
        assertEquals(BigDecimal("189.15"), groups[0].total)
        assertEquals(listOf(3, 1), groups[0].spends.map { it.uid }.sortedDescending())
        assertEquals(listOf("com.vipera.ts.starter.MashreqAE"), groups[1].sources)
        assertEquals(emptyList<String>(), groups[2].sources)
    }

    @Test
    fun takesCardFromBankAlertWhenWalletWasFirst() {
        val groups = group(
            spends = listOf(spend(1, "45.50")),
            detected = listOf(
                detected(
                    "45.50", "9999", DetectedSpendStatus.IMPORTED, transactionUid = 1,
                    channel = SpendChannel.WALLET, pkg = "com.google.android.apps.walletnfcrel",
                ),
                detected("45.50", "0956", DetectedSpendStatus.DUPLICATE, at = now + 2 * minute),
            ),
        )

        assertEquals("0956", groups.single().cardLast4)
        assertEquals(
            listOf("com.google.android.apps.walletnfcrel", "com.emiratesnbd.android"),
            groups.single().sources,
        )
    }

    @Test
    fun findsCardOfSpendEnteredByHand() {
        val groups = group(
            spends = listOf(spend(1, "12.00"), spend(2, "12.00", at = now + 3 * 60 * minute)),
            detected = listOf(detected("12.00", "0956", DetectedSpendStatus.DUPLICATE, at = now + minute)),
        )

        assertEquals(listOf("0956", null), groups.map { it.cardLast4 })
        assertEquals(listOf(1), groups[0].spends.map { it.uid })
    }
}
