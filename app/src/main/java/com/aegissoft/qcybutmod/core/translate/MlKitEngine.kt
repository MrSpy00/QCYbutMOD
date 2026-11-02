package com.aegissoft.qcybutmod.core.translate

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MlKitEngine : TranslationEngine {

    companion object {
        private const val TAG = "MlKitEngine"
    }

    override val engineName: String = "Google ML Kit (Cihaz İçi / Çevrimdışı)"
    override val isOffline: Boolean = true

    private val modelManager = RemoteModelManager.getInstance()
    private val translatorCache = ConcurrentHashMap<String, Translator>()

    private fun getCacheKey(source: String, target: String): String = "$source->$target"

    override suspend fun prepare(sourceLang: SupportedLanguage, targetLang: SupportedLanguage): PrepareState {
        return try {
            val key = getCacheKey(sourceLang.code, targetLang.code)
            var translator = translatorCache[key]

            if (translator == null) {
                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(sourceLang.code)
                    .setTargetLanguage(targetLang.code)
                    .build()
                translator = Translation.getClient(options)
                translatorCache[key] = translator
            }

            // Check if models need to be downloaded
            val isSourceDownloaded = checkModelDownloaded(sourceLang.code)
            val isTargetDownloaded = checkModelDownloaded(targetLang.code)

            if (!isSourceDownloaded || !isTargetDownloaded) {
                Log.d(TAG, "Downloading language models: ${sourceLang.code} / ${targetLang.code}")
                val conditions = DownloadConditions.Builder().build()
                translator.downloadModelIfNeeded(conditions).awaitTask()
            }

            PrepareState.Ready
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare ML Kit models", e)
            PrepareState.Error(e.localizedMessage ?: "Model hazırlama hatası")
        }
    }

    private suspend fun checkModelDownloaded(langCode: String): Boolean {
        return try {
            val model = TranslateRemoteModel.Builder(langCode).build()
            modelManager.isModelDownloaded(model).awaitTask()
        } catch (e: Exception) {
            Log.w(TAG, "Error checking model download status for $langCode: ${e.message}")
            false
        }
    }

    override suspend fun translate(
        text: String,
        sourceLang: SupportedLanguage,
        targetLang: SupportedLanguage
    ): String {
        if (text.isBlank()) return ""
        val key = getCacheKey(sourceLang.code, targetLang.code)
        var translator = translatorCache[key]

        if (translator == null) {
            val prep = prepare(sourceLang, targetLang)
            if (prep is PrepareState.Error) {
                throw IllegalStateException("Model hazır değil: ${prep.message}")
            }
            translator = translatorCache[key]
                ?: throw IllegalStateException("Translator başlatılamadı")
        }

        return try {
            translator.translate(text).awaitTask()
        } catch (e: Exception) {
            Log.e(TAG, "ML Kit translation failed for: $text", e)
            throw e
        }
    }

    override fun close() {
        try {
            for ((_, translator) in translatorCache) {
                translator.close()
            }
            translatorCache.clear()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing translators", e)
        }
    }
}

/**
 * Lightweight await extension for Google Play Services Task without external dependency.
 */
suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result ->
        if (continuation.isActive) {
            continuation.resume(result)
        }
    }
    addOnFailureListener { exception ->
        if (continuation.isActive) {
            continuation.resumeWithException(exception)
        }
    }
    addOnCanceledListener {
        if (continuation.isActive) {
            continuation.cancel()
        }
    }
}
