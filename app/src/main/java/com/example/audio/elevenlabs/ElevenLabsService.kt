package com.example.audio.elevenlabs

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

data class GenerationLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val target: String,
    val success: Boolean,
    val statusCode: Int? = null,
    val message: String
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

class ElevenLabsService(private val context: Context) {
    private val configManager = ElevenLabsConfigManager(context)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val cacheDir: File
        get() = File(context.filesDir, "elevenlabs_cache").apply {
            if (!exists()) mkdirs()
        }

    companion object {
        @Volatile private var bundledIndex: Map<String, String>? = null

        /** Same rule as scripts/generate_game_audio.py: NFKC and collapsed whitespace. */
        fun normaliseText(text: String): String =
            java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFKC)
                .trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")

        private val generationLock = kotlinx.coroutines.sync.Mutex()
        private val _logs = MutableStateFlow<List<GenerationLogEntry>>(emptyList())
        val logs: StateFlow<List<GenerationLogEntry>> = _logs.asStateFlow()

        private val _lastAttemptResult = MutableStateFlow<String?>("لم تتم أي محاولة بعد")
        val lastAttemptResult: StateFlow<String?> = _lastAttemptResult.asStateFlow()

        private val _lastAttemptTimestamp = MutableStateFlow<Long?>(null)
        val lastAttemptTimestamp: StateFlow<Long?> = _lastAttemptTimestamp.asStateFlow()

        private val _lastAttemptSuccess = MutableStateFlow<Boolean?>(null)
        val lastAttemptSuccess: StateFlow<Boolean?> = _lastAttemptSuccess.asStateFlow()

        fun recordLog(target: String, success: Boolean, statusCode: Int?, message: String) {
            val entry = GenerationLogEntry(
                target = target,
                success = success,
                statusCode = statusCode,
                message = message
            )
            _logs.update { (listOf(entry) + it).take(50) }
            _lastAttemptResult.value = if (success) {
                "نجاح ✓ ($message)"
            } else {
                if (statusCode != null) "فشل (رمز $statusCode): $message" else "فشل: $message"
            }
            _lastAttemptTimestamp.value = entry.timestamp
            _lastAttemptSuccess.value = success
        }

        fun clearLogs() {
            _logs.value = emptyList()
        }
    }

    fun getApiKey(): String {
        return try {
            BuildConfig.ELEVENLABS_API_KEY
        } catch (_: Exception) {
            ""
        }
    }

    fun hasValidApiKey(): Boolean {
        if (BuildConfig.VOICE_SERVICE_URL.startsWith("https://")) return true
        val key = getApiKey().trim()
        return key.isNotEmpty() && key != "null" && !key.contains("MY_")
    }

    fun getConfigManager(): ElevenLabsConfigManager = configManager

    /**
     * Creates a unique deterministic hash key based on the text and voice settings.
     */
    fun computeCacheKey(text: String, settings: ElevenLabsSettings): String {
        val raw = "${settings.voiceId}_${settings.modelId}_${text.trim()}_${settings.stability}_${settings.similarityBoost}_${settings.style}_${settings.speed}"
        val bytes = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.take(28)
    }

    fun getCachedFile(text: String, settings: ElevenLabsSettings): File? {
        val key = computeCacheKey(text, settings)
        val file = File(cacheDir, "el_$key.mp3")
        if (file.exists() && file.length() > 0) return file
        return copyBundledHistoryClip(text, file)
    }

    /** Names recorded earlier in the ElevenLabs history ship inside the app (assets/voice_history). */
    private fun copyBundledHistoryClip(text: String, target: File): File? {
        val index = bundledHistoryIndex()
        val asset = bundledPhrasings(normaliseText(text)).firstNotNullOfOrNull { index[it] } ?: return null
        return try {
            val temporary = File.createTempFile("bundled_", ".tmp", cacheDir)
            try {
                context.assets.open("voice_history/$asset").use { input ->
                    temporary.outputStream().use { input.copyTo(it) }
                }
                if (temporary.length() == 0L || !temporary.renameTo(target)) return null
            } finally { temporary.delete() }
            target
        } catch (_: java.io.IOException) { null }
    }

    /** The exact phrase first, then older wordings of the same player's turn saved in the history. */
    private fun bundledPhrasings(phrase: String): List<String> {
        val name = Regex("^يلا يا (.+)، دورك$").find(phrase)?.groupValues?.get(1) ?: return listOf(phrase)
        return listOf(phrase, "$name، جاء دورك", "يلا $name دورك")
    }

    private fun bundledHistoryIndex(): Map<String, String> {
        bundledIndex?.let { return it }
        val parsed = try {
            val json = JSONObject(context.assets.open("voice_history/index.json").bufferedReader().use { it.readText() })
            json.keys().asSequence().associate { normaliseText(it) to json.getString(it) }
        } catch (_: Exception) { emptyMap() }
        bundledIndex = parsed
        return parsed
    }

    /**
     * Looks for an earlier recording of exactly this text and voice in the account history,
     * so a name that was generated before is downloaded instead of being paid for again.
     */
    private fun downloadFromHistory(text: String, apiKey: String, voiceId: String): ByteArray? {
        val wanted = normaliseText(text)
        var cursor: String? = null
        repeat(20) {
            val url = StringBuilder("https://api.elevenlabs.io/v1/history?page_size=100")
                .append("&voice_id=").append(java.net.URLEncoder.encode(voiceId, "UTF-8"))
                .append("&search=").append(java.net.URLEncoder.encode(text.trim(), "UTF-8"))
            cursor?.let { url.append("&start_after_history_item_id=").append(java.net.URLEncoder.encode(it, "UTF-8")) }
            val page = client.newCall(Request.Builder().url(url.toString()).addHeader("xi-api-key", apiKey).get().build())
                .execute().use { response ->
                    if (!response.isSuccessful) return null
                    JSONObject(response.body?.string() ?: return null)
                }
            val items = page.optJSONArray("history") ?: return null
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val source = item.optString("source", "TTS")
                if (item.optString("voice_id") == voiceId && normaliseText(item.optString("text")) == wanted &&
                    (source.isEmpty() || source == "TTS")
                ) {
                    val id = item.optString("history_item_id").ifEmpty { return null }
                    val audioRequest = Request.Builder()
                        .url("https://api.elevenlabs.io/v1/history/${java.net.URLEncoder.encode(id, "UTF-8")}/audio")
                        .addHeader("xi-api-key", apiKey).get().build()
                    return client.newCall(audioRequest).execute().use { audio ->
                        if (audio.isSuccessful) audio.body?.bytes()?.takeIf { it.isNotEmpty() } else null
                    }
                }
            }
            val next = page.optString("last_history_item_id")
            if (!page.optBoolean("has_more", false) || next.isEmpty() || next == cursor) return null
            cursor = next
        }
        return null
    }

    fun isCached(text: String, settings: ElevenLabsSettings): Boolean {
        return getCachedFile(text, settings) != null
    }

    /**
     * Tests connection to ElevenLabs and validates the configured API key and Voice ID.
     */
    suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey().trim()
        if (!hasValidApiKey()) {
            val missingMsg = "المفتاح مفقود: لم يتم ضبط ELEVENLABS_API_KEY في لوحة Secrets أو ملف .env"
            recordLog("فحص الاتصال", success = false, statusCode = null, message = missingMsg)
            return@withContext Result.failure(IllegalStateException(missingMsg))
        }

        try {
            val request = Request.Builder()
                .url("https://api.elevenlabs.io/v1/user")
                .addHeader("xi-api-key", apiKey)
                .get()
                .build()

            val response = try {
                client.newCall(request).execute()
            } catch (e: java.io.IOException) {
                val netMsg = "خطأ في الاتصال بالشبكة: ${e.message ?: "تعذر الوصول إلى الخادم"}"
                recordLog("فحص الاتصال", success = false, statusCode = null, message = netMsg)
                return@withContext Result.failure(Exception(netMsg))
            }

            if (!response.isSuccessful) {
                val code = response.code
                val rawBody = response.body?.string() ?: ""
                val serverDetail = extractServerDetail(rawBody)
                val fullErrorMsg = "رمز $code: $serverDetail"
                recordLog("فحص الاتصال", success = false, statusCode = code, message = fullErrorMsg)
                return@withContext Result.failure(Exception(fullErrorMsg))
            }

            val bodyStr = response.body?.string() ?: ""
            var quotaInfo = ""
            try {
                val json = JSONObject(bodyStr)
                if (json.has("subscription")) {
                    val sub = json.getJSONObject("subscription")
                    val used = sub.optLong("character_count", 0L)
                    val limit = sub.optLong("character_limit", 0L)
                    val tier = sub.optString("tier", "free")
                    quotaInfo = " (الباقة: $tier | المستخدم: $used / $limit حرف)"
                }
            } catch (_: Exception) {}

            val successMsg = "الاتصال بالخادم سليم ومفتاح API صالح ومفعل$quotaInfo"
            recordLog("فحص الاتصال", success = true, statusCode = 200, message = successMsg)
            Result.success(successMsg)
        } catch (e: Exception) {
            val errMsg = "فشل فحص الاتصال: ${e.message ?: "خطأ غير متوقع"}"
            recordLog("فحص الاتصال", success = false, statusCode = null, message = errMsg)
            Result.failure(e)
        }
    }

    /**
     * Synthesizes audio using ElevenLabs API with persistent disk caching.
     * If the audio is already cached, it returns the cached file immediately (zero cost).
     * If forceRegenerate is true, it queries ElevenLabs and updates the cache.
     * Captures unsummarized status codes and exact server error messages on failure.
     */
    suspend fun generateSpeech(
        text: String,
        settings: ElevenLabsSettings = configManager.getSettings(),
        forceRegenerate: Boolean = false
    ): Result<File> = generationLock.withLock { withContext(Dispatchers.IO) {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) {
            val err = "النص فارغ"
            recordLog(cleanText.ifEmpty { "(فارغ)" }, success = false, statusCode = null, message = err)
            return@withContext Result.failure(IllegalArgumentException(err))
        }

        val cached = getCachedFile(cleanText, settings)
        if (!forceRegenerate && cached != null) {
            recordLog(cleanText, success = true, statusCode = 200, message = "تم استرجاع الصوت من الذاكرة المحلية (جاهز ✓)")
            return@withContext Result.success(cached)
        }

        val apiKey = getApiKey().trim()
        if (!hasValidApiKey()) {
            val missingMsg = "المفتاح مفقود: لم يتم ضبط ELEVENLABS_API_KEY في لوحة Secrets أو ملف .env"
            recordLog(cleanText, success = false, statusCode = null, message = missingMsg)
            return@withContext Result.failure(IllegalStateException(missingMsg))
        }

        val targetKey = computeCacheKey(cleanText, settings)
        val outputFile = File(cacheDir, "el_$targetKey.mp3")

        try {
            val proxy = BuildConfig.VOICE_SERVICE_URL.trim()
            val useProxy = proxy.startsWith("https://")

            if (!useProxy) {
                val fromHistory = try {
                    downloadFromHistory(cleanText, apiKey, settings.voiceId.trim())
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    Log.w("ElevenLabsService", "History lookup failed", e)
                    null
                }
                if (fromHistory != null) {
                    val temporary = File.createTempFile("history_", ".tmp", cacheDir)
                    try {
                        temporary.writeBytes(fromHistory)
                        check(temporary.renameTo(outputFile)) { "تعذّر حفظ الصوت" }
                    } finally { temporary.delete() }
                    recordLog(cleanText, success = true, statusCode = 200, message = "تم استرجاع الصوت من سجل ElevenLabs بدون توليد جديد")
                    return@withContext Result.success(outputFile)
                }
            }
            val url = if (useProxy) proxy else "https://api.elevenlabs.io/v1/text-to-speech/${settings.voiceId.trim()}"

            val jsonBody = JSONObject().apply {
                put("text", cleanText)
                put("model_id", settings.modelId.trim())
                put("voice_settings", JSONObject().apply {
                    put("stability", settings.stability.toDouble())
                    put("similarity_boost", settings.similarityBoost.toDouble())
                    put("style", settings.style.toDouble())
                    put("speed", settings.speed.toDouble())
                    put("use_speaker_boost", true)
                })
            }

            val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(url)
                .apply { if (!useProxy) addHeader("xi-api-key", apiKey) }
                .addHeader("Accept", "audio/mpeg")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            val response = try {
                client.newCall(request).execute()
            } catch (e: java.io.IOException) {
                val netMsg = "خطأ في الشبكة أو تعذر الاتصال بالخادم: ${e.message ?: "اتصال غير مستقر"}"
                recordLog(cleanText, success = false, statusCode = null, message = netMsg)
                return@withContext Result.failure(Exception(netMsg))
            }

            response.use {
                if (!response.isSuccessful) {
                    val code = response.code
                    val rawBody = response.body?.string() ?: ""
                    val serverDetail = extractServerDetail(rawBody)
                    val fullErrorMsg = "رمز $code: $serverDetail"
                    recordLog(cleanText, success = false, statusCode = code, message = fullErrorMsg)
                    return@withContext Result.failure(Exception(fullErrorMsg))
                }

                val bytes = response.body?.bytes()
                if (bytes == null || bytes.isEmpty()) {
                    val emptyMsg = "استجابة ElevenLabs لا تحتوي على بيانات صوتية (200 OK فارغ)"
                    recordLog(cleanText, success = false, statusCode = 200, message = emptyMsg)
                    return@withContext Result.failure(Exception(emptyMsg))
                }

                val temporary = File.createTempFile("speech_", ".tmp", cacheDir)
                try {
                    temporary.writeBytes(bytes)
                    check(temporary.renameTo(outputFile)) { "تعذّر حفظ الصوت" }
                } finally { temporary.delete() }

                configManager.recordCharactersUsed(cleanText.length)
                val successMsg = "تم توليد الصوت بنجاح (${cleanText.length} حرف)"
                recordLog(cleanText, success = true, statusCode = 200, message = successMsg)

                Result.success(outputFile)
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.e("ElevenLabsService", "Generation failed", e)
            val unexpectedMsg = e.message ?: "خطأ غير متوقع أثناء التوليد"
            recordLog(cleanText, success = false, statusCode = null, message = unexpectedMsg)
            Result.failure(e)
        }
    }

    }

    private fun extractServerDetail(rawBody: String): String {
        val trimmed = rawBody.trim()
        if (trimmed.isEmpty()) return "استجابة فارغة من الخادم"
        return try {
            val json = JSONObject(trimmed)
            when {
                json.has("detail") -> {
                    val detailObj = json.get("detail")
                    if (detailObj is JSONObject && detailObj.has("message")) {
                        detailObj.getString("message")
                    } else {
                        detailObj.toString()
                    }
                }
                json.has("message") -> json.getString("message")
                else -> trimmed
            }
        } catch (_: Exception) {
            trimmed
        }
    }

    fun clearCache(): Boolean {
        return try {
            cacheDir.listFiles()?.forEach { it.delete() }
            true
        } catch (_: Exception) {
            false
        }
    }
}
