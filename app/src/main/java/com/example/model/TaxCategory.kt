package com.example.model

data class TaxCategory(
    val name: String,
    val description: String
) {
    companion object {
        val ALL = listOf(
            TaxCategory("Business Expense", "Ordinary & necessary expenses for business or trade"),
            TaxCategory("Home Office", "Dedicated home workspace equipment, supplies, internet"),
            TaxCategory("Medical & Healthcare", "Qualified healthcare, prescriptions, dental"),
            TaxCategory("Charitable Contribution", "Donations to qualified 501(c)(3) organizations"),
            TaxCategory("Vehicle & Mileage", "Work-related travel, business trips, client visits"),
            TaxCategory("Professional Supplies", "Tools, software, books, and business gear"),
            TaxCategory("Education & Training", "Courses, certifications, seminars for profession"),
            TaxCategory("Other Deduction", "Other deductible expense under tax guidelines")
        )
    }
}
