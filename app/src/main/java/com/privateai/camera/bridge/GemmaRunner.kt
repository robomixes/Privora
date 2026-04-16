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
    private var activeConversation: Conversation? = null
    private val mutex = Mutex()
    private var loadFailed = false

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

    /** Whether the AI is enabled AND model is present AND hasn't failed — use this to show/hide AI buttons. */
    fun isAvailable(context: Context): Boolean {
        return isEnabled(context) && isModelDownloaded(context) && !loadFailed
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

    /** Delete the downloaded model and cache to free storage. */
    fun deleteModel(context: Context) {
        getModelFile(context).delete()
        // Also delete xnnpack cache
        File(getModelFile(context).absolutePath + ".xnnpack_cache").delete()
        resetCrashFlag(context)
        unload()
    }

    // ── Engine lifecycle ─────────────────────────────────────────────

    /** Load the engine. Call from a coroutine (IO-bound, takes ~3-5 sec cold). */
    suspend fun load(context: Context) = mutex.withLock {
        if (engine != null) return@withLock
        if (loadFailed) return@withLock

        val modelFile = getModelFile(context)
        if (!modelFile.exists()) {
            Log.w(TAG, "Model not found at ${modelFile.absolutePath}")
            return@withLock
        }

        // Check if a previous load attempt crashed the app
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean("load_crashed", false)) {
            Log.w(TAG, "Previous engine load crashed — skipping. User can retry from Settings.")
            loadFailed = true
            return@withLock
        }

        withContext(Dispatchers.IO) {
            try {
                // Clean stale engine caches from previous backend attempts
                val modelDir = modelFile.parentFile
                modelDir?.listFiles()?.forEach { f ->
                    if (f.name.endsWith(".xnnpack_cache") || f.name.endsWith("_mldrift_program_cache.bin")) {
                        f.delete()
                        Log.d(TAG, "Deleted stale cache: ${f.name}")
                    }
                }

                // Mark as "loading" — if app crashes during this, we know on next launch
                prefs.edit().putBoolean("load_crashed", true).commit()

                // Try GPU first (faster, bypasses CPU vision crash in 0.10.0)
                // Fall back to CPU if GPU/OpenCL not available
                var eng: Engine? = null
                var usedBackend = "unknown"
                try {
                    Log.i(TAG, "Attempting GPU backend...")
                    val gpuConfig = EngineConfig(modelPath = modelFile.absolutePath, backend = Backend.GPU())
                    eng = Engine(gpuConfig)
                    eng.initialize()
                    usedBackend = "GPU"
                } catch (gpuErr: Exception) {
                    Log.w(TAG, "GPU failed (${gpuErr.message}), falling back to CPU...")
                    try { eng?.close() } catch (_: Exception) {}
                    eng = null
                    // Clean GPU cache before CPU attempt
                    modelFile.parentFile?.listFiles()?.forEach { f ->
                        if (f.name.contains("mldrift")) { f.delete() }
                    }
                    val cpuConfig = EngineConfig(modelPath = modelFile.absolutePath, backend = Backend.CPU())
                    eng = Engine(cpuConfig)
                    eng.initialize()
                    usedBackend = "CPU"
                }
                engine = eng

                // Load succeeded — clear the crash flag
                prefs.edit().putBoolean("load_crashed", false).apply()
                Log.i(TAG, "Gemma 4 engine loaded successfully (backend=$usedBackend)")
            } catch (e: Exception) {
                prefs.edit().putBoolean("load_crashed", false).apply()
                loadFailed = true
                Log.e(TAG, "Failed to load Gemma engine: ${e.message}", e)
            }
        }
    }

    /** Reset the crash flag so the user can retry loading. */
    fun resetCrashFlag(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean("load_crashed", false).apply()
        loadFailed = false
    }

    /** Close any active conversation (LiteRT-LM only allows one at a time). */
    private fun closeActiveConversation() {
        try { activeConversation?.close() } catch (_: Exception) {}
        activeConversation = null
    }

    /** Unload the engine to free RAM. */
    fun unload() {
        closeActiveConversation()
        try { engine?.close() } catch (_: Exception) {}
        engine = null
        Log.i(TAG, "Gemma engine unloaded")
    }

    fun isLoaded(): Boolean = engine != null

    /** Reload engine on CPU backend after GPU failure. */
    private fun reloadOnCpu(context: Context) {
        try {
            closeActiveConversation()
            engine?.close()
            engine = null
            // Clean GPU caches
            getModelFile(context).parentFile?.listFiles()?.forEach { f ->
                if (f.name.contains("mldrift") || f.name.endsWith(".xnnpack_cache")) f.delete()
            }
            val config = EngineConfig(
                modelPath = getModelFile(context).absolutePath,
                backend = Backend.CPU()
            )
            val eng = Engine(config)
            eng.initialize()
            engine = eng
            Log.i(TAG, "Engine reloaded on CPU backend")
        } catch (e: Exception) {
            Log.e(TAG, "CPU reload also failed: ${e.message}", e)
            loadFailed = true
        }
    }

    /** Retry a text completion after backend switch. */
    private fun retryComplete(prompt: String, systemInstruction: String, temperature: Double): String? {
        val eng = engine ?: return null
        return try {
            closeActiveConversation()
            val conversation = eng.createConversation(
                ConversationConfig(
                    systemInstruction = if (systemInstruction.isNotEmpty()) Contents.of(Content.Text(systemInstruction)) else null,
                    samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = temperature)
                )
            )
            activeConversation = conversation
            val response = conversation.sendMessage(prompt)
            closeActiveConversation()
            Log.i(TAG, "Retry on CPU succeeded")
            extractText(response)
        } catch (e: Exception) {
            closeActiveConversation()
            Log.e(TAG, "Retry on CPU also failed: ${e.message}", e)
            null
        }
    }

    // ── Text inference ───────────────────────────────────────────────

    /** Single-turn text completion. Returns the full response text. */
    suspend fun complete(
        context: Context,
        prompt: String,
        systemInstruction: String = "",
        maxTokens: Int = 512,
        temperature: Double = 0.7
    ): String? {
        Log.d(TAG, "complete() called — loadFailed=$loadFailed, engine=${engine != null}")
        if (loadFailed) { Log.w(TAG, "complete() skipped — loadFailed"); return null }
        if (engine == null) load(context)
        val eng = engine
        if (eng == null) { Log.w(TAG, "complete() skipped — engine null after load"); return null }

        return mutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    closeActiveConversation()
                    Log.d(TAG, "Creating conversation for text completion...")
                    val conversation = eng.createConversation(
                        ConversationConfig(
                            systemInstruction = if (systemInstruction.isNotEmpty()) Contents.of(Content.Text(systemInstruction)) else null,
                            samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = temperature)
                        )
                    )
                    activeConversation = conversation
                    Log.d(TAG, "Sending message (${prompt.take(50)}...)")
                    val response = conversation.sendMessage(prompt)
                    Log.d(TAG, "Response received: ${response.toString().take(100)}")
                    closeActiveConversation()
                    extractText(response)
                } catch (e: Exception) {
                    closeActiveConversation()
                    Log.e(TAG, "Inference failed: ${e.message}", e)
                    // If GPU failed with OpenCL error, retry on CPU
                    if (e.message?.contains("OpenCL") == true) {
                        Log.w(TAG, "GPU inference failed — reloading engine on CPU...")
                        reloadOnCpu(context)
                        return@withContext retryComplete(prompt, systemInstruction, temperature)
                    }
                    null
                }
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

        mutex.withLock {
            closeActiveConversation()
            val conversation = eng.createConversation(
                ConversationConfig(
                    systemInstruction = if (systemInstruction.isNotEmpty()) Contents.of(Content.Text(systemInstruction)) else null,
                    samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = temperature)
                )
            )
            activeConversation = conversation

            try {
                conversation.sendMessageAsync(prompt).collect { chunk ->
                    emit(chunk.toString())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Streaming inference failed: ${e.message}", e)
            } finally {
                closeActiveConversation()
            }
        }
    }

    // ── Vision inference ─────────────────────────────────────────────

    /** Describe an image or answer a question about it using Gemma vision. */
    suspend fun describeImage(
        context: Context,
        imagePath: String,
        prompt: String = "Describe this image in one sentence."
    ): String? {
        Log.d(TAG, "describeImage() called — loadFailed=$loadFailed, engine=${engine != null}, path=$imagePath")
        if (loadFailed) { Log.w(TAG, "describeImage() skipped — loadFailed"); return null }
        if (engine == null) load(context)
        val eng = engine
        if (eng == null) { Log.w(TAG, "describeImage() skipped — engine null"); return null }

        return mutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    closeActiveConversation()
                    Log.d(TAG, "Creating conversation for vision...")
                    val conversation = eng.createConversation(ConversationConfig())
                    activeConversation = conversation
                    // Image first, text second (required by Gemma 4 attention mechanism)
                    Log.d(TAG, "Sending vision message...")
                    val response = conversation.sendMessage(
                        Contents.of(
                            Content.ImageFile(imagePath),
                            Content.Text(prompt)
                        )
                    )
                    Log.d(TAG, "Vision response: ${response.toString().take(100)}")
                    closeActiveConversation()
                    extractText(response)
                } catch (e: Exception) {
                    closeActiveConversation()
                    Log.e(TAG, "Vision inference failed: ${e.message}", e)
                    null
                }
            }
        }
    }

    /** Describe an image using its existing labels (no vision, text-only, safe). */
    suspend fun describeFromLabels(
        context: Context,
        labels: List<String>
    ): String? {
        if (loadFailed) return null
        if (labels.isEmpty()) return null
        val labelStr = labels.joinToString(", ")
        return complete(
            context,
            "A photo was analyzed and these objects were detected: $labelStr.\n" +
            "Write a single factual sentence describing what is likely in this photo. " +
            "Only mention what the detected objects confirm. " +
            "Do NOT guess gender, age, emotions, or identity of people — just say 'person' or 'people'. " +
            "Output only the description, nothing else.",
            temperature = 0.3
        )
    }

    /** Answer a question about a photo using its labels and description (text-only, safe). */
    suspend fun askAboutPhoto(
        context: Context,
        labels: List<String>,
        description: String,
        question: String
    ): String? {
        if (loadFailed) return null
        val context2 = buildString {
            append("Detected objects in photo: ${labels.joinToString(", ")}.")
            if (description.isNotEmpty()) append(" Description: $description.")
        }
        return complete(
            context,
            "Photo analysis: $context2\n\nQuestion: $question\n\n" +
            "Answer based only on what was detected. If you cannot determine the answer from the detected objects, say so. " +
            "Do NOT guess gender, age, or identity. Be concise.",
            temperature = 0.3
        )
    }

    /** Extract text from a Message response. */
    private fun extractText(message: Message?): String? {
        return message?.toString()
    }

    // ── Strict-JSON helper ───────────────────────────────────────────

    /**
     * Run a completion expected to return a single JSON object.
     *
     * Tolerant of the common ways Gemma drifts from "JSON only" — it may
     * wrap the object in ```json fences, prepend a sentence, or follow it
     * with a paragraph. We grab the first {...} substring and parse that.
     *
     * Returns null if no JSON could be extracted (caller's job to fall back
     * to a user-friendly toast or treat the raw text as an answer).
     */
    suspend fun completeJson(
        context: Context,
        prompt: String,
        systemInstruction: String = "",
        maxTokens: Int = 512,
        temperature: Double = 0.2
    ): org.json.JSONObject? {
        val raw = complete(context, prompt, systemInstruction, maxTokens, temperature) ?: return null
        return extractFirstJsonObject(raw)
    }

    /** Pull the first balanced {...} block out of arbitrary model text. */
    internal fun extractFirstJsonObject(raw: String): org.json.JSONObject? {
        val start = raw.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var end = -1
        var inString = false
        var escape = false
        for (i in start until raw.length) {
            val c = raw[i]
            if (escape) { escape = false; continue }
            if (c == '\\') { escape = true; continue }
            if (c == '"') { inString = !inString; continue }
            if (inString) continue
            if (c == '{') depth++
            else if (c == '}') {
                depth--
                if (depth == 0) { end = i; break }
            }
        }
        if (end < 0) return null
        return try { org.json.JSONObject(raw.substring(start, end + 1)) } catch (_: Exception) { null }
    }
}
