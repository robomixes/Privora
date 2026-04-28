package com.privateai.camera.bridge

import android.content.Context
import android.util.Log
import com.privateai.camera.security.ContactRepository
import com.privateai.camera.security.CryptoManager
import com.privateai.camera.security.Expense
import com.privateai.camera.security.Habit
import com.privateai.camera.security.HealthEntry
import com.privateai.camera.security.InsightsRepository
import com.privateai.camera.security.Medication
import com.privateai.camera.security.NoteRepository
import com.privateai.camera.security.PrivateContact
import com.privateai.camera.security.PrivoraDatabase
import com.privateai.camera.security.ScheduleItem
import com.privateai.camera.security.ScheduleKind
import com.privateai.camera.service.ReminderScheduler
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.UUID

/**
 * AI-proposed action that the user must explicitly tap to confirm.
 *
 * The model emits one of these as `{"type":"action", "kind":"<...>", ...payload}`
 * alongside (or instead of) a text reply. The chat UI renders a card with the
 * proposal details + Add/Dismiss buttons. The action is *never* executed
 * automatically — the user always confirms.
 *
 * This is qualitatively different from [ParsedReply.ToolCall] (which is a read-
 * only data lookup the model executes itself). Actions write user data, so they
 * stay behind a one-tap confirmation barrier.
 */
sealed class ProposedAction {
    /** A short human-friendly summary the chat bubble shows above the action card. */
    abstract val summary: String

    data class Reminder(
        val title: String,
        val whenMillis: Long,
        override val summary: String
    ) : ProposedAction()

    data class Expense(
        val amount: Double,
        val currency: String,
        val category: String,
        val description: String,
        override val summary: String
    ) : ProposedAction()

    data class Note(
        val title: String,
        val body: String,
        override val summary: String
    ) : ProposedAction()

    data class HealthRecord(
        val date: Long,
        val weight: Float? = null,
        val sleepHours: Float? = null,
        val mood: Int? = null,        // 1..5
        val painLevel: Int? = null,   // 0..10
        val temperature: Float? = null,
        val steps: Int? = null,
        val heartRate: Int? = null,
        val systolic: Int? = null,
        val diastolic: Int? = null,
        val notes: String? = null,
        override val summary: String
    ) : ProposedAction()

    data class Contact(
        val name: String,
        val phone: String? = null,
        val email: String? = null,
        val notes: String? = null,
        override val summary: String
    ) : ProposedAction()

    data class MedicationAction(
        val name: String,
        val dosage: String? = null,
        val instructions: String? = null,
        override val summary: String
    ) : ProposedAction()

    data class HabitAction(
        val name: String,
        val icon: String? = null,
        override val summary: String
    ) : ProposedAction()
}

object AssistantActions {

    private const val TAG = "AssistantActions"

    /** Categories the model is allowed to use for expense proposals. */
    val EXPENSE_CATEGORIES = listOf(
        "Food", "Transport", "Shopping", "Bills",
        "Health", "Entertainment", "Education", "Other"
    )

    /**
     * Try to parse a model JSON object into a [ProposedAction]. Returns null
     * if the object isn't an action or is missing required fields.
     */
    fun parse(json: JSONObject): ProposedAction? {
        if (json.optString("type", "").lowercase() != "action") return null
        val kind = json.optString("kind", "").lowercase()
        val summary = json.optString("summary", json.optString("note", "")).trim()
        return when (kind) {
            "reminder" -> {
                val title = json.optString("title", "").trim()
                val whenStr = json.optString("when", "").trim()
                val millis = parseTimestamp(whenStr) ?: return null
                if (title.isEmpty() || millis <= System.currentTimeMillis()) return null
                ProposedAction.Reminder(title, millis, summary.ifEmpty { "Reminder: $title" })
            }
            "expense" -> {
                val amount = json.optDouble("amount", Double.NaN)
                if (amount.isNaN() || amount <= 0) return null
                val currency = json.optString("currency", "USD").trim().ifEmpty { "USD" }
                val rawCategory = json.optString("category", "Other").trim()
                val category = EXPENSE_CATEGORIES.firstOrNull { it.equals(rawCategory, ignoreCase = true) } ?: "Other"
                val description = json.optString("description", "").trim()
                if (description.isEmpty()) return null
                ProposedAction.Expense(
                    amount = amount,
                    currency = currency,
                    category = category,
                    description = description,
                    summary = summary.ifEmpty { "${"%.2f".format(amount)} $currency — $description ($category)" }
                )
            }
            "note" -> {
                val title = json.optString("title", "").trim()
                val body = json.optString("body", json.optString("content", "")).trim()
                if (title.isEmpty() && body.isEmpty()) return null
                ProposedAction.Note(
                    title = title.ifEmpty { body.lineSequence().first().take(40) },
                    body = body,
                    summary = summary.ifEmpty { "Note: ${title.ifEmpty { body.take(40) }}" }
                )
            }
            "health", "health_record" -> {
                fun optFloat(key: String): Float? =
                    if (json.has(key) && !json.isNull(key)) json.optDouble(key, Double.NaN).takeIf { !it.isNaN() }?.toFloat() else null
                fun optInt(key: String): Int? =
                    if (json.has(key) && !json.isNull(key)) json.optInt(key, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE } else null

                val weight = optFloat("weight")
                val sleepHours = optFloat("sleepHours") ?: optFloat("sleep")
                val mood = optInt("mood")
                val painLevel = optInt("painLevel") ?: optInt("pain")
                val temperature = optFloat("temperature") ?: optFloat("temp")
                val steps = optInt("steps")
                val heartRate = optInt("heartRate") ?: optInt("hr")
                val systolic = optInt("systolic")
                val diastolic = optInt("diastolic")
                val notes = json.optString("notes", "").trim().ifEmpty { null }
                if (listOfNotNull(weight, sleepHours, mood, painLevel, temperature, steps, heartRate, systolic, diastolic).isEmpty() && notes == null) return null
                val date = parseTimestamp(json.optString("date", "")) ?: System.currentTimeMillis()
                ProposedAction.HealthRecord(
                    date = date,
                    weight = weight, sleepHours = sleepHours, mood = mood, painLevel = painLevel,
                    temperature = temperature, steps = steps, heartRate = heartRate,
                    systolic = systolic, diastolic = diastolic, notes = notes,
                    summary = summary.ifEmpty { "Health entry — tap Add to log it." }
                )
            }
            "contact", "person" -> {
                val name = json.optString("name", "").trim()
                if (name.isEmpty()) return null
                ProposedAction.Contact(
                    name = name,
                    phone = json.optString("phone", "").trim().ifEmpty { null },
                    email = json.optString("email", "").trim().ifEmpty { null },
                    notes = json.optString("notes", "").trim().ifEmpty { null },
                    summary = summary.ifEmpty { "Add $name to contacts?" }
                )
            }
            "medication", "med" -> {
                val name = json.optString("name", "").trim()
                if (name.isEmpty()) return null
                ProposedAction.MedicationAction(
                    name = name,
                    dosage = json.optString("dosage", "").trim().ifEmpty { null },
                    instructions = json.optString("instructions", "").trim().ifEmpty { null },
                    summary = summary.ifEmpty { "Add $name to medications?" }
                )
            }
            "habit" -> {
                val name = json.optString("name", "").trim()
                if (name.isEmpty()) return null
                ProposedAction.HabitAction(
                    name = name,
                    icon = json.optString("icon", "").trim().ifEmpty { null },
                    summary = summary.ifEmpty { "Track \"$name\" as a habit?" }
                )
            }
            else -> null
        }
    }

    /**
     * Execute a confirmed action. Runs on a background thread (must not be
     * called from the main thread). Returns true on success.
     */
    fun execute(context: Context, action: ProposedAction): Boolean = try {
        val crypto = CryptoManager(context).also { it.initialize() }
        when (action) {
            is ProposedAction.Reminder -> {
                val repo = InsightsRepository(File(context.filesDir, "vault/insights"), crypto)
                val item = ScheduleItem(
                    title = action.title,
                    kind = ScheduleKind.CUSTOM,
                    oneShotAt = action.whenMillis,
                    enabled = true
                )
                repo.saveSchedule(item)
                ReminderScheduler.scheduleItem(context, item)
                true
            }
            is ProposedAction.Expense -> {
                val repo = InsightsRepository(File(context.filesDir, "vault/insights"), crypto)
                val expense = Expense(
                    amount = action.amount,
                    currency = action.currency,
                    category = action.category,
                    description = action.description
                )
                repo.saveExpense(expense)
                true
            }
            is ProposedAction.Note -> {
                val repo = NoteRepository(File(context.filesDir, "vault/notes"), crypto)
                repo.createNote(
                    title = action.title,
                    content = action.body,
                    tags = emptyList()
                )
                true
            }
            is ProposedAction.HealthRecord -> {
                val repo = InsightsRepository(File(context.filesDir, "vault/insights"), crypto)
                val entry = HealthEntry(
                    profileId = "self",
                    date = action.date,
                    weight = action.weight,
                    sleepHours = action.sleepHours,
                    mood = action.mood,
                    painLevel = action.painLevel,
                    temperature = action.temperature,
                    steps = action.steps,
                    heartRate = action.heartRate,
                    systolic = action.systolic,
                    diastolic = action.diastolic,
                    notes = action.notes
                )
                repo.saveHealthEntry(entry)
                true
            }
            is ProposedAction.Contact -> {
                val db = PrivoraDatabase.getInstance(context, crypto)
                val contactRepo = ContactRepository(File(context.filesDir, "vault/contacts"), crypto, db)
                val contact = PrivateContact(
                    id = UUID.randomUUID().toString(),
                    name = action.name,
                    phone = action.phone ?: "",
                    email = action.email ?: "",
                    notes = action.notes ?: ""
                )
                contactRepo.saveContact(contact)
                true
            }
            is ProposedAction.MedicationAction -> {
                val repo = InsightsRepository(File(context.filesDir, "vault/insights"), crypto)
                val med = Medication(
                    profileId = "self",
                    name = action.name,
                    dosage = action.dosage ?: "",
                    instructions = action.instructions ?: ""
                )
                repo.saveMedication(med)
                true
            }
            is ProposedAction.HabitAction -> {
                val repo = InsightsRepository(File(context.filesDir, "vault/insights"), crypto)
                val existing = repo.loadHabits()
                val habit = Habit(
                    name = action.name,
                    icon = action.icon ?: "✅",
                    profileId = "self"
                )
                repo.saveHabits(existing + habit)
                true
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to execute action: ${e.message}", e)
        false
    }

    /**
     * Parse a model-emitted timestamp. Accepts ISO-8601 (with or without zone)
     * and falls back to a few common variants. Returns null if unparseable.
     */
    private fun parseTimestamp(s: String): Long? {
        if (s.isEmpty()) return null
        return try {
            // ISO with zone: "2026-04-29T15:00:00Z" or "2026-04-29T15:00:00+02:00"
            Instant.parse(s).toEpochMilli()
        } catch (_: Exception) {
            try {
                // Local datetime without zone: assume device default zone
                java.time.LocalDateTime.parse(s)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            } catch (_: Exception) {
                null
            }
        }
    }
}
