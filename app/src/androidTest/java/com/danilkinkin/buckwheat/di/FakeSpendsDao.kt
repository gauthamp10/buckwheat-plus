package com.danilkinkin.buckwheat.di

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.danilkinkin.buckwheat.data.dao.TransactionDao
import com.danilkinkin.buckwheat.data.entities.Transaction
import com.danilkinkin.buckwheat.data.entities.TransactionType
import java.util.Date

class FakeTransactionDao: TransactionDao {
    private val spends = mutableListOf<Transaction>()

    override fun getAll(): LiveData<List<Transaction>> {
        return MutableLiveData(spends)
    }

    override fun getAll(type: TransactionType): LiveData<List<Transaction>> {
        return MutableLiveData(spends)
    }

    override fun getById(uid: Int): Transaction? {
        return null
    }

    override fun getBetween(type: TransactionType, from: Date, to: Date): List<Transaction> {
        return spends.filter { it.type == type && it.date in from..to }
    }

    override fun insert(vararg transaction: Transaction) {
        spends.addAll(transaction)
    }

    override fun insertAndGetId(transaction: Transaction): Long {
        spends.add(transaction)
        return spends.size.toLong()
    }

    override fun update(vararg transaction: Transaction) {

    }

    override fun deleteById(uid: Int) {
        spends.removeIf { it.uid == uid }
    }

    override fun deleteAll() {

    }

}