package com.example.aibudgettracker.network

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.aibudgettracker.data.BudgetRepository
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class CsvImportWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val uriString = inputData.getString("uri") ?: return Result.failure()
        val uri = Uri.parse(uriString)
        
        return try {
            // Use singleton instance to ensure UI reflects changes immediately
            val repo = BudgetRepository.getInstance(applicationContext)
            val file = uriToFile(applicationContext, uri)
            
            val filePart = MultipartBody.Part.createFormData(
                "file", 
                file.name, 
                file.asRequestBody("text/csv".toMediaTypeOrNull())
            )
            
            val bizCats = repo.categories.filter { it.isBusiness }.map { it.name }
            val persCats = repo.categories.filter { !it.isBusiness }.map { it.name }
            val gson = Gson()
            val bizReq = gson.toJson(bizCats).toRequestBody("text/plain".toMediaTypeOrNull())
            val persReq = gson.toJson(persCats).toRequestBody("text/plain".toMediaTypeOrNull())

            // Get business details from SharedPreferences
            val prefs = applicationContext.getSharedPreferences("BudgetPrefs", Context.MODE_PRIVATE)
            val bizName = prefs.getString("business_name", null)?.toRequestBody("text/plain".toMediaTypeOrNull())
            val bizIndustry = prefs.getString("business_industry", null)?.toRequestBody("text/plain".toMediaTypeOrNull())
            val bizDesc = prefs.getString("business_description", null)?.toRequestBody("text/plain".toMediaTypeOrNull())

            val response = NetworkClient.uploadCsv(filePart, bizReq, persReq, bizName, bizIndustry, bizDesc)
            
            if (response != null && response.transactions != null) {
                Log.d("CsvImportWorker", "Received ${response.transactions.size} transactions from server")
                val dmyFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
                
                // Use the Main dispatcher to modify the repository, as it is tied to Compose UI
                withContext(Dispatchers.Main) {
                    var personalCount = 0
                    var businessCount = 0
                    
                    response.transactions.forEach { tx ->
                        if (tx.isBusiness) businessCount++ else personalCount++
                        
                        val parsedDate = try {
                            if (tx.date.contains("/")) LocalDate.parse(tx.date, dmyFormatter)
                            else LocalDate.parse(tx.date)
                        } catch (e: Exception) { 
                            Log.w("CsvImportWorker", "Failed to parse date: ${tx.date}, using today")
                            LocalDate.now() 
                        }
                        
                        // 1. Ensure the category exists in the repo
                        if (repo.categories.none { it.name.equals(tx.categoryName, ignoreCase = true) && it.isBusiness == tx.isBusiness }) {
                            Log.d("CsvImportWorker", "Adding new category: ${tx.categoryName} (isBusiness: ${tx.isBusiness})")
                            repo.addCategory(tx.categoryName, "Imported Category", tx.isBusiness)
                        }

                        // 2. Add the transaction
                        repo.addTransaction(
                            merchant = tx.merchant, 
                            amount = tx.amount.toFloat(),
                            category = tx.categoryName, 
                            description = tx.description, 
                            date = parsedDate, 
                            originalCategory = tx.originalCategory, 
                            isBusiness = tx.isBusiness
                        )
                    }
                    
                    Log.d("CsvImportWorker", "Successfully processed all transactions: $personalCount personal, $businessCount business")
                    Toast.makeText(applicationContext, "Import Complete: $personalCount personal, $businessCount business added.", Toast.LENGTH_LONG).show()
                }
                
                Result.success()
            } else {
                val errorMsg = response?.message ?: "Unknown server error"
                Log.e("CsvImportWorker", "Response error: $errorMsg")
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "Import Failed: $errorMsg", Toast.LENGTH_LONG).show()
                }
                Result.failure()
            }
        } catch (e: Exception) {
            Log.e("CsvImportWorker", "Error importing CSV: ${e.localizedMessage}")
            e.printStackTrace()
            
            withContext(Dispatchers.Main) {
                Toast.makeText(applicationContext, "Import Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
            Result.failure()
        }
    }

    private fun uriToFile(context: Context, uri: Uri): File {
        val tempFile = File(context.cacheDir, "upload_bg_${System.currentTimeMillis()}.csv").apply { createNewFile() }
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
        }
        return tempFile
    }
}
