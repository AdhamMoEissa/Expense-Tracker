package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CategoryColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DailyCategorySpend(
    val dateMillis: Long,
    val dayLabel: String,
    val fullDateLabel: String,
    val totalAmount: Double,
    val categoryAmounts: Map<String, Double>
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StackedBarChart(
    dailyData: List<DailyCategorySpend>,
    currencySymbol: String = "$",
    modifier: Modifier = Modifier
) {
    if (dailyData.isEmpty() || dailyData.all { it.totalAmount == 0.0 }) {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "No spending recorded for this period",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val maxSpend = remember(dailyData) {
        val highest = dailyData.maxOfOrNull { it.totalAmount } ?: 1.0
        if (highest <= 0.0) 1.0 else highest * 1.15 // 15% headroom
    }

    // Collect all present categories to show in legend
    val presentCategories = remember(dailyData) {
        dailyData.flatMap { it.categoryAmounts.keys }
            .distinct()
            .filter { cat -> dailyData.sumOf { it.categoryAmounts[cat] ?: 0.0 } > 0.0 }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("stacked_bar_chart_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Daily Spending Trend",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Multi-color bars show category breakdown per day",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "Peak: $currencySymbol${String.format(Locale.US, "%.0f", maxSpend)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Canvas drawing the stacked bars
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .pointerInput(dailyData) {
                            detectTapGestures { offset ->
                                val barWidthTotal = size.width / dailyData.size
                                val tappedIndex = (offset.x / barWidthTotal).toInt()
                                if (tappedIndex in dailyData.indices) {
                                    selectedIndex = if (selectedIndex == tappedIndex) null else tappedIndex
                                }
                            }
                        }
                ) {
                    val count = dailyData.size
                    val totalWidth = size.width
                    val slotWidth = totalWidth / count
                    val barWidth = (slotWidth * 0.65f).coerceIn(10f, 36f)
                    val chartHeight = size.height - 35f // leave room for labels

                    // Draw subtle grid lines
                    val gridSteps = 3
                    for (step in 1..gridSteps) {
                        val y = chartHeight * (1f - step / gridSteps.toFloat())
                        drawLine(
                            color = Color(0x18708090),
                            start = Offset(0f, y),
                            end = Offset(totalWidth, y),
                            strokeWidth = 1f
                        )
                    }

                    dailyData.forEachIndexed { index, day ->
                        val centerX = (index * slotWidth) + (slotWidth / 2f)
                        val left = centerX - (barWidth / 2f)
                        val right = left + barWidth

                        // Highlight background if selected
                        if (selectedIndex == index) {
                            drawRoundRect(
                                color = Color(0x1510B981),
                                topLeft = Offset(left - 4f, 0f),
                                size = Size(barWidth + 8f, chartHeight),
                                cornerRadius = CornerRadius(6f, 6f)
                            )
                        }

                        if (day.totalAmount > 0.0) {
                            // Clip the entire stacked bar to a rounded rectangle path
                            val totalBarHeight = ((day.totalAmount / maxSpend) * chartHeight).toFloat().coerceAtLeast(4f)
                            val barTop = chartHeight - totalBarHeight

                            val barPath = Path().apply {
                                addRoundRect(
                                    androidx.compose.ui.geometry.RoundRect(
                                        left = left,
                                        top = barTop,
                                        right = right,
                                        bottom = chartHeight,
                                        cornerRadius = CornerRadius(6f, 6f)
                                    )
                                )
                            }

                            clipPath(barPath) {
                                var currentBottomY = chartHeight

                                // Draw category segments from bottom to top
                                day.categoryAmounts.forEach { (categoryName, amount) ->
                                    if (amount > 0.0) {
                                        val segmentHeight = ((amount / maxSpend) * chartHeight).toFloat().coerceAtLeast(2f)
                                        val segmentTop = currentBottomY - segmentHeight
                                        val color = CategoryColors[categoryName] ?: Color(0xFF64748B)

                                        drawRect(
                                            color = color,
                                            topLeft = Offset(left, segmentTop),
                                            size = Size(barWidth, segmentHeight)
                                        )

                                        // Subtle divider line between segments
                                        drawLine(
                                            color = Color.White.copy(alpha = 0.35f),
                                            start = Offset(left, segmentTop),
                                            end = Offset(right, segmentTop),
                                            strokeWidth = 1f
                                        )

                                        currentBottomY = segmentTop
                                    }
                                }
                            }
                        } else {
                            // Empty day indicator dot
                            drawCircle(
                                color = Color(0x3094A3B8),
                                radius = 3f,
                                center = Offset(centerX, chartHeight - 4f)
                            )
                        }
                    }
                }
            }

            // Labels row below canvas
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                dailyData.forEachIndexed { index, day ->
                    Text(
                        text = day.dayLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = if (selectedIndex == index) FontWeight.Bold else FontWeight.Normal,
                        color = if (selectedIndex == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                selectedIndex = if (selectedIndex == index) null else index
                            }
                    )
                }
            }

            // Interactive Tooltip Card for tapped day
            AnimatedVisibility(
                visible = selectedIndex != null && selectedIndex!! in dailyData.indices,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                selectedIndex?.let { idx ->
                    val day = dailyData[idx]
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = day.fullDateLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Total: $currencySymbol${String.format(Locale.US, "%.2f", day.totalAmount)}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            if (day.categoryAmounts.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    day.categoryAmounts.forEach { (cat, amt) ->
                                        if (amt > 0.0) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .background(CategoryColors[cat] ?: Color.Gray, CircleShape)
                                                )
                                                Text(
                                                    text = "$cat: $currencySymbol${String.format(Locale.US, "%.2f", amt)}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Category Color Legend
            if (presentCategories.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Category Legend:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presentCategories.forEach { cat ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(CategoryColors[cat] ?: Color.Gray, RoundedCornerShape(2.dp))
                            )
                            Text(
                                text = cat,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
