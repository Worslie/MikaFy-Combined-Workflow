package com.example.aibudgettracker.data

import com.google.gson.annotations.SerializedName

// --- 1. DATA MODELS ---

/**
 * Represents a grouping of spending (e.g., "Groceries").
 * totalSpent is a calculated value based on filtered transactions.
 */
data class BudgetCategory(
    val name: String,
    val totalSpent: Float = 0f,
    val transactions: List<Transaction> = emptyList(),
    val categoryDescription: String = "",
    val isDefault: Boolean = false,
    val isVisible: Boolean = true,
    val isBusiness: Boolean = false // Added to distinguish between personal and business categories
)

/**
 * Represents an individual expense.
 * originalCategory helps track where a transaction came from if its parent category is hidden.
 */
data class Transaction(
    val merchant: String,
    val amount: Float,
    val date: String,
    @SerializedName("category_name")
    val categoryName: String,
    val description: String = "",
    @SerializedName("original_category")
    val originalCategory: String? = null,
    @SerializedName("is_business")
    val isBusiness: Boolean = false
) {
    /**
     * Ensures the amount is negative (spending).
     * Useful when the server or CSV provides absolute values for expenses.
     */
    fun asExpense(): Transaction = if (amount > 0) copy(amount = -amount) else this
}

/** Defines the two modes for the Bottom Sheet overlay */
enum class SheetMode { ADD_TRANSACTION, ADD_CATEGORY }
