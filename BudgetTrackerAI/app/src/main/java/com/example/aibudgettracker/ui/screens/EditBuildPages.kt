package com.example.aibudgettracker.ui.screens

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.aibudgettracker.data.BudgetRepository
import com.example.aibudgettracker.network.CsvImportWorker
import java.io.File
import java.io.FileOutputStream
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

@Composable
fun PageContainer(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            if (title.isNotBlank()) {
                Text(
                    title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }
            content()
        }
    }
}

@Composable
fun BigButton(text: String, onClick: () -> Unit, enabled: Boolean = true, isLoading: Boolean = false) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clickable(enabled = enabled && !isLoading) { onClick() },
        color = if (enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
    ) {
        Box(
            Modifier.padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(
                    text,
                    color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
fun MenuRow(title: String, onClick: () -> Unit, showDivider: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = 12.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(
    currentBudget: Float,
    currentCycle: String,
    currentDay: String,
    onBudgetUpdate: (Float) -> Unit,
    onCycleSettingsUpdate: (String, String) -> Unit,
    onTimelineUpdate: (LocalDate) -> Unit
) {
    var budgetValue by remember { mutableStateOf(currentBudget.toString()) }
    var selectedCycle by remember { mutableStateOf(currentCycle) }
    var selectedDay by remember { mutableStateOf(currentDay) }
    var selectedWeekOffset by remember { mutableStateOf("Starting This Week") }
    
    var cycleEx by remember { mutableStateOf(false) }
    var dayEx by remember { mutableStateOf(false) }
    var weekEx by remember { mutableStateOf(false) }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        // Section 1: Budget Limit
        PageContainer("Budget Limit") {
            Column(Modifier.padding(horizontal = 24.dp)) {
                OutlinedTextField(
                    value = budgetValue,
                    onValueChange = { budgetValue = it },
                    label = { Text("Budget Limit (£)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(24.dp))
            BigButton("Update Budget", onClick = {
                onBudgetUpdate(budgetValue.toFloatOrNull() ?: currentBudget)
            })
        }

        Spacer(Modifier.height(16.dp))

        // Section 2: Cycle & Timeline
        PageContainer("Cycle Settings") {
            Column(Modifier.padding(horizontal = 24.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    ExposedDropdownMenuBox(expanded = cycleEx, onExpandedChange = { cycleEx = !cycleEx }, Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = selectedCycle,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Cycle") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(cycleEx) },
                            modifier = Modifier.menuAnchor()
                        )
                        ExposedDropdownMenu(expanded = cycleEx, onDismissRequest = { cycleEx = false }) {
                            listOf("Weekly", "Bi-Weekly", "Monthly").forEach { opt ->
                                DropdownMenuItem(text = { Text(opt) }, onClick = { selectedCycle = opt; cycleEx = false })
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    ExposedDropdownMenuBox(expanded = dayEx, onExpandedChange = { dayEx = !dayEx }, Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = selectedDay,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Day") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(dayEx) },
                            modifier = Modifier.menuAnchor()
                        )
                        ExposedDropdownMenu(expanded = dayEx, onDismissRequest = { dayEx = false }) {
                            val days = if (selectedCycle == "Monthly") (1..28).map { it.toString() }
                            else listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
                            days.forEach { opt ->
                                DropdownMenuItem(text = { Text(opt) }, onClick = { selectedDay = opt; dayEx = false })
                            }
                        }
                    }
                }

                if (selectedCycle == "Bi-Weekly") {
                    Spacer(Modifier.height(8.dp))
                    ExposedDropdownMenuBox(expanded = weekEx, onExpandedChange = { weekEx = !weekEx }, Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedWeekOffset,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Starting Week") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(weekEx) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = weekEx, onDismissRequest = { weekEx = false }) {
                            listOf("Starting This Week", "Starting Next Week").forEach { opt ->
                                DropdownMenuItem(text = { Text(opt) }, onClick = { selectedWeekOffset = opt; weekEx = false })
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            
            BigButton("Update Cycle", onClick = {
                val today = LocalDate.now()
                try {
                    val target: LocalDate = when (selectedCycle) {
                        "Weekly" -> today.with(TemporalAdjusters.nextOrSame(DayOfWeek.valueOf(selectedDay.uppercase())))
                        "Bi-Weekly" -> {
                            val base = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.valueOf(selectedDay.uppercase())))
                            if (selectedWeekOffset == "Starting This Week") base else base.plusWeeks(1)
                        }
                        else -> {
                            val dayNum = selectedDay.toIntOrNull() ?: 1
                            val thisMonthTarget = today.withDayOfMonth(dayNum.coerceAtMost(today.lengthOfMonth()))
                            if (thisMonthTarget.isAfter(today)) {
                                thisMonthTarget
                            } else {
                                val next = today.plusMonths(1)
                                next.withDayOfMonth(dayNum.coerceAtMost(next.lengthOfMonth()))
                            }
                        }
                    }
                    onCycleSettingsUpdate(selectedCycle, selectedDay)
                    onTimelineUpdate(target)
                } catch (e: Exception) { e.printStackTrace() }
            })
        }
        Spacer(Modifier.height(100.dp)) // Extra space at bottom for scrolling
    }
}

@Composable
fun AccountPage(
    userEmail: String?,
    onSignOut: () -> Unit,
    onNavigateToSignIn: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageContainer("Account") {
            Column(Modifier.padding(horizontal = 24.dp)) {
                if (userEmail != null) {
                    Text("Logged in as:", fontSize = 14.sp)
                    Text(userEmail, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                } else {
                    Text("You are currently using the app as a guest.")
                }
            }
            Spacer(Modifier.height(24.dp))
            
            if (userEmail != null) {
                BigButton("Sign Out", onClick = onSignOut)
            } else {
                BigButton("Signin / Signup", onClick = onNavigateToSignIn)
            }

            Spacer(Modifier.height(24.dp))
            
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f)
            )

            // Personal Profile Tab
            MenuRow(
                title = "Personal Profile",
                onClick = onNavigateToProfile,
                showDivider = false
            )
        }
        
        Spacer(Modifier.height(100.dp))
    }
}

@Composable
fun ProfilePage() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("BudgetPrefs", Context.MODE_PRIVATE) }

    // Use a nullable boolean to represent the "unselected" state
    var isBusiness by remember { 
        mutableStateOf(if (prefs.contains("is_business_user")) prefs.getBoolean("is_business_user", false) else null) 
    }
    
    var bizName by remember { mutableStateOf(prefs.getString("business_name", "") ?: "") }
    var industry by remember { mutableStateOf(prefs.getString("business_industry", "") ?: "") }
    var description by remember { mutableStateOf(prefs.getString("business_description", "") ?: "") }

    val canSave = remember(isBusiness, bizName, industry, description) {
        if (isBusiness == true) {
            bizName.isNotBlank() && industry.isNotBlank() && description.isNotBlank()
        } else {
            isBusiness == false
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageContainer("Personal Profile") {
            Column(Modifier.padding(horizontal = 24.dp)) {
                Text(
                    "Complete your profile for tailored expense tracking and AI accuracy.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { isBusiness = false },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isBusiness == false) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isBusiness == false) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Personal", fontWeight = FontWeight.Bold)
                    }
                    
                    Button(
                        onClick = { isBusiness = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isBusiness == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isBusiness == true) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Business", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (isBusiness == true) {
            Spacer(Modifier.height(16.dp))
            PageContainer("Business Details") {
                Column(Modifier.padding(horizontal = 24.dp)) {
                    OutlinedTextField(
                        value = bizName,
                        onValueChange = { bizName = it },
                        label = { Text("Business Name") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = industry,
                        onValueChange = { industry = it },
                        label = { Text("Industry") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Business Description") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        BigButton(
            text = "Save Profile Details",
            enabled = canSave,
            onClick = {
                val editor = prefs.edit()
                editor.putBoolean("is_business_user", isBusiness == true)
                editor.putBoolean("profile_completed", true)
                if (isBusiness == true) {
                    editor.putString("business_name", bizName)
                    editor.putString("business_industry", industry)
                    editor.putString("business_description", description)
                } else {
                    editor.putString("business_name", "")
                    editor.putString("business_industry", "")
                    editor.putString("business_description", "")
                }
                editor.apply()
                Toast.makeText(context, "Profile saved successfully!", Toast.LENGTH_SHORT).show()
            }
        )
        
        Spacer(Modifier.height(100.dp))
    }
}

@Composable
fun BankingPage(onLinkBank: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PageContainer("Banking") {
            Text(
                "Sync your bank transactions automatically using TrueLayer.",
                fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(Modifier.height(32.dp))
            BigButton("Link Bank Account (Coming Soon)", onClick = onLinkBank, enabled = false)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CSVPage(repo: BudgetRepository, onComplete: () -> Unit) {
    val context = LocalContext.current
    
    // Export states
    var startDate by remember { mutableStateOf(LocalDate.now().minusMonths(1)) }
    var endDate by remember { mutableStateOf(LocalDate.now()) }
    var exportType by remember { mutableStateOf("Both") }
    var showStartDP by remember { mutableStateOf(false) }
    var showEndDP by remember { mutableStateOf(false) }
    var typeEx by remember { mutableStateOf(false) }

    val csvPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            // Start Background Import using WorkManager
            val workRequest = OneTimeWorkRequestBuilder<CsvImportWorker>()
                .setInputData(workDataOf("uri" to it.toString()))
                .build()
            
            WorkManager.getInstance(context).enqueue(workRequest)
            
            Toast.makeText(context, "Import started in background!", Toast.LENGTH_SHORT).show()
            onComplete()
        }
    }

    val csvExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        uri?.let {
            // Export logic (simplified for background brevity, but could also be moved to a worker if needed)
            try {
                val csvContent = buildString {
                    val isBoth = exportType == "Both"
                    append("Date,Merchant,Amount")
                    if (isBoth) append(",Type")
                    append("\n")

                    repo.categories
                        .filter { 
                            when(exportType) {
                                "Personal" -> !it.isBusiness
                                "Business" -> it.isBusiness
                                else -> true
                            }
                        }
                        .flatMap { it.transactions }
                        .filter {
                            val d = LocalDate.parse(it.date)
                            !d.isBefore(startDate) && !d.isAfter(endDate)
                        }
                        .sortedByDescending { it.date }
                        .forEach { tx ->
                            append("${tx.date},${tx.merchant.replace(",", "")},${String.format("%.2f", tx.amount)}")
                            if (isBoth) append(",${if (tx.isBusiness) "Business" else "Personal"}")
                            append("\n")
                        }
                }
                context.contentResolver.openOutputStream(it)?.use { os ->
                    os.write(csvContent.toByteArray())
                }
                Toast.makeText(context, "CSV exported successfully!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Export failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageContainer("Import Data") {
            Text(
                "Upload a CSV file of your bank statements to categorize them with AI in the background.",
                fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(Modifier.height(32.dp))
            BigButton(
                text = "Select CSV File",
                onClick = { csvPickerLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv", "text/plain")) }
            )
        }

        Spacer(Modifier.height(16.dp))

        PageContainer("Export Data") {
            Column(Modifier.padding(horizontal = 24.dp)) {
                Text("Select Range", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { showStartDP = true }) {
                        Text("From: ${startDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))}")
                    }
                    TextButton(onClick = { showEndDP = true }) {
                        Text("To: ${endDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))}")
                    }
                }

                Spacer(Modifier.height(16.dp))

                ExposedDropdownMenuBox(
                    expanded = typeEx,
                    onExpandedChange = { typeEx = !typeEx },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = exportType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Data Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeEx) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = typeEx,
                        onDismissRequest = { typeEx = false }
                    ) {
                        listOf("Both", "Personal", "Business").forEach { opt ->
                            DropdownMenuItem(
                                text = { Text(opt) },
                                onClick = { 
                                    exportType = opt
                                    typeEx = false 
                                }
                            )
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(24.dp))
            
            BigButton(
                text = "Export as CSV",
                onClick = { 
                    if (startDate.isAfter(endDate)) {
                        Toast.makeText(context, "Start date cannot be after end date", Toast.LENGTH_SHORT).show()
                        return@BigButton
                    }
                    val typeLabel = when(exportType) {
                        "Both" -> "All"
                        else -> exportType
                    }
                    val fileName = "${startDate}--${endDate}_${typeLabel}.csv"
                    csvExportLauncher.launch(fileName) 
                }
            )
        }
        
        Spacer(Modifier.height(100.dp))
    }

    if (showStartDP) {
        val state = rememberDatePickerState(
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    return utcTimeMillis <= System.currentTimeMillis()
                }
            }
        )
        DatePickerDialog(
            onDismissRequest = { showStartDP = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        startDate = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    showStartDP = false
                }) { Text("OK") }
            }
        ) { DatePicker(state) }
    }

    if (showEndDP) {
        val state = rememberDatePickerState(
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    return utcTimeMillis <= System.currentTimeMillis()
                }
            }
        )
        DatePickerDialog(
            onDismissRequest = { showEndDP = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        endDate = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    showEndDP = false
                }) { Text("OK") }
            }
        ) { DatePicker(state) }
    }
}

private fun uriToFile(context: android.content.Context, uri: Uri): File {
    val tempFile = File(context.cacheDir, "upload.csv").apply { createNewFile() }
    context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(tempFile).use { output -> input.copyTo(output) }
    }
    return tempFile
}
