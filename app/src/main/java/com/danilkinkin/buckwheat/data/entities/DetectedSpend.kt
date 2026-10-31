package com.danilkinkin.buckwheat.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.danilkinkin.buckwheat.autoImport.SpendChannel
import com.danilkinkin.buckwheat.autoImport.SpendFootprint
import java.math.BigDecimal
import java.util.Date

enum class DetectedSpendStatus {
    IMPORTED,
    // Waiting for the app to start a new day before it can be counted
    PENDING,
    DUPLICATE,
    SKIPPED,
}

@Entity(
    tableName = "detected_spends",
    indices = [
        Index(value = ["fingerprint"], unique = true),
        Index(value = ["date"]),
    ],
)
data class DetectedSpend(
    @ColumnInfo(name = "fingerprint")
    val fingerprint: String,

    @ColumnInfo(name = "channel")
    val channel: SpendChannel,

    @ColumnInfo(name = "source_package")
    val sourcePackage: String?,

    @ColumnInfo(name = "value")
    val value: BigDecimal,

    @ColumnInfo(name = "currency")
    val currency: String?,

    @ColumnInfo(name = "merchant")
    val merchant: String?,

    @ColumnInfo(name = "card_last4")
    val cardLast4: String?,

    @ColumnInfo(name = "reference_id")
    val referenceId: String?,

    @ColumnInfo(name = "date")
    val date: Date,

    @ColumnInfo(name = "normalized_text")
    val normalizedText: String,

    @ColumnInfo(name = "status")
    val status: DetectedSpendStatus,

    @ColumnInfo(name = "transaction_uid")
    val transactionUid: Int? = null,
) {
    @PrimaryKey(autoGenerate = true) var uid: Int = 0

    fun toFootprint() = SpendFootprint(
        amount = value,
        timestamp = date.time,
        channel = channel,
        sourcePackage = sourcePackage,
        normalizedText = normalizedText,
        cardLast4 = cardLast4,
        referenceId = referenceId,
    )
}
