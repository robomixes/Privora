package com.privateai.camera.ui.qrscanner

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class QrHistoryItem(
    val id: String = UUID.randomUUID().toString(),
    val rawValue: String,
    val displayValue: String,
    val format: Int,
    val valueType: Int,
    val typeLabel: String,
    val source: QrSource,
    val timestamp: Long = System.currentTimeMillis()
)

enum class QrSource { SCANNED, GENERATED }

enum class QrGeneratorType(val label: String) {
    PLAIN_TEXT("Plain Text"),
    URL("URL"),
    WIFI("WiFi"),
    PHONE("Phone"),
    EMAIL("Email"),
    SMS("SMS"),
    VCARD("Contact")
}

object QrHistoryRepository {
    private const val PREFS_NAME = "qr_history"
    private const val KEY_ITEMS = "items"
    private const val MAX_ITEMS = 200

    fun load(context: Context): List<QrHistoryItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                try {
                    array.getJSONObject(i).toHistoryItem()
                } catch (_: Exception) {
                    null
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addItem(context: Context, item: QrHistoryItem) {
        val items = load(context).toMutableList()
        items.add(0, item)
        if (items.size > MAX_ITEMS) {
            while (items.size > MAX_ITEMS) items.removeLast()
        }
        save(context, items)
    }

    fun deleteItem(context: Context, id: String) {
        val items = load(context).filter { it.id != id }
        save(context, items)
    }

    fun clearAll(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_ITEMS).apply()
    }

    private fun save(context: Context, items: List<QrHistoryItem>) {
        val array = JSONArray()
        items.forEach { array.put(it.toJson()) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    private fun QrHistoryItem.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("rawValue", rawValue)
        put("displayValue", displayValue)
        put("format", format)
        put("valueType", valueType)
        put("typeLabel", typeLabel)
        put("source", source.name)
        put("timestamp", timestamp)
    }

    private fun JSONObject.toHistoryItem(): QrHistoryItem = QrHistoryItem(
        id = getString("id"),
        rawValue = getString("rawValue"),
        displayValue = getString("displayValue"),
        format = getInt("format"),
        valueType = getInt("valueType"),
        typeLabel = getString("typeLabel"),
        source = QrSource.valueOf(getString("source")),
        timestamp = getLong("timestamp")
    )
}
