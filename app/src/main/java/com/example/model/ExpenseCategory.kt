package com.example.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.theme.CategoryColors

data class ExpenseCategory(
    val name: String,
    val icon: ImageVector,
    val color: Color
) {
    companion object {
        val ALL = listOf(
            ExpenseCategory("Groceries", Icons.Default.ShoppingCart, CategoryColors["Groceries"] ?: Color(0xFF10B981)),
            ExpenseCategory("Dining", Icons.Default.Fastfood, CategoryColors["Dining"] ?: Color(0xFFF59E0B)),
            ExpenseCategory("Housing", Icons.Default.Home, CategoryColors["Housing"] ?: Color(0xFF3B82F6)),
            ExpenseCategory("Transportation", Icons.Default.DirectionsCar, CategoryColors["Transportation"] ?: Color(0xFF8B5CF6)),
            ExpenseCategory("Utilities", Icons.Default.Business, CategoryColors["Utilities"] ?: Color(0xFF06B6D4)),
            ExpenseCategory("Healthcare", Icons.Default.LocalHospital, CategoryColors["Healthcare"] ?: Color(0xFFEF4444)),
            ExpenseCategory("Entertainment", Icons.Default.Movie, CategoryColors["Entertainment"] ?: Color(0xFFEC4899)),
            ExpenseCategory("Office & Work", Icons.Default.Work, CategoryColors["Office & Work"] ?: Color(0xFF6366F1)),
            ExpenseCategory("Shopping", Icons.Default.ShoppingCart, CategoryColors["Shopping"] ?: Color(0xFF14B8A6)),
            ExpenseCategory("Travel", Icons.Default.Flight, CategoryColors["Travel"] ?: Color(0xFFF97316)),
            ExpenseCategory("Personal Care", Icons.Default.Spa, CategoryColors["Personal Care"] ?: Color(0xFF84CC16)),
            ExpenseCategory("Education", Icons.Default.School, CategoryColors["Education"] ?: Color(0xFFA855F7)),
            ExpenseCategory("Other", Icons.Default.Category, CategoryColors["Other"] ?: Color(0xFF64748B))
        )

        fun fromName(name: String): ExpenseCategory {
            return ALL.find { it.name.equals(name, ignoreCase = true) }
                ?: ExpenseCategory(name, Icons.Default.Category, CategoryColors[name] ?: Color(0xFF64748B))
        }
    }
}
