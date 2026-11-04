package com.aegissoft.qcybutmod.core.audio

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.speech.tts.TextToSpeech

/**
 * Tek dokunuşla Google TTS Türkçe ses paketi indirme akışı.
 *
 * ACTION_CHECK_TTS_DATA sonuç tabanlıdır (arka planda kapanıp yalnızca Activity
 * sonucu döndürür, kullanıcıya görünür bir ekran göstermez) — bu yüzden aday değil.
 * Kart yalnızca kadın sesi yokken göründüğü için kontrol adımı zaten atlanmıştır.
 */
object FemaleVoiceInstaller {
    /** Market adımı için sentetik adım etiketi (Intent aksiyonu değil). */
    const val MARKET_STEP = "market://details?id=com.google.android.tts"

    /**
     * Sistem TTS ayarları ekranı. Sabit API 36 android.jar'da `Settings` altında
     * görünmediği için belgelenmiş aksiyon metni literal olarak kullanılıyor.
     */
    const val TTS_SETTINGS_STEP = "android.settings.TTS_SETTINGS"

    /**
     * Sıra: en doğrudan ekran önce. Aksiyon sabitleri derleme anında string olarak
     * gömüldüğü için bu liste JVM testlerinde Android'e bağımlı olmadan doğrulanabilir.
     */
    val order: List<String> = listOf(
        TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA,
        TTS_SETTINGS_STEP,
        MARKET_STEP
    )

    /** Adım etiketinden açılacak Intent. */
    fun intentFor(step: String): Intent = when {
        step == TTS_SETTINGS_STEP -> Intent(TTS_SETTINGS_STEP)
        step.startsWith("market:") -> Intent(Intent.ACTION_VIEW, Uri.parse(step))
        else -> Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
    }

    /**
     * İlk çözebilen adımı döndürür; hiçbiri çözülmezse null.
     * resolve asla fırlatmaz — istisnalar yutulur ve sıradaki adaya geçilir.
     */
    fun resolve(resolve: (String) -> Boolean): String? =
        order.firstOrNull { runCatching { resolve(it) }.getOrDefault(false) }

    /**
     * Çözülebilen en doğrudan ekranı açar.
     * @return false = hiçbir aday açılamadı (yönlendirme metni gösterilmeli).
     */
    fun open(context: Context): Boolean {
        val step = resolve { s ->
            context.packageManager.resolveActivity(intentFor(s), PackageManager.MATCH_DEFAULT_ONLY) != null
        } ?: return false
        return runCatching {
            val intent = intentFor(step)
            context.startActivity(
                if (step.startsWith("market:")) Intent.createChooser(intent, "Uygulama seçin") else intent
            )
            true
        }.getOrDefault(false)
    }

    /** Elle takip için 3 satır kılavuz (TTS ayarları ekranı hedeflenirse gösterilir). */
    val guidance: List<String> = listOf(
        "Google TTS (Metin okuma) motorunu seçin",
        "Ses verilerini yükle → Türkçe → Kadın sesi indirin",
        "Uygulamaya dönünce kart hazır görünmelidir"
    )
}
