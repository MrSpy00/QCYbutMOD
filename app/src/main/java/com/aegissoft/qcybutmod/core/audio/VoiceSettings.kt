package com.aegissoft.qcybutmod.core.audio

import android.content.Context
import android.content.SharedPreferences

enum class VoiceSource(val id: String, val title: String, val subtitle: String) {
    AEGIS_TR("aegis_tr", "aegis (Türkçe)", "Türkçe sesli asistan konuşur, kulaklık sesi susturulur"),
    FIRMWARE_EN("firmware_en", "Kulaklık (İngilizce)", "Kulaklığın dahili orijinal İngilizce sesleri çalar"),
    OFF("off", "Kapalı", "Tüm sesli bildirimler kapatılır")
}

object VoiceSettings {
    private const val PREFS_NAME = "qcy_voice_settings"
    private const val KEY_VOICE_SOURCE = "voice_source"
    private const val KEY_TTS_RATE = "tts_rate"
    private const val KEY_TTS_VOLUME = "tts_volume"
    private const val KEY_SAVED_FW_LEVEL = "saved_firmware_prompt_level"
    private const val KEY_TRANSLATE_TTS = "translate_tts_headset"
    private const val KEY_TRANSLATE_TTS_ROUTE = "translate_tts_route"
    private const val KEY_TRANSCRIPT_ORDER = "transcript_order"
    private const val KEY_TRANSCRIPT_ROTATE = "transcript_rotate"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getVoiceSource(context: Context): VoiceSource {
        val id = getPrefs(context).getString(KEY_VOICE_SOURCE, VoiceSource.AEGIS_TR.id)
        return VoiceSource.entries.firstOrNull { it.id == id } ?: VoiceSource.AEGIS_TR
    }

    fun setVoiceSource(context: Context, source: VoiceSource) {
        getPrefs(context).edit().putString(KEY_VOICE_SOURCE, source.id).apply()
    }

    fun getTtsRate(context: Context): Float {
        return getPrefs(context).getFloat(KEY_TTS_RATE, 1.02f).coerceIn(0.7f, 1.6f)
    }

    fun setTtsRate(context: Context, rate: Float) {
        getPrefs(context).edit().putFloat(KEY_TTS_RATE, rate.coerceIn(0.7f, 1.6f)).apply()
    }

    fun getTtsVolume(context: Context): Float {
        return getPrefs(context).getFloat(KEY_TTS_VOLUME, 0.85f).coerceIn(0.1f, 1.0f)
    }

    fun setTtsVolume(context: Context, volume: Float) {
        getPrefs(context).edit().putFloat(KEY_TTS_VOLUME, volume.coerceIn(0.1f, 1.0f)).apply()
    }

    fun getSavedFirmwarePromptLevel(context: Context): Int {
        val lvl = getPrefs(context).getInt(KEY_SAVED_FW_LEVEL, 8)
        return if (lvl in 1..15) lvl else 8
    }

    fun setSavedFirmwarePromptLevel(context: Context, level: Int) {
        if (level in 1..15) {
            getPrefs(context).edit().putInt(KEY_SAVED_FW_LEVEL, level).apply()
        }
    }

    fun isTranslateTtsEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_TRANSLATE_TTS, true)
    }

    fun setTranslateTtsEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_TRANSLATE_TTS, enabled).apply()
    }

    // --- Translation TTS output route: headset | phone | off (replaces the on/off toggle) ---
    const val ROUTE_HEADSET = "headset"
    const val ROUTE_PHONE = "phone"
    const val ROUTE_OFF = "off"

    fun getTranslateTtsRoute(context: Context): String {
        val r = getPrefs(context).getString(KEY_TRANSLATE_TTS_ROUTE, null)
        return when (r) {
            ROUTE_HEADSET, ROUTE_PHONE, ROUTE_OFF -> r
            // migrate from the legacy boolean (true=headset, false=off)
            else -> if (isTranslateTtsEnabled(context)) ROUTE_HEADSET else ROUTE_OFF
        }
    }

    fun setTranslateTtsRoute(context: Context, route: String) {
        if (route != ROUTE_HEADSET && route != ROUTE_PHONE && route != ROUTE_OFF) return
        getPrefs(context).edit()
            .putString(KEY_TRANSLATE_TTS_ROUTE, route)
            .putBoolean(KEY_TRANSLATE_TTS, route == ROUTE_HEADSET)
            .apply()
    }

    /** Cycles headset -> phone -> off -> headset, persisting and returning the new route. */
    fun cycleTranslateTtsRoute(context: Context): String {
        val next = when (getTranslateTtsRoute(context)) {
            ROUTE_HEADSET -> ROUTE_PHONE
            ROUTE_PHONE -> ROUTE_OFF
            else -> ROUTE_HEADSET
        }
        setTranslateTtsRoute(context, next)
        return next
    }

    // --- Transcript bubble display customization ---
    const val ORDER_SOURCE_FIRST = "source_first"
    const val ORDER_TARGET_FIRST = "target_first"

    fun getTranscriptOrder(context: Context): String {
        val v = getPrefs(context).getString(KEY_TRANSCRIPT_ORDER, null)
        return if (v == ORDER_TARGET_FIRST) ORDER_TARGET_FIRST else ORDER_SOURCE_FIRST
    }

    fun setTranscriptOrder(context: Context, order: String) {
        if (order == ORDER_SOURCE_FIRST || order == ORDER_TARGET_FIRST) {
            getPrefs(context).edit().putString(KEY_TRANSCRIPT_ORDER, order).apply()
        }
    }

    // Facing mode: rotate transcript bubbles 180 deg so the person opposite the
    // phone holder can read the screen right-side up.
    fun getTranscriptRotate(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_TRANSCRIPT_ROTATE, false)
    }

    fun setTranscriptRotate(context: Context, rotate: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_TRANSCRIPT_ROTATE, rotate).apply()
    }
}
