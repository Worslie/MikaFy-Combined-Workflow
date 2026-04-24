package com.example.aibudgettracker.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable

/** Simple Bottom Navigation Bar */
@Composable
fun MyBottomNavigation(sel: Int, onSel: (Int) -> Unit) {
    // Reverting to the standard Material 3 NavigationBar implementation.
    // This follows industry standards for height (80dp) and internal element positioning,
    // and automatically handles system window insets for safe areas.
    NavigationBar {
        NavigationBarItem(
            selected = sel == 0,
            onClick = { onSel(0) },
            label = { Text("Budget") },
            icon = { 
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "Budget"
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
        NavigationBarItem(
            selected = sel == 1,
            onClick = { onSel(1) },
            label = { Text("Edit") },
            icon = { 
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Edit"
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
    }
}
