package com.example.aibudgettracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aibudgettracker.data.BudgetCategory
import kotlin.math.abs

/** Individual row for a category in the "Spending Insights" section */
@Composable
fun CategoryRow(
    cat: BudgetCategory,
    isEdit: Boolean,
    onSel: (BudgetCategory) -> Unit,
    onToggle: () -> Unit,
    onDel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = cat.isVisible) { onSel(cat) }
            .padding(horizontal = 24.dp)
    ) {
        Row(
            Modifier
                .padding(vertical = 12.dp)
                .heightIn(min = 32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                cat.name,
                fontWeight = FontWeight.Normal,
                fontSize = 18.sp,
                color = if (cat.isVisible) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline,
                modifier = Modifier.weight(1f)
            )
            
            if (cat.isVisible) {
                val amount = abs(cat.totalSpent)
                val parts = String.format("%.2f", amount).split(".")
                val pounds = parts[0]
                val pennies = parts[1]

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "£$pounds",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        ".$pennies",
                        fontSize = 14.4.sp, // 20% smaller than 18.sp
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(bottom = 1.dp) // Slight alignment adjustment
                    )
                }
            }

            // Management buttons visible only in Edit Mode
            if (isEdit && cat.name != "General") {
                if (cat.isDefault) {
                    // Only show "Hide/Show" for default categories (like Bills, Groceries, etc.)
                    TextButton(onClick = onToggle) {
                        Text(
                            if (cat.isVisible) "Hide" else "Show",
                            fontSize = 12.sp
                        )
                    }
                } else {
                    // Show delete icon for custom (AI-generated or user-added) categories
                    IconButton(onClick = onDel) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
        // Divider using the primary theme color (0e7895) with subtle transparency
        HorizontalDivider(
            thickness = 0.5.dp, 
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
    }
}
