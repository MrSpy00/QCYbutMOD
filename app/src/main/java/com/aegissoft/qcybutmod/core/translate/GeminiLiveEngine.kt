package com.aegissoft.qcybutmod.core.translate

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiLiveEngine(private val context: Context) : TranslationEngine {

    companion object {
        private const val TAG = "GeminiLiveEngine"
        private const val PREFS_NAME = "qcy_translate_prefs"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val DEFAULT_MODEL = "gemini-2.5-flash"

        fun getApiKey(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(KEY_GEMINI_API_KEY, "") ?: ""
        }

        fun setApiKey(context: Context, key: String) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_GEMINI_API_KEY, key.trim()).apply()
        }
    }

    override val engineName: String = "Google Gemini Live AI (Bulut)"
    override val isOffline: Boolean = false

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun prepare(sourceLang: SupportedLanguage, targetLang: SupportedLanguage): PrepareState {
        val apiKey = getApiKey(context)
        if (apiKey.isBlank()) {
            return PrepareState.Error("Gemini API anahtarı ayarlanmamış. Lütfen Ayarlar bölümünden anahtarınızı kaydedin.")
        }
        return PrepareState.Ready
    }

    override suspend fun translate(
        text: String,
        sourceLang: SupportedLanguage,
        targetLang: SupportedLanguage
    ): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey(context)
        if (apiKey.isBlank()) {
            throw IllegalStateException("Gemini API anahtarı bulunamadı. Lütfen Ayarlar'dan ekleyin.")
        }

        val prompt = "You are an ultra-fast, direct speech translator for smart earbuds. " +
                "Translate the following spoken message from ${sourceLang.nameTr} (${sourceLang.code}) " +
                "into ${targetLang.nameTr} (${targetLang.code}). " +
                "Rules: Output ONLY the translated phrase with natural spoken tone, no notes, no explanations, no quotes.\n\n" +
                "Text: $text"

        val jsonBody = JSONObject().apply {
            val contents = JSONArray().apply {
                val part = JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                }
                put(part)
            }
            put("contents", contents)

            val config = JSONObject().apply {
                put("temperature", 0.1)
                put("maxOutputTokens", 256)
            }
            put("generationConfig", config)
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$DEFAULT_MODEL:generateContent?key=$apiKey"
        val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val responseStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e(TAG, "Gemini API error code ${response.code}: $responseStr")
                    throw IllegalStateException("Gemini hatası: HTTP ${response.code}")
                }

                val root = JSONObject(responseStr)
                val candidates = root.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val contentObj = firstCandidate.optJSONObject("content")
                    val parts = contentObj?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        val resultText = parts.getJSONObject(0).optString("text", "")
                        return@withContext resultText.trim()
                    }
                }
                return@withContext ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error invoking Gemini API", e)
            throw e
        }
    }

    override fun close() {
        httpClient.dispatcher.cancelAll()
    }
}
