package com.example.data.repository

import android.graphics.Bitmap
import com.example.data.local.ExpenseDao
import com.example.data.local.ExpenseEntity
import com.example.data.security.SecurityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ExpenseRepository(
    private val expenseDao: ExpenseDao,
    private val securityManager: SecurityManager
) {

    val allExpenses: Flow<List<ExpenseEntity>> = expenseDao.getAllExpenses()
    val taxDeductibleExpenses: Flow<List<ExpenseEntity>> = expenseDao.getTaxDeductibleExpenses()
    val totalSpent: Flow<Double?> = expenseDao.getTotalSpent()
    val taxDeductibleTotal: Flow<Double?> = expenseDao.getTaxDeductibleTotal()

    fun getExpensesByDateRange(startTime: Long, endTime: Long): Flow<List<ExpenseEntity>> {
        return expenseDao.getExpensesByDateRange(startTime, endTime)
    }

    fun getExpenseById(id: Long): Flow<ExpenseEntity?> {
        return expenseDao.getExpenseById(id)
    }

    suspend fun saveExpense(
        amount: Double,
        merchant: String,
        category: String,
        dateMillis: Long,
        isTaxDeductible: Boolean,
        taxCategory: String?,
        tags: String,
        notes: String,
        receiptBitmap: Bitmap? = null,
        existingId: Long = 0,
        existingReceiptFile: String? = null
    ): Long = withContext(Dispatchers.IO) {
        var encryptedFileName = existingReceiptFile

        if (receiptBitmap != null) {
            // Delete old file if replacing
            if (!existingReceiptFile.isNullOrEmpty()) {
                securityManager.deleteReceipt(existingReceiptFile)
            }
            // Encrypt and save new receipt
            encryptedFileName = securityManager.encryptAndSaveBitmap(receiptBitmap)
        }

        val entity = ExpenseEntity(
            id = existingId,
            amount = amount,
            merchant = merchant,
            category = category,
            dateMillis = dateMillis,
            isTaxDeductible = isTaxDeductible,
            taxCategory = if (isTaxDeductible) taxCategory else null,
            tags = tags,
            notes = notes,
            encryptedReceiptFileName = encryptedFileName
        )

        if (existingId > 0) {
            expenseDao.updateExpense(entity)
            existingId
        } else {
            expenseDao.insertExpense(entity)
        }
    }

    suspend fun deleteExpense(expense: ExpenseEntity) = withContext(Dispatchers.IO) {
        if (!expense.encryptedReceiptFileName.isNullOrEmpty()) {
            securityManager.deleteReceipt(expense.encryptedReceiptFileName)
        }
        expenseDao.deleteExpense(expense)
    }

    suspend fun deleteAllExpensesAndReceipts() = withContext(Dispatchers.IO) {
        securityManager.deleteAllReceipts()
        expenseDao.deleteAllExpenses()
    }

    suspend fun loadReceiptBitmap(fileName: String): Bitmap? = withContext(Dispatchers.IO) {
        securityManager.decryptReceiptAsBitmap(fileName)
    }
}
