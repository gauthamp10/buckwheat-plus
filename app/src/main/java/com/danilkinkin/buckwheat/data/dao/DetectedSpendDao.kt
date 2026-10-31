package com.danilkinkin.buckwheat.data.dao

import androidx.room.*
import com.danilkinkin.buckwheat.data.entities.DetectedSpend
import com.danilkinkin.buckwheat.data.entities.DetectedSpendStatus
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface DetectedSpendDao {
    @Query("SELECT * FROM detected_spends WHERE fingerprint = :fingerprint LIMIT 1")
    fun getByFingerprint(fingerprint: String): DetectedSpend?

    @Query("SELECT * FROM detected_spends WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    fun getBetween(from: Date, to: Date): List<DetectedSpend>

    @Query("SELECT * FROM detected_spends WHERE status = :status ORDER BY date ASC")
    fun getByStatus(status: DetectedSpendStatus): List<DetectedSpend>

    @Query("SELECT * FROM detected_spends WHERE status IN ('IMPORTED', 'DUPLICATE') ORDER BY date ASC")
    fun observeMatched(): Flow<List<DetectedSpend>>

    @Query("SELECT transaction_uid FROM detected_spends WHERE transaction_uid IS NOT NULL")
    fun getLinkedTransactionUids(): List<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(detectedSpend: DetectedSpend): Long

    @Update
    fun update(detectedSpend: DetectedSpend)
}
