package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.ExpenseCategory
import com.example.model.TaxCategory
import com.example.ui.ExpenseViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    viewModel: ExpenseViewModel,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val scannedData by viewModel.scannedData.collectAsStateWithLifecycle()

    var amountText by remember { mutableStateOf("") }
    var merchantText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Groceries") }
    var dateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isTaxDeductible by remember { mutableStateOf(false) }
    var selectedTaxCategory by remember { mutableStateOf(TaxCategory.ALL.first().name) }
    var tagsText by remember { mutableStateOf("") }
    var notesText by remember { mutableStateOf("") }
    var receiptBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var amountError by remember { mutableStateOf(false) }
    var merchantError by remember { mutableStateOf(false) }

    // When OCR extracts receipt data, fill into form
    LaunchedEffect(scannedData) {
        scannedData?.let { data ->
            if (data.amount != null) {
                amountText = String.format(Locale.US, "%.2f", data.amount)
            }
            if (data.merchant.isNotBlank()) {
                merchantText = data.merchant
            }
            if (data.suggestedCategory.isNotBlank()) {
                selectedCategory = data.suggestedCategory
            }
            if (data.isTaxDeductible) {
                isTaxDeductible = true
                if (data.suggestedTaxCategory != null) {
                    selectedTaxCategory = data.suggestedTaxCategory
                }
            }
            if (data.suggestedTags.isNotEmpty()) {
                tagsText = data.suggestedTags.joinToString(", ")
            }
            dateMillis = data.dateMillis
        }
    }

    // Camera Capture Launcher (TakePicturePreview returns Bitmap thumbnail)
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            receiptBitmap = bitmap
            viewModel.scanReceipt(bitmap)
        }
    }

    // Photo Gallery Picker Launcher (Zero permissions Android photo picker)
    val pickMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                receiptBitmap = bitmap
                viewModel.scanReceipt(bitmap)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val dateFormat = remember { SimpleDateFormat("EEE, MMM d, yyyy", Locale.US) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 80.dp)
            .testTag("add_expense_screen")
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Title and description
        Text(
            text = "Record Transaction",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Scan receipts locally with on-device OCR or enter manually",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Receipt Scan Options Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("receipt_scanner_card"),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.DocumentScanner,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "On-Device Receipt Scanner",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Snap a photo of your receipt. On-device ML Kit reads the vendor, total, and tax status offline with zero cloud transmission.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { takePictureLauncher.launch(null) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("camera_scan_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Camera", fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = {
                            pickMediaLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("gallery_scan_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Gallery", fontWeight = FontWeight.SemiBold)
                    }
                }

                // Scanning Progress
                if (isScanning) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(
                            text = "Analyzing receipt with offline OCR...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Receipt Preview Thumbnail if captured
                if (receiptBitmap != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Image(
                                    bitmap = receiptBitmap!!.asImageBitmap(),
                                    contentDescription = "Receipt Thumbnail",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = "Attached & Hardware Encrypted",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Text(
                                        text = "Will be saved securely as encrypted file",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            IconButton(onClick = {
                                receiptBitmap = null
                                viewModel.clearScannedData()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Remove receipt", tint = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Amount Field
        OutlinedTextField(
            value = amountText,
            onValueChange = {
                amountText = it
                amountError = false
            },
            label = { Text("Amount Spent *") },
            prefix = { Text(settings.currencySymbol, fontWeight = FontWeight.Bold) },
            placeholder = { Text("0.00") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = amountError,
            supportingText = if (amountError) { { Text("Please enter a valid amount") } } else null,
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("expense_amount_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Merchant Field
        OutlinedTextField(
            value = merchantText,
            onValueChange = {
                merchantText = it
                merchantError = false
            },
            label = { Text("Merchant / Store Name *") },
            placeholder = { Text("e.g. Trader Joe's, Chevron, Apple") },
            singleLine = true,
            isError = merchantError,
            supportingText = if (merchantError) { { Text("Please enter a merchant name") } } else null,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("expense_merchant_input")
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Category Selection
        Text(
            text = "Category",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExpenseCategory.ALL.forEach { cat ->
                val selected = selectedCategory.equals(cat.name, ignoreCase = true)
                FilterChip(
                    selected = selected,
                    onClick = { selectedCategory = cat.name },
                    leadingIcon = {
                        Icon(
                            imageVector = cat.icon,
                            contentDescription = null,
                            tint = if (selected) cat.color else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text(cat.name, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = cat.color.copy(alpha = 0.2f),
                        selectedLabelColor = cat.color
                    ),
                    modifier = Modifier.testTag("category_select_${cat.name}")
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Date selection
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Transaction Date",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = dateFormat.format(Date(dateMillis)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = { dateMillis = System.currentTimeMillis() },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Today", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { dateMillis = System.currentTimeMillis() - 86_400_000L },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Yesterday", fontSize = 12.sp)
                }
            }
        }

        // Tax Deductible Section (when Tax Tracker is enabled in settings)
        if (settings.isTaxTrackerEnabled) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isTaxDeductible) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = if (isTaxDeductible) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "Tax Deductible Expense",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Tag for business, medical, or donation deduction",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = isTaxDeductible,
                            onCheckedChange = { isTaxDeductible = it },
                            modifier = Modifier.testTag("tax_deductible_toggle"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.tertiary,
                                checkedTrackColor = MaterialTheme.colorScheme.tertiaryContainer
                            )
                        )
                    }

                    if (isTaxDeductible) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Tax Deduction Category:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            TaxCategory.ALL.forEach { taxCat ->
                                val selected = selectedTaxCategory == taxCat.name
                                FilterChip(
                                    selected = selected,
                                    onClick = { selectedTaxCategory = taxCat.name },
                                    label = { Text(taxCat.name, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.tertiary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onTertiary
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Tags Field
        OutlinedTextField(
            value = tagsText,
            onValueChange = { tagsText = it },
            label = { Text("Merchant / Item Tags (comma separated)") },
            placeholder = { Text("e.g. groceries, organic, tax2026, client") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("expense_tags_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Notes Field
        OutlinedTextField(
            value = notesText,
            onValueChange = { notesText = it },
            label = { Text("Notes / Description (Optional)") },
            placeholder = { Text("e.g. Lunch with design team, project kickoff") },
            maxLines = 3,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("expense_notes_input")
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Save Button
        Button(
            onClick = {
                val amount = amountText.toDoubleOrNull()
                if (amount == null || amount <= 0.0) {
                    amountError = true
                    return@Button
                }
                if (merchantText.isBlank()) {
                    merchantError = true
                    return@Button
                }

                viewModel.saveExpense(
                    amount = amount,
                    merchant = merchantText.trim(),
                    category = selectedCategory,
                    dateMillis = dateMillis,
                    isTaxDeductible = isTaxDeductible,
                    taxCategory = if (isTaxDeductible) selectedTaxCategory else null,
                    tags = tagsText.trim(),
                    notes = notesText.trim(),
                    receiptBitmap = receiptBitmap,
                    onComplete = onSaved
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("save_expense_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(Icons.Default.Check, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save Encrypted Expense", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}
