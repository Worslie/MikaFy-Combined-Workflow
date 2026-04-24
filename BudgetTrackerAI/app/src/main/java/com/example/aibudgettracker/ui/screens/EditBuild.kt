package com.example.aibudgettracker.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aibudgettracker.data.BudgetRepository
import java.time.LocalDate

enum class EditMenu { SETTINGS, ACCOUNT, PROFILE, BANKING, CSV }

@Composable
fun EditBuild(
    repo: BudgetRepository,
    currentBudget: Float,
    currentTarget: LocalDate,
    currentCycle: String,
    currentDay: String,
    userEmail: String?,
    onSignOut: () -> Unit,
    onBudgetUpdate: (Float) -> Unit,
    onTimelineUpdate: (LocalDate) -> Unit,
    onCycleSettingsUpdate: (String, String) -> Unit,
    onComplete: () -> Unit,
    onNavigateToSignIn: () -> Unit,
    onLinkBank: () -> Unit,
    initialMenu: EditMenu? = null,
    onMenuChange: (EditMenu?) -> Unit = {}
) {
    var activeMenu by remember(initialMenu) { mutableStateOf(initialMenu) }

    if (activeMenu != null) {
        // Handle physical back button - if in Profile, go back to Account
        BackHandler { 
            val nextMenu = if (activeMenu == EditMenu.PROFILE) EditMenu.ACCOUNT else null 
            activeMenu = nextMenu
            onMenuChange(nextMenu)
        }
        
        Column(Modifier.fillMaxSize()) {
            // Header with back button
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { 
                    val nextMenu = if (activeMenu == EditMenu.PROFILE) EditMenu.ACCOUNT else null 
                    activeMenu = nextMenu
                    onMenuChange(nextMenu)
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Sub-pages from EditBuildPages.kt
            when (activeMenu) {
                EditMenu.SETTINGS -> SettingsPage(
                    currentBudget = currentBudget,
                    currentCycle = currentCycle,
                    currentDay = currentDay,
                    onBudgetUpdate = onBudgetUpdate,
                    onCycleSettingsUpdate = onCycleSettingsUpdate,
                    onTimelineUpdate = onTimelineUpdate
                )
                EditMenu.ACCOUNT -> AccountPage(
                    userEmail = userEmail,
                    onSignOut = onSignOut,
                    onNavigateToSignIn = onNavigateToSignIn,
                    onNavigateToProfile = { 
                        activeMenu = EditMenu.PROFILE
                        onMenuChange(EditMenu.PROFILE)
                    }
                )
                EditMenu.PROFILE -> ProfilePage()
                EditMenu.BANKING -> BankingPage(onLinkBank = onLinkBank)
                EditMenu.CSV -> CSVPage(repo = repo, onComplete = { 
                    activeMenu = null
                    onMenuChange(null)
                })
                else -> {}
            }
        }
    } else {
        // Main Management Menu
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(16.dp))

            // Rounded container matching the Budget Screen style
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    val menus = listOf(
                        "Settings" to EditMenu.SETTINGS,
                        "Account" to EditMenu.ACCOUNT,
                        "Banking" to EditMenu.BANKING,
                        "CSV" to EditMenu.CSV
                    )
                    
                    menus.forEachIndexed { index, (title, menu) ->
                        MenuRow(
                            title = title,
                            onClick = { 
                                activeMenu = menu
                                onMenuChange(menu)
                            },
                            showDivider = index < menus.size - 1
                        )
                    }
                }
            }
        }
    }
}
