package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val themeMode: String = "SYSTEM", // "SYSTEM", "LIGHT", "DARK"
    val isTaxTrackerEnabled: Boolean = true,
    val monthlyBudget: Double = 3500.0,
    val currencySymbol: String = "$"
)

class AppSettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("expense_tracker_prefs", Context.MODE_PRIVATE)

    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<AppSettings> = _settingsFlow.asStateFlow()

    private fun loadSettings(): AppSettings {
        return AppSettings(
            themeMode = prefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM",
            isTaxTrackerEnabled = prefs.getBoolean("tax_tracker_enabled", true),
            monthlyBudget = prefs.getFloat("monthly_budget", 3500.0f).toDouble(),
            currencySymbol = prefs.getString("currency_symbol", "$") ?: "$"
        )
    }

    fun setThemeMode(mode: String) {
        prefs.edit().putString("theme_mode", mode).apply()
        _settingsFlow.value = _settingsFlow.value.copy(themeMode = mode)
    }

    fun setTaxTrackerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("tax_tracker_enabled", enabled).apply()
        _settingsFlow.value = _settingsFlow.value.copy(isTaxTrackerEnabled = enabled)
    }

    fun setMonthlyBudget(budget: Double) {
        prefs.edit().putFloat("monthly_budget", budget.toFloat()).apply()
        _settingsFlow.value = _settingsFlow.value.copy(monthlyBudget = budget)
    }

    fun clearAllSettings() {
        prefs.edit().clear().apply()
        _settingsFlow.value = loadSettings()
    }
}
