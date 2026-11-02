package com.aegissoft.qcybutmod.core.translate

sealed class PrepareState {
    object Idle : PrepareState()
    data class Downloading(val progressPercent: Int) : PrepareState()
    object Ready : PrepareState()
    data class Error(val message: String) : PrepareState()
}

interface TranslationEngine {
    val engineName: String
    val isOffline: Boolean

    suspend fun prepare(sourceLang: SupportedLanguage, targetLang: SupportedLanguage): PrepareState
    suspend fun translate(text: String, sourceLang: SupportedLanguage, targetLang: SupportedLanguage): String
    fun close()
}
