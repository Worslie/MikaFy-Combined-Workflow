package com.example.aibudgettracker.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.LocalDate

// --- 2. REPOSITORY (Data Management) ---

/**
 * Handles data persistence using SharedPreferences and JSON (Gson).
 * Manages the logic for adding transactions and hiding/moving categories.
 */
class BudgetRepository private constructor(context: Context) {
    private val sharedPrefs = context.applicationContext.getSharedPreferences("BudgetPrefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    // Observable list that triggers UI updates when items are modified
    var categories = mutableStateListOf<BudgetCategory>()
        private set

    private val personalDefaults = mapOf(
        "General" to "Miscellaneous expenses.",
        "Bills" to "Recurring payments like utilities, rent, and subscriptions.",
        "Groceries" to "Supermarket and daily food essentials.",
        "Dining Out" to "Restaurants, cafes, and food delivery.",
        "Travel" to "Expenses related to trips, flights, and accommodation.",
        "Entertainment" to "Movies, games, events, and other leisure activities.",
        "Shopping" to "Clothing, electronics, and other personal items."
    )

    private val businessDefaults = mapOf(
        "General" to "General business expenses.",
        "Bills" to "Business utilities, software, and rent.",
        "Materials" to "Raw materials or stock for the business.",
        "Marketing" to "Ads, promotions, and brand development.",
        "Operations" to "General day-to-day business costs.",
        "Travel" to "Business-related trips and meetings."
    )

    companion object {
        @Volatile
        private var INSTANCE: BudgetRepository? = null

        fun getInstance(context: Context): BudgetRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BudgetRepository(context).also { INSTANCE = it }
            }
        }
    }

    init {
        loadData()
    }

    /** Serializes the current state to JSON and saves to disk */
    private fun saveData() {
        sharedPrefs.edit().putString("categories_json", gson.toJson(categories)).apply()
    }

    /** Loads data from disk; populates defaults if no saved data exists */
    private fun loadData() {
        val json = sharedPrefs.getString("categories_json", null)
        if (json != null) {
            val type = object : TypeToken<List<BudgetCategory>>() {}.type
            val saved: List<BudgetCategory> = gson.fromJson(json, type)
            categories.clear()

            // Sanitize to handle potential nulls from backward compatibility (Gson)
            categories.addAll(saved.map {
                // Cast to nullable to allow null check on field that Gson might have left null
                val desc: String? = it.categoryDescription
                if (desc == null) it.copy(categoryDescription = "") else it
            })

            // Ensure personal default categories exist
            personalDefaults.forEach { (name, desc) ->
                val existing = categories.find { it.name.equals(name, ignoreCase = true) && !it.isBusiness }
                if (existing == null) {
                    categories.add(BudgetCategory(name, categoryDescription = desc, isDefault = true, isVisible = true, isBusiness = false))
                }
            }

            // Ensure business default categories exist
            businessDefaults.forEach { (name, desc) ->
                val existing = categories.find { it.name.equals(name, ignoreCase = true) && it.isBusiness }
                if (existing == null) {
                    categories.add(BudgetCategory(name, categoryDescription = desc, isDefault = true, isVisible = true, isBusiness = true))
                }
            }
        } else {
            // Initial load for personal defaults
            personalDefaults.forEach { (name, desc) ->
                categories.add(BudgetCategory(name, categoryDescription = desc, isDefault = true, isVisible = true, isBusiness = false))
            }
            // Initial load for business defaults
            businessDefaults.forEach { (name, desc) ->
                categories.add(BudgetCategory(name, categoryDescription = desc, isDefault = true, isVisible = true, isBusiness = true))
            }
            saveData()
        }
    }

    /** * Hides a category and moves its transactions to "General".
     * Restores them if toggled back to visible.
     */
    fun toggleVisibility(name: String, isBusiness: Boolean) {
        if (name == "General") return
        val idx = categories.indexOfFirst { it.name == name && it.isBusiness == isBusiness }
        val genIdx = categories.indexOfFirst { it.name == "General" && it.isBusiness == isBusiness }
        if (idx == -1 || genIdx == -1) return

        val category = categories[idx]
        if (category.isVisible) {
            // Move to General
            val movedTxs = category.transactions.map {
                it.copy(
                    categoryName = "General",
                    originalCategory = category.name
                )
            }
            categories[genIdx] = categories[genIdx].copy(
                transactions = (categories[genIdx].transactions + movedTxs).sortedByDescending { it.date },
                totalSpent = categories[genIdx].totalSpent + category.totalSpent
            )
            categories[idx] =
                category.copy(isVisible = false, transactions = emptyList(), totalSpent = 0f)
        } else {
            // Pull back from General
            val genTxs = categories[genIdx].transactions.toMutableList()
            val pulled = genTxs.filter { it.originalCategory == name }
            genTxs.removeAll { it.originalCategory == name }
            val totalPulled = pulled.sumOf { it.amount.toDouble() }.toFloat()
            categories[genIdx] = categories[genIdx].copy(
                transactions = genTxs,
                totalSpent = (categories[genIdx].totalSpent - totalPulled).coerceAtLeast(0f)
            )
            categories[idx] = category.copy(
                isVisible = true,
                transactions = pulled.map {
                    it.copy(
                        categoryName = name,
                        originalCategory = null
                    )
                },
                totalSpent = totalPulled
            )
        }
        saveData()
    }

    fun addCategory(name: String, description: String = "", isBusiness: Boolean) {
        if (categories.none { it.name.equals(name, ignoreCase = true) && it.isBusiness == isBusiness }) {
            categories.add(BudgetCategory(name, categoryDescription = description, isVisible = true, isBusiness = isBusiness))
            saveData()
        }
    }

    /** Deletes custom categories and moves orphans to General */
    fun deleteCategory(name: String, isBusiness: Boolean) {
        val idx = categories.indexOfFirst { it.name == name && it.isBusiness == isBusiness }
        if (idx == -1) return
        val cat = categories[idx]
        if (cat.isDefault) return

        val genIdx = categories.indexOfFirst { it.name == "General" && it.isBusiness == isBusiness }
        if (genIdx != -1) {
            val movedTxs = cat.transactions.map {
                it.copy(categoryName = "General", originalCategory = null)
            }
            categories[genIdx] = categories[genIdx].copy(
                transactions = (categories[genIdx].transactions + movedTxs).sortedByDescending { it.date },
                totalSpent = categories[genIdx].totalSpent + cat.totalSpent
            )
        }
        categories.removeAt(idx)
        saveData()
    }

    fun addTransaction(
        merchant: String,
        amount: Float,
        category: String,
        description: String,
        date: LocalDate?,
        originalCategory: String? = null,
        isBusiness: Boolean = false
    ) {
        val dateStr = (date ?: LocalDate.now()).toString()
        val target = category
        
        // Find category case-insensitively and matching the business/personal type
        val actualCategory = categories.find { it.name.equals(target, ignoreCase = true) && it.isBusiness == isBusiness }?.name ?: target
        
        // Ensure expense is negative
        val finalAmount = if (amount > 0) -amount else amount
        
        val tx = Transaction(merchant, finalAmount, dateStr, actualCategory, description, originalCategory, isBusiness)
        val idx = categories.indexOfFirst { it.name == actualCategory && it.isBusiness == isBusiness }
        if (idx != -1) {
            // totalSpent should increase when finalAmount is negative.
            categories[idx] = categories[idx].copy(
                transactions = (categories[idx].transactions + tx).sortedByDescending { it.date },
                totalSpent = categories[idx].totalSpent - finalAmount
            )
            saveData()
        }
    }

    fun removeTransaction(c: String, tx: Transaction) {
        val idx = categories.indexOfFirst { it.name == c && it.isBusiness == tx.isBusiness }
        if (idx != -1) {
            val newList = categories[idx].transactions.toMutableList().apply { remove(tx) }
            // Reversing the add logic: totalSpent - (-amount) = totalSpent + amount
            categories[idx] = categories[idx].copy(
                transactions = newList,
                totalSpent = (categories[idx].totalSpent + tx.amount).coerceAtLeast(0f)
            )
            saveData()
        }
    }

    fun moveTransaction(tx: Transaction, fromCat: String, toCat: String, toBusiness: Boolean) {
        removeTransaction(fromCat, tx)
        addTransaction(
            merchant = tx.merchant,
            amount = tx.amount, // addTransaction handles making it negative
            category = toCat,
            description = tx.description,
            date = LocalDate.parse(tx.date),
            originalCategory = tx.originalCategory,
            isBusiness = toBusiness
        )
    }

    /**
     * Updates the business/personal status of a transaction.
     */
    fun updateTransactionType(catName: String, tx: Transaction, isBusiness: Boolean) {
        moveTransaction(tx, catName, catName, isBusiness)
    }

    // ----- Start of demo code -----
    /**
     * Populates the repository with a variety of personal and business transactions
     * for demonstration purposes.
     */
    fun loadDemoData() {
        // Personal Demo Transactions - Use negative for spending to match backend logic
        addTransaction("Tesco", -42.50f, "Groceries", "Weekly shop", LocalDate.now().minusDays(1), isBusiness = false)
        addTransaction("Netflix", -10.99f, "Bills", "Monthly sub", LocalDate.now().minusDays(3), isBusiness = false)
        addTransaction("Starbucks", -5.40f, "Dining Out", "Morning coffee", LocalDate.now(), isBusiness = false)
        addTransaction("Shell", -55.00f, "Travel", "Fuel", LocalDate.now().minusDays(2), isBusiness = false)
        addTransaction("Apple Store", -29.00f, "Shopping", "Phone case", LocalDate.now().minusDays(5), isBusiness = false)
        addTransaction("Cinema City", -15.00f, "Entertainment", "Movie night", LocalDate.now().minusDays(4), isBusiness = false)
        addTransaction("Gym Membership", -35.00f, "Bills", "Monthly fee", LocalDate.now().minusDays(7), isBusiness = false)

        // Business Demo Transactions
        addTransaction("Amazon AWS", -125.00f, "Bills", "Server hosting", LocalDate.now().minusDays(1), isBusiness = true)
        addTransaction("Local Wholesaler", -450.00f, "Materials", "Stock order", LocalDate.now().minusDays(4), isBusiness = true)
        addTransaction("Facebook Ads", -75.00f, "Marketing", "Promo campaign", LocalDate.now().minusDays(2), isBusiness = true)
        addTransaction("Office Depot", -35.00f, "Operations", "Stationery", LocalDate.now().minusDays(6), isBusiness = true)
        addTransaction("Virgin Trains", -89.00f, "Travel", "Client meeting", LocalDate.now().minusDays(3), isBusiness = true)
        
        saveData()
    }
    // ----- End of demo code -----
}
