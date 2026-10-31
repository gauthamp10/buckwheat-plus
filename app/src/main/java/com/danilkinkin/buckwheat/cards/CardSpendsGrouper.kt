package com.danilkinkin.buckwheat.cards

import com.danilkinkin.buckwheat.autoImport.SpendChannel
import com.danilkinkin.buckwheat.data.entities.DetectedSpend
import com.danilkinkin.buckwheat.data.entities.DetectedSpendStatus
import com.danilkinkin.buckwheat.data.entities.Transaction
import java.math.BigDecimal
import kotlin.math.abs

data class CardGroup(
    // null for spends without any card info, including the ones entered by hand
    val cardLast4: String?,
    val sources: List<String>,
    val total: BigDecimal,
    val spends: List<Transaction>,
)

object CardSpendsGrouper {
    // Same window the duplicate detector uses to match one spend reported by several apps
    private const val SAME_SPEND_WINDOW_MS = 15 * 60 * 1000L

    fun group(
        spends: List<Transaction>,
        detected: List<DetectedSpend>,
        sourceName: (DetectedSpend) -> String,
    ): List<CardGroup> {
        val imported = detected
            .filter { it.status == DetectedSpendStatus.IMPORTED && it.transactionUid != null }
            .associateBy { it.transactionUid }
        val duplicates = detected.filter { it.status == DetectedSpendStatus.DUPLICATE }

        val bySpend = spends.map { spend ->
            // The alert that was skipped as a duplicate often is the one that names the card
            val records = listOfNotNull(imported[spend.uid]) + duplicates.filter {
                it.value.compareTo(spend.value) == 0
                        && abs(it.date.time - spend.date.time) <= SAME_SPEND_WINDOW_MS
            }

            // Wallet can show a virtual card number, the bank alert has the real one
            val card = records
                .sortedBy { it.channel == SpendChannel.WALLET }
                .firstNotNullOfOrNull { it.cardLast4 }

            Triple(spend, card, records.map(sourceName))
        }

        return bySpend
            .groupBy { (_, card) -> card }
            .map { (card, items) ->
                CardGroup(
                    cardLast4 = card,
                    sources = items.flatMap { it.third }.distinct(),
                    total = items.fold(BigDecimal.ZERO) { sum, item -> sum + item.first.value },
                    spends = items.map { it.first }.sortedByDescending { it.date },
                )
            }
            .sortedWith(compareBy<CardGroup> { it.cardLast4 == null }.thenByDescending { it.total })
    }
}
