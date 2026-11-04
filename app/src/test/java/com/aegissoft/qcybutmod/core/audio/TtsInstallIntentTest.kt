package com.aegissoft.qcybutmod.core.audio

import android.speech.tts.TextToSpeech
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FemaleVoiceInstaller adım sırası ve resolve semantiği (JVM, Android bağımlılığı yok).
 * Aksiyon sabitleri derleme anında gömüldüğü için mockable android.jar'dan okunmaz.
 */
class TtsInstallIntentTest {

    @Test
    fun orderIsInstallThenSettingsThenMarket() {
        assertEquals(3, FemaleVoiceInstaller.order.size)
        assertEquals(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA, FemaleVoiceInstaller.order[0])
        assertEquals("android.settings.TTS_SETTINGS", FemaleVoiceInstaller.order[1])
        assertTrue(FemaleVoiceInstaller.MARKET_STEP.startsWith("market://"))
        assertEquals(FemaleVoiceInstaller.MARKET_STEP, FemaleVoiceInstaller.order[2])
    }

    @Test
    fun resolveReturnsFirstResolvable() {
        val hit = FemaleVoiceInstaller.resolve { true }
        assertEquals(FemaleVoiceInstaller.order[0], hit)
    }

    @Test
    fun resolveSkipsUnresolvableAndPicksLater() {
        val wanted = FemaleVoiceInstaller.order[1]
        val hit = FemaleVoiceInstaller.resolve { it == wanted }
        assertEquals(wanted, hit)
    }

    @Test
    fun resolveReturnsNullWhenNothingResolves() {
        assertNull(FemaleVoiceInstaller.resolve { false })
    }

    @Test
    fun resolveSwallowsExceptionsAndContinues() {
        // ilk aday patlar → sıradaki adaya geçilmeli, istisna dışarı taşmamalı
        var seen = 0
        val hit = FemaleVoiceInstaller.resolve {
            seen++
            if (seen == 1) throw IllegalStateException("resolver failed")
            true
        }
        assertEquals(FemaleVoiceInstaller.order[1], hit)
        // tüm adaylar patarsa → null, yine de fırlatmadan
        assertNull(FemaleVoiceInstaller.resolve { throw RuntimeException("boom") })
    }

    @Test
    fun guidanceHasThreeLines() {
        assertEquals(3, FemaleVoiceInstaller.guidance.size)
        assertTrue(FemaleVoiceInstaller.guidance[1].contains("Türkçe"))
    }
}
