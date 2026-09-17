package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val merchant: String,
    val category: String,
    val dateMillis: Long,
    val isTaxDeductible: Boolean = false,
    val taxCategory: String? = null,
    val tags: String = "",
    val notes: String = "",
    val encryptedReceiptFileName: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
