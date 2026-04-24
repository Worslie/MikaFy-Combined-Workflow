package com.example.aibudgettracker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aibudgettracker.data.BudgetRepository
import com.example.aibudgettracker.data.Transaction
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * Secondary screen that shows every transaction for a specific category.
 * Filters transactions by the date range and business/personal view selected on the main screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    catName: String,
    repo: BudgetRepository,
    startDate: LocalDate,
    endDate: LocalDate,
    isBusinessView: Boolean,
    globalEdit: Boolean,
    onBack: () -> Unit
) {
    // Find the category that matches both the name and the current view (Personal vs Business)
    val cat = repo.categories.find { it.name == catName && it.isBusiness == isBusinessView } ?: return

    // Filter transactions to match date range AND business/personal view
    val filteredTransactions = remember(cat.transactions, startDate, endDate, isBusinessView) {
        cat.transactions.filter {
            val txDate = LocalDate.parse(it.date)
            val dateMatch = !txDate.isBefore(startDate) && !txDate.isAfter(endDate)
            val typeMatch = it.isBusiness == isBusinessView
            dateMatch && typeMatch
        }
    }

    val selectedTxs = remember { mutableStateListOf<Transaction>() }
    var showMoveSheet by remember { mutableStateOf(false) }

    // Clear selection if edit mode is turned off
    LaunchedEffect(globalEdit) {
        if (!globalEdit) selectedTxs.clear()
    }

    if (showMoveSheet && selectedTxs.isNotEmpty()) {
        ModalBottomSheet(
            onDismissRequest = { showMoveSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            MoveTransactionContent(
                tx = selectedTxs.first(),
                categories = repo.categories,
                onMove = { targetCat, toBusiness ->
                    selectedTxs.forEach { repo.moveTransaction(it, catName, targetCat, toBusiness) }
                    selectedTxs.clear()
                    showMoveSheet = false
                }
            )
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(8.dp))

            LazyColumn(Modifier.weight(1f)) {
                items(filteredTransactions) { tx ->
                    val isSelected = selectedTxs.contains(tx)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (globalEdit) {
                                    if (isSelected) selectedTxs.remove(tx) else selectedTxs.add(tx)
                                }
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (globalEdit) {
                            Icon(
                                imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                        }

                        Column(Modifier.weight(1f)) {
                            Text(tx.merchant, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                            val dateColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                            Text(
                                LocalDate.parse(tx.date)
                                    .format(DateTimeFormatter.ofPattern("MMM dd, yyyy")),
                                fontSize = 12.sp,
                                color = dateColor
                            )
                            if (tx.description.isNotBlank()) Text(
                                tx.description,
                                fontSize = 12.sp,
                                color = dateColor
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val isIncome = tx.amount > 0
                            Text(
                                text = if (isIncome) "+£${String.format("%.2f", tx.amount)}" else "£${String.format("%.2f", abs(tx.amount))}",
                                color = if (isIncome) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    HorizontalDivider()
                }
                
                if (filteredTransactions.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = "No ${if (isBusinessView) "business" else "personal"} transactions found.",
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
            
            // Space for the floating action bar
            if (globalEdit && selectedTxs.isNotEmpty()) {
                Spacer(Modifier.height(80.dp))
            }
        }

        // Contextual Selection Bar
        if (globalEdit && selectedTxs.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 4.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${selectedTxs.size} Selected",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    
                    Row {
                        IconButton(onClick = { showMoveSheet = true }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Move Selected",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = {
                            selectedTxs.forEach { repo.removeTransaction(catName, it) }
                            selectedTxs.clear()
                        }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete Selected",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MoveTransactionContent(
    tx: Transaction,
    categories: List<com.example.aibudgettracker.data.BudgetCategory>,
    onMove: (String, Boolean) -> Unit
) {
    var selectedSide by remember { mutableIntStateOf(if (tx.isBusiness) 1 else 0) }
    
    Column(Modifier.padding(bottom = 32.dp)) {
        Text(
            "Move Transaction",
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )
        
        TabRow(
            selectedTabIndex = selectedSide,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Tab(selected = selectedSide == 0, onClick = { selectedSide = 0 }) {
                Text("Personal", Modifier.padding(16.dp))
            }
            Tab(selected = selectedSide == 1, onClick = { selectedSide = 1 }) {
                Text("Business", Modifier.padding(16.dp))
            }
        }
        
        Spacer(Modifier.height(8.dp))
        
        val isBusinessTarget = selectedSide == 1
        val targetCats = categories.filter { it.isBusiness == isBusinessTarget && it.isVisible }
        
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
            items(targetCats) { cat ->
                ListItem(
                    headlineContent = { Text(cat.name) },
                    supportingContent = { if (cat.categoryDescription.isNotBlank()) Text(cat.categoryDescription) },
                    modifier = Modifier.clickable { onMove(cat.name, isBusinessTarget) }
                )
            }
        }
    }
}
