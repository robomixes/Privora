package com.privateai.camera.security

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

// ===== Data Models =====

data class Expense(
    val id: String = UUID.randomUUID().toString(),
    val amount: Double,
    val currency: String = "USD",
    val category: String,
    val description: String,
    val date: Long = System.currentTimeMillis(),
    val receiptPhotoId: String? = null
)

data class HealthProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val icon: String = "👤", // emoji
    val createdAt: Long = System.currentTimeMillis()
)

data class HealthEntry(
    val id: String = UUID.randomUUID().toString(),
    val profileId: String = "self", // "self" or profile ID
    val date: Long = System.currentTimeMillis(),
    val weight: Float? = null,
    val sleepHours: Float? = null,
    val painLevel: Int? = null,
    val mood: Int? = null, // 1-5
    val temperature: Float? = null, // °C or °F
    val steps: Int? = null,
    val heartRate: Int? = null, // bpm
    val systolic: Int? = null, // blood pressure
    val diastolic: Int? = null,
    val notes: String? = null
)

data class Habit(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val icon: String = "✅",
    val color: Int = 0xFF4CAF50.toInt(),
    val createdAt: Long = System.currentTimeMillis()
)

data class HabitLog(
    val date: String, // "2026-04-03"
    val completed: Set<String> // habit IDs
)

val EXPENSE_CATEGORIES = listOf("Food", "Transport", "Shopping", "Bills", "Health", "Entertainment", "Education", "Other")
val MOOD_EMOJIS = listOf("😞", "😕", "😐", "🙂", "😀")

/**
 * Encrypted storage for Insights data (expenses, health, habits).
 * Same encryption as vault — AES-256-GCM.
 */
class InsightsRepository(private val baseDir: File, private val crypto: CryptoManager) {

    companion object {
        private const val TAG = "InsightsRepository"
    }

    private val expensesDir = File(baseDir, "expenses").also { it.mkdirs() }
    private val healthDir = File(baseDir, "health").also { it.mkdirs() }
    private val habitsDir = File(baseDir, "habits").also { it.mkdirs() }

    // ===== Expenses =====

    fun saveExpense(expense: Expense) {
        val json = JSONObject().apply {
            put("id", expense.id); put("amount", expense.amount); put("currency", expense.currency)
            put("category", expense.category); put("description", expense.description)
            put("date", expense.date); put("receiptPhotoId", expense.receiptPhotoId ?: "")
        }.toString()
        crypto.encryptToFile(json.toByteArray(Charsets.UTF_8), File(expensesDir, "${expense.id}.expense.enc"))
    }

    fun listExpenses(): List<Expense> {
        return (expensesDir.listFiles() ?: emptyArray())
            .filter { it.name.endsWith(".expense.enc") }
            .mapNotNull { file ->
                try {
                    val json = String(crypto.decryptFile(file), Charsets.UTF_8)
                    val obj = JSONObject(json)
                    Expense(
                        id = obj.getString("id"), amount = obj.getDouble("amount"),
                        currency = obj.optString("currency", "USD"),
                        category = obj.getString("category"), description = obj.getString("description"),
                        date = obj.getLong("date"),
                        receiptPhotoId = obj.optString("receiptPhotoId", "").ifEmpty { null }
                    )
                } catch (e: Exception) { Log.e(TAG, "Failed to load expense: ${e.message}"); null }
            }
            .sortedByDescending { it.date }
    }

    fun deleteExpense(id: String) {
        File(expensesDir, "$id.expense.enc").delete()
    }

    fun getMonthlyTotal(year: Int, month: Int): Double {
        val fmt = SimpleDateFormat("yyyy-MM", Locale.US)
        val target = "$year-${"%02d".format(month)}"
        return listExpenses().filter { fmt.format(Date(it.date)) == target }.sumOf { it.amount }
    }

    fun getCategoryTotals(): Map<String, Double> {
        val expenses = listExpenses()
        return EXPENSE_CATEGORIES.associateWith { cat ->
            expenses.filter { it.category == cat }.sumOf { it.amount }
        }.filter { it.value > 0 }
    }

    // ===== Health =====

    fun saveHealthEntry(entry: HealthEntry) {
        val json = JSONObject().apply {
            put("id", entry.id); put("profileId", entry.profileId); put("date", entry.date)
            if (entry.weight != null) put("weight", entry.weight)
            if (entry.sleepHours != null) put("sleepHours", entry.sleepHours)
            if (entry.painLevel != null) put("painLevel", entry.painLevel)
            if (entry.mood != null) put("mood", entry.mood)
            if (entry.temperature != null) put("temperature", entry.temperature)
            if (entry.steps != null) put("steps", entry.steps)
            if (entry.heartRate != null) put("heartRate", entry.heartRate)
            if (entry.systolic != null) put("systolic", entry.systolic)
            if (entry.diastolic != null) put("diastolic", entry.diastolic)
            if (entry.notes != null) put("notes", entry.notes)
        }.toString()
        crypto.encryptToFile(json.toByteArray(Charsets.UTF_8), File(healthDir, "${entry.id}.health.enc"))
    }

    fun listHealthEntries(): List<HealthEntry> {
        return (healthDir.listFiles() ?: emptyArray())
            .filter { it.name.endsWith(".health.enc") }
            .mapNotNull { file ->
                try {
                    val json = String(crypto.decryptFile(file), Charsets.UTF_8)
                    val obj = JSONObject(json)
                    HealthEntry(
                        id = obj.getString("id"),
                        profileId = obj.optString("profileId", "self"),
                        date = obj.getLong("date"),
                        weight = if (obj.has("weight")) obj.getDouble("weight").toFloat() else null,
                        sleepHours = if (obj.has("sleepHours")) obj.getDouble("sleepHours").toFloat() else null,
                        painLevel = if (obj.has("painLevel")) obj.getInt("painLevel") else null,
                        mood = if (obj.has("mood")) obj.getInt("mood") else null,
                        temperature = if (obj.has("temperature")) obj.getDouble("temperature").toFloat() else null,
                        steps = if (obj.has("steps")) obj.getInt("steps") else null,
                        heartRate = if (obj.has("heartRate")) obj.getInt("heartRate") else null,
                        systolic = if (obj.has("systolic")) obj.getInt("systolic") else null,
                        diastolic = if (obj.has("diastolic")) obj.getInt("diastolic") else null,
                        notes = obj.optString("notes", "").ifEmpty { null }
                    )
                } catch (e: Exception) { Log.e(TAG, "Failed to load health: ${e.message}"); null }
            }
            .sortedByDescending { it.date }
    }

    fun deleteHealthEntry(id: String) {
        File(healthDir, "$id.health.enc").delete()
    }

    fun listHealthEntriesForProfile(profileId: String): List<HealthEntry> =
        listHealthEntries().filter { it.profileId == profileId }

    fun listHealthEntriesInRange(from: Long, to: Long, profileId: String = "self"): List<HealthEntry> =
        listHealthEntries().filter { it.profileId == profileId && it.date in from..to }

    // ===== Health Profiles =====

    fun saveProfiles(profiles: List<HealthProfile>) {
        val arr = JSONArray()
        profiles.forEach { p -> arr.put(JSONObject().apply { put("id", p.id); put("name", p.name); put("icon", p.icon); put("createdAt", p.createdAt) }) }
        crypto.encryptToFile(arr.toString().toByteArray(Charsets.UTF_8), File(healthDir, "profiles.enc"))
    }

    fun loadProfiles(): List<HealthProfile> {
        val file = File(healthDir, "profiles.enc")
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(String(crypto.decryptFile(file), Charsets.UTF_8))
            (0 until arr.length()).map { i -> val o = arr.getJSONObject(i)
                HealthProfile(o.getString("id"), o.getString("name"), o.optString("icon", "👤"), o.optLong("createdAt", 0))
            }
        } catch (_: Exception) { emptyList() }
    }

    // ===== Expense Date Range =====

    fun listExpensesInRange(from: Long, to: Long): List<Expense> =
        listExpenses().filter { it.date in from..to }

    // ===== PDF Report Generation =====

    fun generateExpenseReport(expenses: List<Expense>): String = "" // kept for compat, use table version

    fun generateExpenseReportTable(expenses: List<Expense>): Triple<List<String>, List<String>, List<List<String>>> {
        val fmt = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val subtitle = listOf(
            "Generated: ${fmt.format(Date())}",
            "Total: ${"%.2f".format(expenses.sumOf { it.amount })} • ${expenses.size} items"
        )
        val summary = EXPENSE_CATEGORIES.mapNotNull { cat ->
            val total = expenses.filter { it.category == cat }.sumOf { it.amount }
            if (total > 0) "$cat: ${"%.2f".format(total)}" else null
        }
        val headers = listOf("Date", "Category", "Description", "Amount")
        val rows = expenses.map { e ->
            listOf(fmt.format(Date(e.date)), e.category, e.description, "${"%.2f".format(e.amount)}")
        }
        return Triple(subtitle + listOf("---") + summary, headers, rows)
    }

    fun generateHealthReport(entries: List<HealthEntry>, profileName: String): String = "" // compat

    fun generateHealthReportTable(entries: List<HealthEntry>, profileName: String): Triple<List<String>, List<String>, List<List<String>>> {
        val fmt = SimpleDateFormat("MMM dd HH:mm", Locale.getDefault())
        val subtitle = mutableListOf(
            "Profile: $profileName",
            "Generated: ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date())}",
            "Entries: ${entries.size}"
        )
        // Averages
        val avgs = mutableListOf<String>()
        entries.mapNotNull { it.weight }.let { if (it.isNotEmpty()) avgs.add("Weight: ${"%.1f".format(it.average())}kg") }
        entries.mapNotNull { it.sleepHours }.let { if (it.isNotEmpty()) avgs.add("Sleep: ${"%.1f".format(it.average())}hrs") }
        entries.mapNotNull { it.heartRate }.let { if (it.isNotEmpty()) avgs.add("HR: ${it.average().toInt()}bpm") }
        entries.mapNotNull { it.temperature }.let { if (it.isNotEmpty()) avgs.add("Temp: ${"%.1f".format(it.average())}°") }
        entries.mapNotNull { it.steps }.let { if (it.isNotEmpty()) avgs.add("Steps: ${it.average().toInt()}") }
        entries.filter { it.systolic != null }.let { if (it.isNotEmpty()) avgs.add("BP: ${it.map { e -> e.systolic!! }.average().toInt()}/${it.map { e -> e.diastolic!! }.average().toInt()}") }

        val headers = listOf("Date", "Weight", "Sleep", "HR", "Temp", "BP", "Steps", "Mood", "Notes")
        val rows = entries.map { e ->
            listOf(
                fmt.format(Date(e.date)),
                e.weight?.let { "${"%.1f".format(it)}" } ?: "",
                e.sleepHours?.let { "${"%.1f".format(it)}" } ?: "",
                e.heartRate?.toString() ?: "",
                e.temperature?.let { "${"%.1f".format(it)}" } ?: "",
                if (e.systolic != null) "${e.systolic}/${e.diastolic}" else "",
                e.steps?.toString() ?: "",
                e.mood?.let { if (it in 1..5) MOOD_EMOJIS[it - 1] else "" } ?: "",
                e.notes ?: ""
            )
        }
        return Triple(subtitle + if (avgs.isNotEmpty()) listOf("---", "Averages: ${avgs.joinToString(" • ")}") else emptyList(), headers, rows)
    }

    // ===== Habits =====

    fun saveHabits(habits: List<Habit>) {
        val arr = JSONArray()
        habits.forEach { h ->
            arr.put(JSONObject().apply {
                put("id", h.id); put("name", h.name); put("icon", h.icon)
                put("color", h.color); put("createdAt", h.createdAt)
            })
        }
        crypto.encryptToFile(arr.toString().toByteArray(Charsets.UTF_8), File(habitsDir, "habits_config.enc"))
    }

    fun loadHabits(): List<Habit> {
        val file = File(habitsDir, "habits_config.enc")
        if (!file.exists()) return emptyList()
        return try {
            val json = String(crypto.decryptFile(file), Charsets.UTF_8)
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Habit(
                    id = obj.getString("id"), name = obj.getString("name"),
                    icon = obj.optString("icon", "✅"),
                    color = obj.optInt("color", 0xFF4CAF50.toInt()),
                    createdAt = obj.optLong("createdAt", 0)
                )
            }
        } catch (e: Exception) { Log.e(TAG, "Failed to load habits: ${e.message}"); emptyList() }
    }

    fun saveHabitLog(log: HabitLog) {
        val json = JSONObject().apply {
            put("date", log.date)
            put("completed", JSONArray(log.completed.toList()))
        }.toString()
        crypto.encryptToFile(json.toByteArray(Charsets.UTF_8), File(habitsDir, "${log.date}.hablog.enc"))
    }

    fun loadHabitLog(date: String): HabitLog {
        val file = File(habitsDir, "$date.hablog.enc")
        if (!file.exists()) return HabitLog(date, emptySet())
        return try {
            val json = String(crypto.decryptFile(file), Charsets.UTF_8)
            val obj = JSONObject(json)
            val arr = obj.getJSONArray("completed")
            val set = (0 until arr.length()).map { arr.getString(it) }.toSet()
            HabitLog(obj.getString("date"), set)
        } catch (e: Exception) { HabitLog(date, emptySet()) }
    }

    fun loadHabitLogs(days: Int = 30): List<HabitLog> {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = java.util.Calendar.getInstance()
        return (0 until days).mapNotNull { i ->
            cal.timeInMillis = System.currentTimeMillis() - i * 86400000L
            val date = fmt.format(cal.time)
            val log = loadHabitLog(date)
            if (log.completed.isNotEmpty()) log else null
        }
    }

    fun getStreak(habitId: String): Int {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = java.util.Calendar.getInstance()
        var streak = 0
        for (i in 0..365) {
            cal.timeInMillis = System.currentTimeMillis() - i * 86400000L
            val date = fmt.format(cal.time)
            val log = loadHabitLog(date)
            if (habitId in log.completed) streak++ else if (i > 0) break
        }
        return streak
    }

    // ===== Wipe =====

    fun wipeAll() {
        expensesDir.listFiles()?.forEach { it.delete() }
        healthDir.listFiles()?.forEach { it.delete() }
        habitsDir.listFiles()?.forEach { it.delete() }
        Log.i(TAG, "Insights data wiped")
    }
}
