package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.export.CsvExportManager
import com.example.data.local.ExpenseEntity
import com.example.data.security.SecurityManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context matches app name`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Expense Tracker", appName)
    }

    @Test
    fun `csv export manager generates valid formatted rows`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val csvManager = CsvExportManager(context)

        val expenses = listOf(
            ExpenseEntity(
                id = 1L,
                amount = 89.50,
                merchant = "Trader Joe's",
                category = "Groceries",
                dateMillis = 1710000000000L,
                isTaxDeductible = false,
                taxCategory = null,
                tags = "food, pantry",
                notes = "Weekly groceries"
            ),
            ExpenseEntity(
                id = 2L,
                amount = 120.00,
                merchant = "Office Depot",
                category = "Office & Work",
                dateMillis = 1710000000000L,
                isTaxDeductible = true,
                taxCategory = "Business Expense",
                tags = "supplies, paper",
                notes = "Office printer supplies"
            )
        )

        val csv = csvManager.generateCsvContent(expenses)
        assertTrue(csv.contains("ID,Date,Merchant,Amount,Category,Tax Deductible,Tax Category,Tags,Notes,Has Encrypted Receipt"))
        assertTrue(csv.contains("Trader Joe's"))
        assertTrue(csv.contains("89.50"))
        assertTrue(csv.contains("Office Depot"))
        assertTrue(csv.contains("120.00"))
        assertTrue(csv.contains("Business Expense"))
    }
}
