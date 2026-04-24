package com.example.aibudgettracker.ui.screens

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aibudgettracker.data.BudgetCategory
import com.example.aibudgettracker.data.BudgetRepository
import com.example.aibudgettracker.ui.components.CategoryRow
import com.example.aibudgettracker.ui.components.SpendingGraph
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

@Composable
fun BudgetBuild(
    repo: BudgetRepository,
    total: Float,
    days: Int,
    isEdit: Boolean,
    currentCycle: String,
    isBusinessView: Boolean,
    onSel: (BudgetCategory, LocalDate, LocalDate) -> Unit,
    onAdd: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("BudgetPrefs", Context.MODE_PRIVATE) }

    var showCardTemporarily by remember {
        val hideUntil = prefs.getLong("profile_card_hide_until", 0L)
        mutableStateOf(System.currentTimeMillis() >= hideUntil)
    }

    val isProfileComplete = remember {
        prefs.getBoolean("profile_completed", false)
    }

    val cycleLength = remember(currentCycle) {
        when (currentCycle) {
            "Monthly" -> 30L
            "Bi-Weekly" -> 14L
            else -> 7L
        }
    }

    var offset by remember { mutableLongStateOf(0L) }
    val currentPeriodEnd = remember(days) { LocalDate.now().plusDays(days.toLong()) }

    val viewStartDate: LocalDate = remember(currentPeriodEnd, offset, cycleLength) {
        if (cycleLength == 30L) {
            // Strictly the first day of the month preceding currentPeriodEnd
            currentPeriodEnd.plusMonths(offset - 1).with(TemporalAdjusters.firstDayOfMonth())
        } else {
            val endDate = currentPeriodEnd.plusDays((offset * cycleLength) - 1)
            endDate.minusDays(cycleLength - 1)
        }
    }

    val viewEndDate: LocalDate = remember(viewStartDate, cycleLength) {
        if (cycleLength == 30L) {
            viewStartDate.with(TemporalAdjusters.lastDayOfMonth())
        } else {
            viewStartDate.plusDays(cycleLength - 1)
        }
    }

    // Filter categories to only show those matching the current view (Personal/Business)
    val displayList = repo.categories
        .filter { it.isBusiness == isBusinessView && (it.isVisible || isEdit || it.name == "General") }
        .map { cat ->
            val filteredTxs = cat.transactions.filter {
                val txDate = LocalDate.parse(it.date)
                val dateMatch = !txDate.isBefore(viewStartDate) && !txDate.isAfter(viewEndDate)
                val typeMatch = it.isBusiness == isBusinessView
                dateMatch && typeMatch
            }
            cat.copy(
                transactions = filteredTxs,
                totalSpent = filteredTxs.filter { it.amount < 0 }.sumOf { it.amount.toDouble() }.toFloat() * -1f
            )
        }
        .sortedWith(compareByDescending<BudgetCategory> { it.name == "General" }.thenByDescending { it.isVisible }
            .thenBy { it.name })

    val periodSpent = displayList.sumOf { it.totalSpent.toDouble() }.toFloat()
    val rem = (total - periodSpent).coerceAtLeast(0f)

    var totalDrag by remember { mutableFloatStateOf(0f) }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (totalDrag > 100) {
                            offset-- 
                        } else if (totalDrag < -100) {
                            if (offset < 0) offset++ 
                        }
                        totalDrag = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        totalDrag += dragAmount
                    }
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Spacer(Modifier.height(16.dp))

            // --- Details Prompt Card (Clickable with Dismiss 'x') ---
            if (!isProfileComplete && showCardTemporarily) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF0c7e5d) 
                ) {
                    Box(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToProfile() }
                                .padding(16.dp)
                                .padding(end = 32.dp), // Space for Dismiss button
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "Define your business profile for tailored expense tracking and AI accuracy.",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        IconButton(
                            onClick = {
                                val hideUntil = System.currentTimeMillis() + (48 * 60 * 60 * 1000L)
                                prefs.edit().putLong("profile_card_hide_until", hideUntil).apply()
                                showCardTemporarily = false
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // --- Rounded Edge Box for Summary and Graph ---
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            val label = if (cycleLength == 30L) viewStartDate.plusDays(10).format(DateTimeFormatter.ofPattern("MMMM yyyy"))
                            else "${viewStartDate.format(DateTimeFormatter.ofPattern("dd MMM"))} - ${viewEndDate.format(DateTimeFormatter.ofPattern("dd MMM"))}"
                            
                            Text(
                                label,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 22.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Resets in $days Days",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                fontSize = 14.sp
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "£${String.format("%.2f", rem)}",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Remaining",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    val progress = if (total > 0f) (periodSpent / total).coerceIn(0f, 1f) else 0f
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .height(8.dp)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    SpendingGraph(displayList, viewStartDate, viewEndDate)
                }
            }

            Spacer(Modifier.height(24.dp))
        }

        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Column(modifier = Modifier.padding(vertical = 16.dp)) {
                    Text(
                        "Spending Insights",
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    displayList.forEach { cat ->
                        CategoryRow(
                            cat,
                            isEdit,
                            onSel = { onSel(it, viewStartDate, viewEndDate) },
                            { repo.toggleVisibility(cat.name, isBusinessView) },
                            { repo.deleteCategory(cat.name, isBusinessView) })
                    }
                    
                    Spacer(Modifier.height(12.dp))
                    
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .clickable { onAdd() },
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    ) {
                        Box(
                            Modifier.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "+ Add Custom Category",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                                fontSize = 16.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            Spacer(Modifier.height(100.dp))
        }
    }
}
