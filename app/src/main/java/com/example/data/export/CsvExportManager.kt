package com.example.data.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.data.local.ExpenseEntity
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CsvExportManager(private val context: Context) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    fun generateCsvContent(expenses: List<ExpenseEntity>): String {
        val sb = StringBuilder()
        // CSV Header
        sb.append("ID,Date,Merchant,Amount,Category,Tax Deductible,Tax Category,Tags,Notes,Has Encrypted Receipt\n")

        for (expense in expenses) {
            val dateStr = dateFormat.format(Date(expense.dateMillis))
            val hasReceipt = if (!expense.encryptedReceiptFileName.isNullOrEmpty()) "Yes" else "No"

            sb.append(expense.id).append(",")
            sb.append(escapeCsv(dateStr)).append(",")
            sb.append(escapeCsv(expense.merchant)).append(",")
            sb.append(String.format(Locale.US, "%.2f", expense.amount)).append(",")
            sb.append(escapeCsv(expense.category)).append(",")
            sb.append(if (expense.isTaxDeductible) "Yes" else "No").append(",")
            sb.append(escapeCsv(expense.taxCategory ?: "")).append(",")
            sb.append(escapeCsv(expense.tags)).append(",")
            sb.append(escapeCsv(expense.notes)).append(",")
            sb.append(hasReceipt).append("\n")
        }

        return sb.toString()
    }

    fun writeCsvToOutputStream(expenses: List<ExpenseEntity>, outputStream: OutputStream) {
        val content = generateCsvContent(expenses)
        outputStream.use { it.write(content.toByteArray(Charsets.UTF_8)) }
    }

    fun createShareIntent(expenses: List<ExpenseEntity>): Intent {
        val content = generateCsvContent(expenses)
        val timestamp = fileDateFormat.format(Date())
        val fileName = "expenses_export_$timestamp.csv"

        val exportDir = File(context.cacheDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()

        val file = File(exportDir, fileName)
        FileOutputStream(file).use { it.write(content.toByteArray(Charsets.UTF_8)) }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Expense & Tax Ledger Export ($timestamp)")
            putExtra(Intent.EXTRA_TEXT, "Here is your exported expense ledger and tax deduction backup.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return Intent.createChooser(intent, "Export Expenses CSV")
    }

    private fun escapeCsv(value: String): String {
        var escaped = value.replace("\"", "\"\"")
        if (escaped.contains(",") || escaped.contains("\n") || escaped.contains("\"")) {
            escaped = "\"$escaped\""
        }
        return escaped
    }
}
