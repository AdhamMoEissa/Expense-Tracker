package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.ExpenseEntity
import com.example.ui.ExpenseViewModel
import com.example.ui.screens.AddExpenseScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ExpenseListScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TransactionDetailDialog
import com.example.ui.theme.MyApplicationTheme

enum class AppDestination(val title: String, val icon: ImageVector, val tag: String) {
    DASHBOARD("Analytics", Icons.Default.BarChart, "nav_tab_dashboard"),
    EXPENSES("Ledger", Icons.AutoMirrored.Filled.ReceiptLong, "nav_tab_expenses"),
    ADD("Add / Scan", Icons.Default.AddCircle, "nav_tab_add"),
    SETTINGS("Settings", Icons.Default.Settings, "nav_tab_settings")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: ExpenseViewModel = viewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()

            MyApplicationTheme(themePreference = settings.themeMode) {
                MainExpenseApp(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainExpenseApp(
    viewModel: ExpenseViewModel,
    modifier: Modifier = Modifier
) {
    var currentDestination by remember { mutableStateOf(AppDestination.DASHBOARD) }
    var selectedExpenseForDetail by remember { mutableStateOf<ExpenseEntity?>(null) }
    val viewingReceiptBitmap by viewModel.viewingReceiptBitmap.collectAsStateWithLifecycle()
    val isLoadingReceipt by viewModel.isLoadingReceipt.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (currentDestination) {
                            AppDestination.DASHBOARD -> "Expense & Tax Analytics"
                            AppDestination.EXPENSES -> "Transactions Ledger"
                            AppDestination.ADD -> "Record Expense / Scan"
                            AppDestination.SETTINGS -> "Settings & Privacy"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.testTag("main_bottom_navigation")
            ) {
                AppDestination.values().forEach { destination ->
                    val isSelected = currentDestination == destination
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentDestination = destination },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.title
                            )
                        },
                        label = {
                            Text(
                                text = destination.title,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag(destination.tag)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentDestination,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "ScreenTransition"
            ) { target ->
                when (target) {
                    AppDestination.DASHBOARD -> {
                        DashboardScreen(
                            viewModel = viewModel,
                            onNavigateToAddExpense = { currentDestination = AppDestination.ADD },
                            onNavigateToExpensesList = { currentDestination = AppDestination.EXPENSES },
                            onSelectExpense = { selectedExpenseForDetail = it }
                        )
                    }
                    AppDestination.EXPENSES -> {
                        ExpenseListScreen(
                            viewModel = viewModel,
                            onSelectExpense = { selectedExpenseForDetail = it },
                            onNavigateToAdd = { currentDestination = AppDestination.ADD }
                        )
                    }
                    AppDestination.ADD -> {
                        AddExpenseScreen(
                            viewModel = viewModel,
                            onSaved = { currentDestination = AppDestination.EXPENSES }
                        )
                    }
                    AppDestination.SETTINGS -> {
                        SettingsScreen(viewModel = viewModel)
                    }
                }
            }
        }

        // Transaction Detail and Decrypted Receipt Viewer Dialog
        selectedExpenseForDetail?.let { expense ->
            TransactionDetailDialog(
                expense = expense,
                currencySymbol = settings.currencySymbol,
                receiptBitmap = viewingReceiptBitmap,
                isLoadingReceipt = isLoadingReceipt,
                onLoadReceipt = { fileName -> viewModel.loadReceipt(fileName) },
                onDelete = { exp ->
                    viewModel.deleteExpense(exp)
                    selectedExpenseForDetail = null
                },
                onDismiss = {
                    selectedExpenseForDetail = null
                    viewModel.clearViewingReceipt()
                }
            )
        }
    }
}
