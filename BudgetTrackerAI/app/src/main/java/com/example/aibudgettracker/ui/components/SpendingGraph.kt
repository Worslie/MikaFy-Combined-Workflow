package com.example.aibudgettracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aibudgettracker.data.BudgetCategory
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

@Composable
fun SpendingGraph(
    displayList: List<BudgetCategory>,
    startDate: LocalDate,
    endDate: LocalDate
) {
    val daysInRange = ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1
    // We show daily bars if the range is 7 days or less, otherwise we show weekly bars
    val isDailyView = daysInRange <= 7

    // 1. Flatten all transactions from the filtered category list
    val allTransactions = displayList.flatMap { it.transactions }
    
    val graphData = if (isDailyView) {
        // Daily sums
        val dailySums = allTransactions
            .groupBy { it.date }
            .mapValues { (_, txs) -> 
                txs.sumOf { abs(it.amount.toDouble()) }.toFloat() 
            }
        
        val list = mutableListOf<Pair<String, Float>>()
        var curr = startDate
        while (!curr.isAfter(endDate)) {
            val label = curr.dayOfMonth.toString()
            list.add(label to (dailySums[curr.toString()] ?: 0f))
            curr = curr.plusDays(1)
        }
        list
    } else {
        // Weekly sums
        val list = mutableListOf<Pair<String, Float>>()
        var currStart = startDate
        while (currStart.isBefore(endDate)) {
            val currEnd = currStart.plusDays(6).let { if (it.isAfter(endDate)) endDate else it }
            val weekSum = allTransactions.filter {
                val d = LocalDate.parse(it.date)
                !d.isBefore(currStart) && !d.isAfter(currEnd)
            }.sumOf { abs(it.amount.toDouble()) }.toFloat()
            
            val label = "${currStart.dayOfMonth}-${currEnd.dayOfMonth}"
            list.add(label to weekSum)
            currStart = currStart.plusDays(7)
        }
        list
    }

    // Calculate the maximum value to scale the bars linearly
    val maxVal = graphData.map { it.second }.maxOrNull()?.takeIf { it > 0 } ?: 1f

    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = if (isDailyView) "Daily Breakdown" else "Weekly Breakdown",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            Modifier
                .fillMaxWidth()
                .height(110.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            graphData.forEach { (label, value) ->
                // Linear scaling for the bars
                val barPercentage = if (value > 0) (value / maxVal) else 0f
                
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        // Background Base (Always visible)
                        Box(
                            Modifier
                                .width(16.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        )

                        // The Actual Spending Bar (Visible if there is spending)
                        if (value > 0) {
                            Box(
                                Modifier
                                    .width(16.dp)
                                    .fillMaxHeight(barPercentage.coerceIn(0.1f, 1f))
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1
                    )
                }
            }
        }
    }
}