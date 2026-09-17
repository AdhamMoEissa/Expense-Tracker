package com.example.ui

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.export.CsvExportManager
import com.example.data.local.AppDatabase
import com.example.data.local.ExpenseEntity
import com.example.data.ocr.ReceiptOcrScanner
import com.example.data.ocr.ScannedReceiptData
import com.example.data.preferences.AppSettings
import com.example.data.preferences.AppSettingsRepository
import com.example.data.repository.ExpenseRepository
import com.example.data.security.SecurityManager
import com.example.ui.components.CategoryShare
import com.example.ui.components.DailyCategorySpend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class TimeRangeFilter(val label: String, val days: Int) {
    PAST_7_DAYS("7 Days", 7),
    PAST_14_DAYS("14 Days", 14),
    THIS_MONTH("This Month", 30),
    ALL_TIME("All", 365)
}

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {

    private val securityManager = SecurityManager(application)
    private val database = AppDatabase.getDatabase(application)
    private val repository = ExpenseRepository(database.expenseDao(), securityManager)
    private val settingsRepository = AppSettingsRepository(application)
    private val ocrScanner = ReceiptOcrScanner(application)
    private val csvExportManager = CsvExportManager(application)

    val settings: StateFlow<AppSettings> = settingsRepository.settingsFlow

    val allExpenses: StateFlow<List<ExpenseEntity>> = repository.allExpenses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filters
    val selectedTimeRange = MutableStateFlow(TimeRangeFilter.PAST_7_DAYS)
    val searchQuery = MutableStateFlow("")
    val selectedCategoryFilter = MutableStateFlow<String?>(null)
    val taxOnlyFilter = MutableStateFlow(false)

    // Filtered expenses for the list / ledger view
    val filteredExpenses: StateFlow<List<ExpenseEntity>> = combine(
        allExpenses,
        searchQuery,
        selectedCategoryFilter,
        taxOnlyFilter
    ) { expenses, query, category, taxOnly ->
        expenses.filter { item ->
            val matchesQuery = if (query.isBlank()) true else {
                item.merchant.contains(query, ignoreCase = true) ||
                item.notes.contains(query, ignoreCase = true) ||
                item.tags.contains(query, ignoreCase = true) ||
                item.category.contains(query, ignoreCase = true)
            }
            val matchesCategory = category == null || item.category.equals(category, ignoreCase = true)
            val matchesTax = !taxOnly || item.isTaxDeductible
            matchesQuery && matchesCategory && matchesTax
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Daily stacked category spending for the StackedBarChart
    val dailyCategorySpending: StateFlow<List<DailyCategorySpend>> = combine(
        allExpenses,
        selectedTimeRange
    ) { expenses, timeRange ->
        computeDailyCategorySpending(expenses, timeRange)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Category breakdown shares
    val categoryShares: StateFlow<List<CategoryShare>> = combine(
        allExpenses,
        selectedTimeRange
    ) { expenses, timeRange ->
        computeCategoryShares(expenses, timeRange)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Current month totals
    val currentMonthTotal: StateFlow<Double> = allExpenses.combine(selectedTimeRange) { expenses, _ ->
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfMonth = cal.timeInMillis

        expenses.filter { it.dateMillis >= startOfMonth }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val currentMonthTaxDeductible: StateFlow<Double> = allExpenses.combine(selectedTimeRange) { expenses, _ ->
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfMonth = cal.timeInMillis

        expenses.filter { it.dateMillis >= startOfMonth && it.isTaxDeductible }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // OCR Scanning State
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scannedData = MutableStateFlow<ScannedReceiptData?>(null)
    val scannedData: StateFlow<ScannedReceiptData?> = _scannedData.asStateFlow()

    // Active Receipt Viewer Decryption
    private val _viewingReceiptBitmap = MutableStateFlow<Bitmap?>(null)
    val viewingReceiptBitmap: StateFlow<Bitmap?> = _viewingReceiptBitmap.asStateFlow()

    private val _isLoadingReceipt = MutableStateFlow(false)
    val isLoadingReceipt: StateFlow<Boolean> = _isLoadingReceipt.asStateFlow()

    init {
        seedSampleDataIfEmpty()
    }

    private fun seedSampleDataIfEmpty() {
        viewModelScope.launch(Dispatchers.IO) {
            val count = database.expenseDao().getExpenseByIdOnce(1L)
            if (count == null) {
                // Seed standard realistic adult expenses for immediate visual insights
                val now = System.currentTimeMillis()
                val day = 86_400_000L
                val sampleExpenses = listOf(
                    ExpenseEntity(
                        amount = 142.50,
                        merchant = "Whole Foods Market",
                        category = "Groceries",
                        dateMillis = now - (0 * day),
                        isTaxDeductible = false,
                        tags = "groceries, organic, home",
                        notes = "Weekly grocery run"
                    ),
                    ExpenseEntity(
                        amount = 45.20,
                        merchant = "Chevron Fuel",
                        category = "Transportation",
                        dateMillis = now - (0 * day),
                        isTaxDeductible = true,
                        taxCategory = "Vehicle & Mileage",
                        tags = "gas, travel, work",
                        notes = "Fuel for client meetings"
                    ),
                    ExpenseEntity(
                        amount = 18.75,
                        merchant = "Blue Bottle Coffee",
                        category = "Dining",
                        dateMillis = now - (1 * day),
                        isTaxDeductible = true,
                        taxCategory = "Business Expense",
                        tags = "client meeting, coffee",
                        notes = "Quarterly check-in with design client"
                    ),
                    ExpenseEntity(
                        amount = 89.99,
                        merchant = "Staples Office Supply",
                        category = "Office & Work",
                        dateMillis = now - (1 * day),
                        isTaxDeductible = true,
                        taxCategory = "Home Office",
                        tags = "printer ink, paper, work",
                        notes = "Tax season printing and office supplies"
                    ),
                    ExpenseEntity(
                        amount = 135.00,
                        merchant = "City Electric & Gas",
                        category = "Utilities",
                        dateMillis = now - (2 * day),
                        isTaxDeductible = true,
                        taxCategory = "Home Office",
                        tags = "utilities, electric, bills",
                        notes = "Monthly electricity invoice"
                    ),
                    ExpenseEntity(
                        amount = 64.30,
                        merchant = "Trader Joe's",
                        category = "Groceries",
                        dateMillis = now - (3 * day),
                        isTaxDeductible = false,
                        tags = "groceries, pantry",
                        notes = "Produce and pantry staples"
                    ),
                    ExpenseEntity(
                        amount = 55.00,
                        merchant = "CVS Pharmacy",
                        category = "Healthcare",
                        dateMillis = now - (4 * day),
                        isTaxDeductible = true,
                        taxCategory = "Medical & Healthcare",
                        tags = "prescriptions, medical",
                        notes = "Prescription medication refilled"
                    ),
                    ExpenseEntity(
                        amount = 24.99,
                        merchant = "Adobe Creative Cloud",
                        category = "Office & Work",
                        dateMillis = now - (5 * day),
                        isTaxDeductible = true,
                        taxCategory = "Business Expense",
                        tags = "software, work, subscription",
                        notes = "Monthly design software license"
                    ),
                    ExpenseEntity(
                        amount = 72.40,
                        merchant = "Olive Garden Bistro",
                        category = "Dining",
                        dateMillis = now - (6 * day),
                        isTaxDeductible = false,
                        tags = "family dinner, dining",
                        notes = "Weekend family dinner"
                    )
                )
                sampleExpenses.forEach { database.expenseDao().insertExpense(it) }
            }
        }
    }

    private fun computeDailyCategorySpending(
        expenses: List<ExpenseEntity>,
        timeRange: TimeRangeFilter
    ): List<DailyCategorySpend> {
        val numDays = timeRange.days
        val cal = Calendar.getInstance()
        val dayFormat = SimpleDateFormat("E", Locale.US)
        val shortDateFormat = SimpleDateFormat("M/d", Locale.US)
        val fullDateFormat = SimpleDateFormat("EEE, MMM d, yyyy", Locale.US)

        val daysList = mutableListOf<DailyCategorySpend>()

        for (i in (numDays - 1) downTo 0) {
            val targetCal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -i)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startOfDay = targetCal.timeInMillis
            targetCal.set(Calendar.HOUR_OF_DAY, 23)
            targetCal.set(Calendar.MINUTE, 59)
            targetCal.set(Calendar.SECOND, 59)
            targetCal.set(Calendar.MILLISECOND, 999)
            val endOfDay = targetCal.timeInMillis

            val dayExpenses = expenses.filter { it.dateMillis in startOfDay..endOfDay }
            val total = dayExpenses.sumOf { it.amount }

            val categoryMap = mutableMapOf<String, Double>()
            dayExpenses.forEach { exp ->
                val current = categoryMap.getOrDefault(exp.category, 0.0)
                categoryMap[exp.category] = current + exp.amount
            }

            val dateObj = Date(startOfDay)
            val label = if (numDays <= 7) dayFormat.format(dateObj) else shortDateFormat.format(dateObj)

            daysList.add(
                DailyCategorySpend(
                    dateMillis = startOfDay,
                    dayLabel = label,
                    fullDateLabel = fullDateFormat.format(dateObj),
                    totalAmount = total,
                    categoryAmounts = categoryMap
                )
            )
        }
        return daysList
    }

    private fun computeCategoryShares(
        expenses: List<ExpenseEntity>,
        timeRange: TimeRangeFilter
    ): List<CategoryShare> {
        val minTime = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -timeRange.days)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val validExpenses = expenses.filter { it.dateMillis >= minTime }
        val total = validExpenses.sumOf { it.amount }
        if (total <= 0.0) return emptyList()

        return validExpenses
            .groupBy { it.category }
            .map { (category, list) ->
                val sum = list.sumOf { it.amount }
                CategoryShare(
                    categoryName = category,
                    totalAmount = sum,
                    percentage = (sum / total).toFloat(),
                    count = list.size
                )
            }
            .sortedByDescending { it.totalAmount }
    }

    // OCR Scanning actions
    fun scanReceipt(bitmap: Bitmap) {
        viewModelScope.launch {
            _isScanning.value = true
            try {
                val data = ocrScanner.scanBitmap(bitmap)
                _scannedData.value = data
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun scanReceipt(uri: Uri) {
        viewModelScope.launch {
            _isScanning.value = true
            try {
                val data = ocrScanner.scanUri(uri)
                _scannedData.value = data
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun clearScannedData() {
        _scannedData.value = null
    }

    // Save or update transaction
    fun saveExpense(
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
        existingReceiptFile: String? = null,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.saveExpense(
                amount = amount,
                merchant = merchant,
                category = category,
                dateMillis = dateMillis,
                isTaxDeductible = isTaxDeductible,
                taxCategory = taxCategory,
                tags = tags,
                notes = notes,
                receiptBitmap = receiptBitmap,
                existingId = existingId,
                existingReceiptFile = existingReceiptFile
            )
            onComplete()
        }
    }

    fun deleteExpense(expense: ExpenseEntity, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
            onComplete()
        }
    }

    // Load and decrypt receipt image for transaction viewer
    fun loadReceipt(fileName: String) {
        viewModelScope.launch {
            _isLoadingReceipt.value = true
            _viewingReceiptBitmap.value = null
            try {
                val bmp = repository.loadReceiptBitmap(fileName)
                _viewingReceiptBitmap.value = bmp
            } finally {
                _isLoadingReceipt.value = false
            }
        }
    }

    fun clearViewingReceipt() {
        _viewingReceiptBitmap.value = null
    }

    // Settings actions
    fun setThemeMode(mode: String) = settingsRepository.setThemeMode(mode)
    fun setTaxTrackerEnabled(enabled: Boolean) = settingsRepository.setTaxTrackerEnabled(enabled)
    fun setMonthlyBudget(budget: Double) = settingsRepository.setMonthlyBudget(budget)

    // Delete all user data (Red button action with confirmation)
    fun deleteAllUserData(onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.deleteAllExpensesAndReceipts()
            settingsRepository.clearAllSettings()
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    // CSV Export
    fun exportCsvToStream(outputStream: OutputStream) {
        val currentList = allExpenses.value
        csvExportManager.writeCsvToOutputStream(currentList, outputStream)
    }

    fun getShareCsvIntent(): Intent {
        return csvExportManager.createShareIntent(allExpenses.value)
    }
}
