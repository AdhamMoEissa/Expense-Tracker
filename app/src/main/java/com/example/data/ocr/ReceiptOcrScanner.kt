package com.example.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern
import kotlin.coroutines.resume

data class ScannedReceiptData(
    val merchant: String,
    val amount: Double?,
    val dateMillis: Long,
    val suggestedCategory: String,
    val isTaxDeductible: Boolean,
    val suggestedTaxCategory: String?,
    val suggestedTags: List<String>,
    val rawText: String
)

class ReceiptOcrScanner(private val context: Context) {

    // On-device latin text recognizer (bundled, runs 100% locally offline)
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun scanBitmap(bitmap: Bitmap): ScannedReceiptData {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        return processInputImage(inputImage)
    }

    suspend fun scanUri(uri: Uri): ScannedReceiptData {
        val inputImage = InputImage.fromFilePath(context, uri)
        return processInputImage(inputImage)
    }

    private suspend fun processInputImage(image: InputImage): ScannedReceiptData =
        suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val result = parseVisionText(visionText.text)
                    continuation.resume(result)
                }
                .addOnFailureListener { exception ->
                    // Fallback to empty data on error so user can input manually
                    continuation.resume(
                        ScannedReceiptData(
                            merchant = "",
                            amount = null,
                            dateMillis = System.currentTimeMillis(),
                            suggestedCategory = "Other",
                            isTaxDeductible = false,
                            suggestedTaxCategory = null,
                            suggestedTags = emptyList(),
                            rawText = ""
                        )
                    )
                }
        }

    private fun parseVisionText(fullText: String): ScannedReceiptData {
        val lines = fullText.lines().map { it.trim() }.filter { it.isNotEmpty() }

        val merchant = extractMerchant(lines)
        val amount = extractAmount(lines, fullText)
        val dateMillis = extractDate(lines) ?: System.currentTimeMillis()
        val category = extractCategory(merchant, fullText)
        val (isDeductible, taxCategory) = extractTaxDeductible(category, fullText)
        val tags = extractTags(merchant, category, fullText)

        return ScannedReceiptData(
            merchant = merchant,
            amount = amount,
            dateMillis = dateMillis,
            suggestedCategory = category,
            isTaxDeductible = isDeductible,
            suggestedTaxCategory = taxCategory,
            suggestedTags = tags,
            rawText = fullText
        )
    }

    private fun extractMerchant(lines: List<String>): String {
        // Skip common receipt header words (welcome, receipt, phone, store#, date, etc.)
        val skipKeywords = listOf(
            "welcome", "receipt", "invoice", "order", "cashier", "register",
            "store #", "tel", "phone", "thank you", "customer", "terminal", "table", "guest"
        )

        for (line in lines.take(6)) {
            val lower = line.lowercase(Locale.ROOT)
            val isSkip = skipKeywords.any { lower.contains(it) }
            val hasLetters = line.any { it.isLetter() }
            val isDateOrTime = line.matches(Regex(".*\\d{1,2}[:/\\-]\\d{1,2}.*"))

            if (!isSkip && hasLetters && !isDateOrTime && line.length >= 3) {
                // Clean up leading/trailing symbols
                val clean = line.replace(Regex("^[^a-zA-Z0-9]+|[^a-zA-Z0-9]+$"), "")
                if (clean.isNotBlank()) {
                    return clean
                }
            }
        }

        return lines.firstOrNull { it.any { c -> c.isLetter() } } ?: "Unknown Merchant"
    }

    private fun extractAmount(lines: List<String>, fullText: String): Double? {
        val totalKeywords = listOf("total", "bal due", "amount due", "balance", "net amount", "grand total", "sum")
        val amountRegex = Pattern.compile("[$€£¥]?\\s*([0-9]+[.,][0-9]{2})\\b")

        // 1. Search for lines with "TOTAL" keyword and extract amount on that line or next line
        for (i in lines.indices) {
            val lineLower = lines[i].lowercase(Locale.ROOT)
            if (totalKeywords.any { lineLower.contains(it) } && !lineLower.contains("subtotal")) {
                val matcher = amountRegex.matcher(lines[i])
                var lastFound: Double? = null
                while (matcher.find()) {
                    val raw = matcher.group(1)?.replace(",", ".")
                    lastFound = raw?.toDoubleOrNull()
                }
                if (lastFound != null && lastFound > 0.0) {
                    return lastFound
                }
                // Check immediate next line if amount is on the next line
                if (i + 1 < lines.size) {
                    val nextMatcher = amountRegex.matcher(lines[i + 1])
                    if (nextMatcher.find()) {
                        val raw = nextMatcher.group(1)?.replace(",", ".")
                        val parsed = raw?.toDoubleOrNull()
                        if (parsed != null && parsed > 0.0) {
                            return parsed
                        }
                    }
                }
            }
        }

        // 2. Fallback: Find the largest reasonable amount on the receipt
        val allAmounts = mutableListOf<Double>()
        val matcher = amountRegex.matcher(fullText)
        while (matcher.find()) {
            val raw = matcher.group(1)?.replace(",", ".")
            raw?.toDoubleOrNull()?.let {
                if (it in 0.50..50000.0) {
                    allAmounts.add(it)
                }
            }
        }

        return allAmounts.maxOrNull()
    }

    private fun extractDate(lines: List<String>): Long? {
        val dateFormats = listOf(
            SimpleDateFormat("MM/dd/yyyy", Locale.US),
            SimpleDateFormat("MM-dd-yyyy", Locale.US),
            SimpleDateFormat("yyyy-MM-dd", Locale.US),
            SimpleDateFormat("dd/MM/yyyy", Locale.US),
            SimpleDateFormat("MMM dd, yyyy", Locale.US),
            SimpleDateFormat("MM/dd/yy", Locale.US),
            SimpleDateFormat("dd-MMM-yyyy", Locale.US)
        )

        val datePattern = Regex("\\b(\\d{1,4}[/-]\\d{1,2}[/-]\\d{2,4})\\b")

        for (line in lines) {
            val match = datePattern.find(line)
            if (match != null) {
                val dateStr = match.value
                for (fmt in dateFormats) {
                    try {
                        val parsed = fmt.parse(dateStr)
                        if (parsed != null) {
                            val cal = Calendar.getInstance().apply { time = parsed }
                            // Sanity check year between 2000 and 2035
                            val year = cal.get(Calendar.YEAR)
                            if (year in 2000..2035) {
                                return parsed.time
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }
        return null
    }

    private fun extractCategory(merchant: String, fullText: String): String {
        val combined = "$merchant $fullText".lowercase(Locale.ROOT)

        return when {
            combined.containsAny("grocer", "market", "walmart", "kroger", "whole foods", "trader joe", "aldi", "safeway", "costco", "supermarket", "produce", "bakery") -> "Groceries"
            combined.containsAny("restaurant", "cafe", "coffee", "starbucks", "mcdonald", "burger", "pizza", "bistro", "grill", "bar", "chipotle", "diner", "food", "dining") -> "Dining"
            combined.containsAny("uber", "lyft", "shell", "chevron", "bp", "exxon", "gas", "fuel", "parking", "transit", "subway", "train", "metro", "auto", "car") -> "Transportation"
            combined.containsAny("pharmacy", "cvs", "walgreens", "clinic", "hospital", "doctor", "health", "dental", "medicine", "rx", "care") -> "Healthcare"
            combined.containsAny("electric", "water", "utility", "power", "internet", "verizon", "at&t", "t-mobile", "telecom", "energy") -> "Utilities"
            combined.containsAny("staples", "office depot", "adobe", "google cloud", "aws", "microsoft", "zoom", "fedex", "ups", "shipping", "consult", "software", "slack") -> "Office & Work"
            combined.containsAny("cinema", "theater", "movie", "amc", "netflix", "spotify", "ticket", "concert", "game", "bowling") -> "Entertainment"
            combined.containsAny("hotel", "airline", "delta", "united", "american air", "airbnb", "marriott", "hilton", "flight", "resort") -> "Travel"
            combined.containsAny("home depot", "lowe's", "ikea", "furniture", "hardware", "rent", "housing", "mortgage") -> "Housing"
            combined.containsAny("salon", "barber", "hair", "spa", "cosmetics", "sephora", "gym", "fitness") -> "Personal Care"
            combined.containsAny("book", "course", "university", "school", "tuition", "udemy", "training", "education") -> "Education"
            combined.containsAny("amazon", "target", "best buy", "clothing", "apparel", "store", "mall") -> "Shopping"
            else -> "Other"
        }
    }

    private fun extractTaxDeductible(category: String, fullText: String): Pair<Boolean, String?> {
        val lower = fullText.lowercase(Locale.ROOT)
        return when {
            category == "Office & Work" || lower.containsAny("office", "supplies", "business", "client", "consulting", "software license", "domain") ->
                true to "Business Expense"
            lower.containsAny("home office", "monitor", "printer", "desk", "ergonomic") ->
                true to "Home Office"
            lower.containsAny("charity", "donation", "foundation", "501(c)", "non-profit", "goodwill", "red cross") ->
                true to "Charitable Contribution"
            category == "Healthcare" || lower.containsAny("prescription", "medical", "dental", "physician", "copay") ->
                true to "Medical & Healthcare"
            lower.containsAny("mileage", "parking for meeting", "toll", "business travel", "flight for conference") ->
                true to "Vehicle & Mileage"
            category == "Education" || lower.containsAny("certification", "professional workshop", "seminar") ->
                true to "Education & Training"
            else -> false to null
        }
    }

    private fun extractTags(merchant: String, category: String, fullText: String): List<String> {
        val tags = mutableListOf<String>()
        tags.add(category.lowercase(Locale.ROOT))
        val merchantTag = merchant.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
        if (merchantTag.isNotEmpty() && merchantTag.length <= 15) {
            tags.add(merchantTag)
        }
        val lower = fullText.lowercase(Locale.ROOT)
        if (lower.contains("tax") || lower.contains("deductible")) tags.add("tax-deductible")
        if (lower.contains("business") || lower.contains("work")) tags.add("work")
        if (lower.contains("subscription") || lower.contains("monthly")) tags.add("recurring")
        return tags.distinct()
    }

    private fun String.containsAny(vararg candidates: String): Boolean {
        return candidates.any { this.contains(it) }
    }
}
