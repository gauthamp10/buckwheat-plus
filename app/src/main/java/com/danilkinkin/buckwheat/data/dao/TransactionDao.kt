package com.danilkinkin.buckwheat.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.danilkinkin.buckwheat.data.entities.Transaction
import com.danilkinkin.buckwheat.data.entities.TransactionType
import java.util.Date

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date ASC")
    fun getAll(): LiveData<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE type = :type ORDER BY date ASC")
    fun getAll(type: TransactionType): LiveData<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE uid = :uid")
    fun getById(uid: Int): Transaction?

    @Query("SELECT * FROM transactions WHERE type = :type AND date BETWEEN :from AND :to ORDER BY date ASC")
    fun getBetween(type: TransactionType, from: Date, to: Date): List<Transaction>

    @Insert
    fun insert(vararg transaction: Transaction)

    @Insert
    fun insertAndGetId(transaction: Transaction): Long

    @Update(entity = Transaction::class, onConflict = OnConflictStrategy.REPLACE)
    fun update(vararg transaction: Transaction)

    @Query("DELETE FROM transactions WHERE uid = :uid")
    fun deleteById(uid: Int)

    @Query("DELETE FROM transactions")
    fun deleteAll()
}
