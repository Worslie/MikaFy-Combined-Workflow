package com.example.aibudgettracker

// Cursor iteration 1.2
// Custom packages
import com.example.aibudgettracker.data.*
import com.example.aibudgettracker.network.*
import com.example.aibudgettracker.network.banking.*
import com.example.aibudgettracker.ui.components.*
import com.example.aibudgettracker.ui.screens.*
import com.example.aibudgettracker.ui.theme.AIBudgetTrackerTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aibudgettracker.network.userAuth.AuthRepository
import com.example.aibudgettracker.network.userAuth.launchGoogleSignIn
import com.example.aibudgettracker.network.userAuth.onGoogleSignIn
import com.example.aibudgettracker.network.userAuth.supabase
import kotlinx.coroutines.launch
import java.time.*
import java.time.temporal.ChronoUnit
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import java.time.temporal.TemporalAdjusters

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val authRepo = AuthRepository(supabase, this)
        val repo = BudgetRepository.getInstance(this)
        val prefs = getSharedPreferences("BudgetPrefs", Context.MODE_PRIVATE)

        setContent {
            // Initialize view from saved preference
            var isBusinessView by remember { 
                mutableStateOf(prefs.getBoolean("is_business_user", false)) 
            }
            
            AIBudgetTrackerTheme(isBusiness = isBusinessView) {
                var budgetTotal by remember {
                    mutableFloatStateOf(prefs.getFloat("total_limit", 1000f))
                }
                var targetDate by remember {
                    mutableStateOf(
                        LocalDate.parse(
                            prefs.getString("target_date", LocalDate.now().with(TemporalAdjusters.firstDayOfNextMonth()).toString())
                        )
                    )
                }
                var cycleType by remember {
                    mutableStateOf(prefs.getString("cycle_type", "Monthly") ?: "Monthly")
                }
                var startDay by remember {
                    mutableStateOf(prefs.getString("start_day", "1") ?: "1")
                }

                LaunchedEffect(cycleType, startDay) {
                    val today = LocalDate.now()
                    
                    val nextOccurrence = when (cycleType) {
                        "Monthly" -> {
                            val day = startDay.toIntOrNull() ?: 1
                            var target = try {
                                today.withDayOfMonth(day)
                            } catch (e: Exception) {
                                today.with(TemporalAdjusters.lastDayOfMonth())
                            }
                            
                            // If today IS the reset day or after it, the NEXT one is next month.
                            // If today is BEFORE the reset day, the NEXT one is this month.
                            if (!target.isAfter(today)) {
                                target = try {
                                    today.plusMonths(1).withDayOfMonth(day)
                                } catch (e: Exception) {
                                    today.plusMonths(1).with(TemporalAdjusters.lastDayOfMonth())
                                }
                            }
                            target
                        }
                        "Bi-Weekly" -> {
                            val anchorDateStr = prefs.getString("anchor_date", null)
                            val anchorDate = if (anchorDateStr == null) {
                                today.also { prefs.edit().putString("anchor_date", it.toString()).apply() }
                            } else {
                                LocalDate.parse(anchorDateStr)
                            }
                            val daysBetween = ChronoUnit.DAYS.between(anchorDate, today)
                            val cyclesPassed = daysBetween / 14
                            var next = anchorDate.plusDays(cyclesPassed * 14)
                            if (!next.isAfter(today)) next = next.plusDays(14)
                            next
                        }
                        else -> { // Weekly
                            val anchorDateStr = prefs.getString("anchor_date", null)
                            val anchorDate = if (anchorDateStr == null) {
                                today.also { prefs.edit().putString("anchor_date", it.toString()).apply() }
                            } else {
                                LocalDate.parse(anchorDateStr)
                            }
                            val weeksBetween = ChronoUnit.WEEKS.between(anchorDate, today)
                            var next = anchorDate.plusWeeks(weeksBetween)
                            if (!next.isAfter(today)) next = next.plusWeeks(1)
                            next
                        }
                    }

                    if (targetDate != nextOccurrence) {
                        targetDate = nextOccurrence
                        prefs.edit().putString("target_date", nextOccurrence.toString()).apply()
                    }
                }

                MainBuild(
                    authRepo = authRepo,
                    repository = repo,
                    budgetTotal = budgetTotal,
                    targetDate = targetDate,
                    currentCycle = cycleType,
                    currentDay = startDay,
                    isBusinessView = isBusinessView,
                    onBusinessViewChange = { newValue ->
                        isBusinessView = newValue
                        prefs.edit().putBoolean("is_business_user", newValue).apply()
                    },
                    onSave = { newLimit, newDate ->
                        budgetTotal = newLimit
                        targetDate = newDate
                        prefs.edit()
                            .putFloat("total_limit", newLimit)
                            .putString("target_date", newDate.toString())
                            .apply()
                    },
                    onUpdateCycleSettings = { newCycle, newDay ->
                        cycleType = newCycle
                        startDay = newDay
                        prefs.edit()
                            .putString("cycle_type", newCycle)
                            .putString("start_day", newDay)
                            .apply()
                    }
                )
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainBuild(
        authRepo: AuthRepository,
        repository: BudgetRepository,
        budgetTotal: Float,
        targetDate: LocalDate,
        currentCycle: String,
        currentDay: String,
        isBusinessView: Boolean,
        onBusinessViewChange: (Boolean) -> Unit,
        onSave: (Float, LocalDate) -> Unit,
        onUpdateCycleSettings: (String, String) -> Unit
    ) {
        var selectedTab by remember { mutableIntStateOf(0) }
        var activeCategory by remember { mutableStateOf<BudgetCategory?>(null) }
        var activeDateRange by remember { mutableStateOf<Pair<LocalDate, LocalDate>?>(null) }
        var isEditMode by remember { mutableStateOf(false) }
        var showSheet by remember { mutableStateOf(false) }
        var sheetMode by remember { mutableStateOf(SheetMode.ADD_TRANSACTION) }

        var mIn by remember { mutableStateOf("") }
        var aIn by remember { mutableStateOf("") }
        var dIn by remember { mutableStateOf("") }
        var selCatForTx by remember { mutableStateOf("Auto (AI)") }
        var isDropEx by remember { mutableStateOf(false) }
        var selDate by remember { mutableStateOf<LocalDate?>(null) }
        var showDP by remember { mutableStateOf(false) }
        var isBusinessTx by remember { mutableStateOf(false) }
        
        val scope = rememberCoroutineScope()
        var isProcessingAI by remember { mutableStateOf(false) }
        val days = ChronoUnit.DAYS.between(LocalDate.now(), targetDate).toInt().coerceAtLeast(0)

        // TEST CODE: START
        var showSignIn by remember { mutableStateOf(false) }
        val context = LocalContext.current

        // Add this near your other 'remember' states in MainBuild
        var userEmail by remember { mutableStateOf<String?>(null) }
        var activeEditMenu by remember { mutableStateOf<EditMenu?>(null) }
        
        var showPinScreen by remember { mutableStateOf(false) }
        var pinMode by remember { mutableStateOf(PinMode.LOGIN) }
        var pinError by remember { mutableStateOf<String?>(null) }
        var fullErrorDialogMessage by remember { mutableStateOf<String?>(null) }
        var isBackendAuthenticated by remember { mutableStateOf(authRepo.hasBackendSession()) }

        // --- EFFECT: AUTH LISTENER ---
        LaunchedEffect(Unit) {
            userEmail = authRepo.getUserEmail()
            supabase.auth.sessionStatus.collect { status ->
                if (status is SessionStatus.Authenticated) {
                    userEmail = status.session.user?.email
                        authRepo.syncAuthTokenFromSession()
                    // If Supabase is authenticated but backend isn't, we might need to re-login to backend
                    // Or if we have a backend session, we might need a PIN
                    if (authRepo.hasBackendSession() && authRepo.isPinRequired()) {
                        pinMode = PinMode.LOGIN
                        showPinScreen = true
                    }
                } else if (status is SessionStatus.NotAuthenticated) {
                    userEmail = null
                    isBackendAuthenticated = false
                }
            }
        }

        // --- EFFECT: SYNC BUSINESS VIEW ---
        LaunchedEffect(selectedTab) {
            if (selectedTab == 0) {
                val prefs = context.getSharedPreferences("BudgetPrefs", Context.MODE_PRIVATE)
                val savedBiz = prefs.getBoolean("is_business_user", isBusinessView)
                if (savedBiz != isBusinessView) {
                    onBusinessViewChange(savedBiz)
                }
            }
        }

        fun processAITransaction(m: String, a: Float, d: String, dt: LocalDate?, isBusiness: Boolean) {
            isProcessingAI = true
            scope.launch {
                val tempTx = Transaction(m, a, (dt ?: LocalDate.now()).toString(), "", d, isBusiness = isBusiness)
                
                // Fetch business details from SharedPreferences
                val prefs = context.getSharedPreferences("BudgetPrefs", Context.MODE_PRIVATE)
                val bizName = prefs.getString("business_name", null)
                val bizIndustry = prefs.getString("business_industry", null)
                val bizDesc = prefs.getString("business_description", null)

                // Get available categories for the correct context (business vs personal)
                val availableCats = repository.categories
                    .filter { it.isBusiness == isBusiness }
                    .map { it.name }

                val result = NetworkClient.safeAnalyzeTransaction(
                    transaction = tempTx,
                    availableCategories = availableCats,
                    businessName = bizName,
                    businessIndustry = bizIndustry,
                    businessDescription = bizDesc
                )

                var finalCategory = "General"
                
                if (result is AnalysisResult.Success) {
                    val predictedCategory = result.response.transactions?.firstOrNull()?.categoryName

                    // --- TEST CODE: START ---
                    Log.d("AI_DEBUG", "Server returned category: '$predictedCategory'")
                    Toast.makeText(context, "AI Result: '$predictedCategory'", Toast.LENGTH_SHORT).show()
                    // --- TEST CODE: END ---

                    if (!predictedCategory.isNullOrBlank()) {
                        finalCategory = predictedCategory
                    } else {
                        Toast.makeText(context, "AI couldn't categorize this. Filed under 'General'.", Toast.LENGTH_LONG).show()
                    }
                } else {
                    val errorMsg = (result as? AnalysisResult.Error)?.message ?: "Unknown Error"
                    Toast.makeText(context, "AI Analysis Failed: $errorMsg. Filed under 'General'.", Toast.LENGTH_LONG).show()
                }

                if (repository.categories.none { it.name.equals(finalCategory, ignoreCase = true) && it.isBusiness == isBusiness }) {
                    repository.addCategory(finalCategory, "AI Generated Category", isBusiness)
                }

                repository.addTransaction(
                    merchant = m,
                    amount = a,
                    category = finalCategory,
                    description = d,
                    date = dt,
                    isBusiness = isBusiness
                )
                isProcessingAI = false
                showSheet = false
            }
        }

        Scaffold(
            topBar = {
                if (!showSignIn && !showPinScreen) {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                if (activeCategory != null) activeCategory!!.name else "MikaFy",
                                fontWeight = FontWeight.Bold,
                                fontSize = 26.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            scrolledContainerColor = MaterialTheme.colorScheme.background
                        ),
                        navigationIcon = {
                            if (selectedTab == 0 && activeCategory == null) {
                                TextButton(onClick = { onBusinessViewChange(!isBusinessView) }) {
                                    Text(
                                        text = if (isBusinessView) "Business" else "Personal",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else if (activeCategory != null) {
                                IconButton(onClick = { isEditMode = false; activeCategory = null; activeDateRange = null }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        },
                        actions = {
                            if (selectedTab == 0 && activeCategory == null) {
                                TextButton(onClick = { isEditMode = !isEditMode }) { 
                                    Text(if (isEditMode) "Done" else "Edit", fontWeight = FontWeight.Bold) 
                                }
                            } else if (activeCategory != null) {
                                TextButton(onClick = { isEditMode = !isEditMode }) { 
                                    Text(if (isEditMode) "Done" else "Edit", fontWeight = FontWeight.Bold) 
                                }
                            }
                        }
                    )
                }
            },
            bottomBar = {
                if (!showSignIn && !showPinScreen) {
                    MyBottomNavigation(selectedTab) {
                        isEditMode = false; selectedTab = it; activeCategory = null; activeDateRange = null
                    }
                }
            },
            floatingActionButton = {
                if (!showSignIn && !showPinScreen && selectedTab == 0 && activeCategory == null) {
                    FloatingActionButton(onClick = {
                        mIn = ""; aIn = ""; dIn = ""; selCatForTx = "Auto (AI)"; selDate = null; isBusinessTx = isBusinessView;
                        sheetMode = SheetMode.ADD_TRANSACTION; showSheet = true
                    }) { Text("+", fontSize = 24.sp) }
                }
            }
        ) { p ->
            Box(Modifier.padding(p)) {
                // TEST CODE: START
                if (showSignIn) {
                    NoSigninScreen(
                        onGoogleSignIn = {
                            scope.launch {
                                val result = launchGoogleSignIn(context)
                                result.onSuccess { idToken ->
                                    // Start both Supabase and Backend login in parallel to reduce latency
                                    launch { onGoogleSignIn(idToken) }

                                    val backendResult = authRepo.loginToBackend(idToken)
                                    backendResult.onSuccess { hasPin ->
                                        if (hasPin) {
                                            pinMode = PinMode.LOGIN
                                        } else {
                                            pinMode = PinMode.SETUP_ENTER
                                        }
                                        showPinScreen = true
                                        showSignIn = false
                                    }.onFailure { error ->
                                        val fullError = error.message ?: "Unknown backend error"
                                        Log.e("MainActivity", "Backend login failed: $fullError")
                                        fullErrorDialogMessage = fullError
                                    }
                                }.onFailure { error ->
                                    Log.e("MainActivity", "Google Sign-In failed", error)
                                    Toast.makeText(context, "Google Sign-In failed: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        onEmailSignIn = {
                            Toast.makeText(context, "Email sign-in coming soon!", Toast.LENGTH_SHORT).show()
                        },
                        isLoggedIn = false, // Controlled manually now
                        onComplete = { isBusiness, bizName, industry, description ->
                            // This path is now mostly handled via the Google SignIn success block
                        }
                    )
                } else if (showPinScreen) {
                    PinScreen(
                        mode = pinMode,
                        errorMessage = pinError,
                        onPinComplete = { pin ->
                            scope.launch {
                                if (pinMode == PinMode.LOGIN) {
                                    authRepo.loginWithPin(pin).onSuccess {
                                        showPinScreen = false
                                        isBackendAuthenticated = true
                                    }.onFailure { error ->
                                        pinError = "Invalid PIN"
                                        fullErrorDialogMessage = error.message
                                    }
                                } else {
                                    authRepo.setupPin(userEmail ?: "", pin).onSuccess {
                                        showPinScreen = false
                                        isBackendAuthenticated = true
                                    }.onFailure { error ->
                                        pinError = "Failed to set PIN"
                                        fullErrorDialogMessage = error.message
                                    }
                                }
                            }
                        }
                    )
                } else if (!isBackendAuthenticated && userEmail == null) {
                    LaunchedEffect(Unit) { showSignIn = true }
                } else if (activeCategory != null && activeDateRange != null) {
                    TransactionDetailScreen(
                        catName = activeCategory!!.name,
                        repo = repository,
                        startDate = activeDateRange!!.first,
                        endDate = activeDateRange!!.second,
                        isBusinessView = isBusinessView,
                        globalEdit = isEditMode
                    ) { isEditMode = false; activeCategory = null; activeDateRange = null }
                } else {
                    when (selectedTab) {
                        0 -> BudgetBuild(
                            repo = repository,
                            total = budgetTotal,
                            days = days,
                            isEdit = isEditMode,
                            currentCycle = currentCycle,
                            isBusinessView = isBusinessView,
                            onSel = { cat, start, end ->
                                isEditMode = false
                                activeCategory = cat
                                activeDateRange = start to end
                            },
                            onAdd = { sheetMode = SheetMode.ADD_CATEGORY; showSheet = true },
                            onNavigateToProfile = { 
                                selectedTab = 1
                                activeEditMenu = EditMenu.PROFILE 
                            }
                        )
                        1 -> EditBuild(
                            repo = repository,
                            currentBudget = budgetTotal,
                            currentTarget = targetDate,
                            currentCycle = currentCycle,
                            currentDay = currentDay,
                            userEmail = userEmail,
                            onSignOut = {
                                scope.launch {
                                    authRepo.signOut()
                                }
                            },
                            onBudgetUpdate = { newB -> onSave(newB, targetDate) },
                            onTimelineUpdate = { newD -> onSave(budgetTotal, newD) },
                            onCycleSettingsUpdate = onUpdateCycleSettings,
                            onComplete = { selectedTab = 0 },
                            onNavigateToSignIn = { showSignIn = true },
                            onLinkBank = { /* Banking features commented out */ },
                            initialMenu = activeEditMenu,
                            onMenuChange = { activeEditMenu = it }
                        )
                    }
                }

                if (fullErrorDialogMessage != null) {
                    AlertDialog(
                        onDismissRequest = { fullErrorDialogMessage = null },
                        title = { Text("Error Detail") },
                        text = { 
                            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                Text(fullErrorDialogMessage ?: "Unknown error") 
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { fullErrorDialogMessage = null }) {
                                Text("Dismiss")
                            }
                        }
                    )
                }

                if (showSheet) {
                    ModalBottomSheet(onDismissRequest = { if(!isProcessingAI) showSheet = false }) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
                            if (sheetMode == SheetMode.ADD_TRANSACTION) {
                                Text("New Transaction", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                ) {
                                    Text("Personal")
                                    Switch(
                                        checked = isBusinessTx,
                                        onCheckedChange = { isBusinessTx = it },
                                        modifier = Modifier.padding(horizontal = 12.dp)
                                    )
                                    Text("Business")
                                }

                                ExposedDropdownMenuBox(
                                    expanded = isDropEx,
                                    onExpandedChange = { isDropEx = !isDropEx }) {
                                    OutlinedTextField(
                                        value = selCatForTx,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Category") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(isDropEx) },
                                        modifier = Modifier.menuAnchor().fillMaxWidth()
                                    )
                                    ExposedDropdownMenu(
                                        expanded = isDropEx,
                                        onDismissRequest = { isDropEx = false }) {
                                        DropdownMenuItem(
                                            text = { Text("Auto (AI)") },
                                            onClick = { selCatForTx = "Auto (AI)"; isDropEx = false })
                                        repository.categories.filter { it.isVisible && it.isBusiness == isBusinessTx }.forEach {
                                            DropdownMenuItem(
                                                text = { Text(it.name) },
                                                onClick = { selCatForTx = it.name; isDropEx = false })
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = mIn,
                                    onValueChange = { mIn = it },
                                    label = { Text("Merchant") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = aIn,
                                    onValueChange = { aIn = it },
                                    label = { Text("Amount (£)") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = dIn,
                                    onValueChange = { dIn = it },
                                    label = { Text("Description (Optional)") },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { showDP = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text(selDate?.toString() ?: "Date: Today") }
                                Button(
                                    enabled = !isProcessingAI,
                                    onClick = {
                                        val amountVal = aIn.toFloatOrNull() ?: 0f
                                        if (mIn.isNotBlank() && amountVal > 0f) {
                                            if (selCatForTx == "Auto (AI)") {
                                                processAITransaction(mIn, amountVal, dIn, selDate, isBusinessTx)
                                            } else {
                                                repository.addTransaction(
                                                    merchant = mIn,
                                                    amount = amountVal,
                                                    category = selCatForTx,
                                                    description = dIn,
                                                    date = selDate,
                                                    isBusiness = isBusinessTx
                                                )
                                                showSheet = false
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                ) { 
                                    if (isProcessingAI) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                                    } else {
                                        Text("Save Transaction") 
                                    }
                                }
                            } else {
                                var n by remember { mutableStateOf("") }
                                var d by remember { mutableStateOf("") }
                                Text("New Category", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                OutlinedTextField(
                                    value = n,
                                    onValueChange = { n = it },
                                    label = { Text("Category Name") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = d,
                                    onValueChange = { d = it },
                                    label = { Text("Description") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Button(
                                    onClick = {
                                        if (n.isNotBlank()) {
                                            repository.addCategory(n, d, isBusinessView); showSheet = false
                                        }
                                    },
                                    Modifier.fillMaxWidth().padding(top = 16.dp)
                                ) { Text("Create Category") }
                            }
                        }
                    }
                }

                if (showDP) {
                    val state = rememberDatePickerState()
                    DatePickerDialog(
                        onDismissRequest = { showDP = false },
                        confirmButton = {
                            TextButton(onClick = {
                                selDate = state.selectedDateMillis?.let {
                                    Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                                }
                                showDP = false
                            }) { Text("OK") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDP = false }) { Text("Cancel") }
                        }
                    ) {
                        DatePicker(state = state)
                    }
                }
            }
        }
    }
}
