package com.aegissoft.qcybutmod.core.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptMutePolicyTest {
    @Test fun remutesWhenNotFirmwareAndVolumeRaised() { assertTrue(PromptMutePolicy.shouldRemute("aegis_tr", 6, 10_000, 0)) }
    @Test fun remutesWhenSourceOff() { assertTrue(PromptMutePolicy.shouldRemute("off", 6, 10_000, 0)) }
    @Test fun neverRemutesInFirmwareMode() { assertFalse(PromptMutePolicy.shouldRemute("firmware_en", 6, 10_000, 0)) }
    @Test fun neverWhenVolumeZero() { assertFalse(PromptMutePolicy.shouldRemute("aegis_tr", 0, 10_000, 0)) }
    @Test fun debounceSuppressesEchoStorm() { assertFalse(PromptMutePolicy.shouldRemute("aegis_tr", 6, 1_500, 0)) }
    @Test fun debounceAllowsAfterWindow() { assertTrue(PromptMutePolicy.shouldRemute("aegis_tr", 6, 3_000, 0)) }
    @Test fun recheckDelayCoversDebounceWindow() {
        assertEquals(1600L, PromptMutePolicy.recheckDelayMs(now = 500L, lastRemuteAt = 0L)) // 2000-500+100
        assertEquals(100L, PromptMutePolicy.recheckDelayMs(now = 5000L, lastRemuteAt = 0L)) // pencere çoktan doldu
    }
    @Test fun assistantGestureAllowedOnlyInFirmwareMode() {
        assertFalse(PromptMutePolicy.shouldAllowVoiceAssistantGesture("aegis_tr"))
        assertFalse(PromptMutePolicy.shouldAllowVoiceAssistantGesture("off"))
        assertTrue(PromptMutePolicy.shouldAllowVoiceAssistantGesture("firmware_en"))
    }
    @Test fun remapAssistantGesturesClearsAssistantKey() {
        val out = PromptMutePolicy.remapAssistantGestures(mapOf(1 to 0, 5 to 4), assistantFuncId = 4, noneFuncId = 0)
        assertEquals(mapOf(1 to 0, 5 to 0), out)                    // Üç Dokunma → NONE
        val kept = PromptMutePolicy.remapAssistantGestures(mapOf(5 to 4), 4, 4) // firmware_en: none==assistant → değişmez
        assertEquals(mapOf(5 to 4), kept)
    }
}
