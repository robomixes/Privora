package com.privateai.camera.bridge

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Singleton wrapper for Gemma 4 on-device inference via LiteRT-LM.
 *
 * - Lazy loads the engine only when first invoked
 * - All methods are suspend-safe and thread-safe
 * - Model file must be downloaded separately (not bundled in APK)
 */
object GemmaRunner {

    private const val TAG = "GemmaRunner"
    private const val PREFS_NAME = "gemma_settings"
    private const val KEY_ENABLED = "ai_enabled"
    private const val MODEL_DIR = "models"
    private const val MODEL_FILE = "gemma-4-e2b.litertlm"

    private var engine: Engine? = null
    private val mutex = Mutex()

    // ── Status checks ────────────────────────────────────────────────

    /** Whether the user has enabled Advanced AI in settings. */
    fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** Whether the model file is downloaded and ready. */
    fun isModelDownloaded(context: Context): Boolean {
        return getModelFile(context).exists()
    }

    /** Whether the AI is enabled AND model is present — use this to show/hide AI buttons. */
    fun isAvailable(context: Context): Boolean {
        return isEnabled(context) && isModelDownloaded(context)
    }

    /** Get the model file path. */
    fun getModelFile(context: Context): File {
        return File(File(context.filesDir, MODEL_DIR), MODEL_FILE)
    }

    /** Get model size in bytes (0 if not downloaded). */
    fun getModelSizeBytes(context: Context): Long {
        val f = getModelFile(context)
        return if (f.exists()) f.length() else 0L
    }

    /** Delete the downloaded model to free storage. */
    fun deleteModel(context: Context) {
        getModelFile(context).delete()
        unload()
    }

    // ── Engine lifecycle ─────────────────────────────────────────────

    /** Load the engine. Call from a coroutine (IO-bound, takes ~3-5 sec cold). */
    suspend fun load(context: Context) = mutex.withLock {
        if (engine != null) return@withLock

        val modelFile = getModelFile(context)
        if (!modelFile.exists()) {
            Log.w(TAG, "Model not found at ${modelFile.absolutePath}")
            return@withLock
        }

        withContext(Dispatchers.IO) {
            try {
                val config = EngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = Backend.CPU()
                )
                val eng = Engine(config)
                eng.initialize()
                engine = eng
                Log.i(TAG, "Gemma 4 engine loaded successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load Gemma engine: ${e.message}", e)
            }
        }
    }

    /** Unload the engine to free RAM. */
    fun unload() {
        try {
            engine?.close()
        } catch (_: Exception) {}
        engine = null
        Log.i(TAG, "Gemma engine unloaded")
    }

    fun isLoaded(): Boolean = engine != null

    // ── Text inference ───────────────────────────────────────────────

    /** Single-turn text completion. Returns the full response text. */
    suspend fun complete(
        context: Context,
        prompt: String,
        systemInstruction: String = "",
        maxTokens: Int = 512,
        temperature: Double = 0.7
    ): String? {
        if (engine == null) load(context)
        val eng = engine ?: return null

        return withContext(Dispatchers.IO) {
            try {
                val conversation = eng.createConversation(
                    ConversationConfig(
                        systemInstruction = if (systemInstruction.isNotEmpty()) Contents.of(Content.Text(systemInstruction)) else null,
                        samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = temperature)
                    )
                )
                val response = conversation.sendMessage(prompt)
                conversation.close()
                extractText(response)
            } catch (e: Exception) {
                Log.e(TAG, "Inference failed: ${e.message}", e)
                null
            }
        }
    }

    /** Streaming text completion. Emits partial token strings as they arrive. */
    fun completeStreaming(
        context: Context,
        prompt: String,
        systemInstruction: String = "",
        temperature: Double = 0.7
    ): Flow<String> = flow {
        if (engine == null) {
            withContext(Dispatchers.IO) { load(context) }
        }
        val eng = engine ?: return@flow

        val conversation = eng.createConversation(
            ConversationConfig(
                systemInstruction = if (systemInstruction.isNotEmpty()) Contents.of(Content.Text(systemInstruction)) else null,
                samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = temperature)
            )
        )

        try {
            conversation.sendMessageAsync(prompt).collect { chunk ->
                emit(chunk.toString())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Streaming inference failed: ${e.message}", e)
        } finally {
            conversation.close()
        }
    }

    // ── Vision inference ─────────────────────────────────────────────

    /** Describe an image or answer a question about it. */
    suspend fun describeImage(
        context: Context,
        imagePath: String,
        prompt: String = "Describe this image in one sentence."
    ): String? {
        if (engine == null) load(context)
        val eng = engine ?: return null

        return withContext(Dispatchers.IO) {
            try {
                val conversation = eng.createConversation(ConversationConfig())
                val response = conversation.sendMessage(
                    Contents.of(
                        Content.ImageFile(imagePath),
                        Content.Text(prompt)
                    )
                )
                conversation.close()
                extractText(response)
            } catch (e: Exception) {
                Log.e(TAG, "Vision inference failed: ${e.message}", e)
                null
            }
        }
    }

    /** Extract text from a Message response. */
    private fun extractText(message: Message?): String? {
        return message?.toString()
    }
}
