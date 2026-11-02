package com.aegissoft.qcybutmod.core.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceGenderResolverTest {
    @Test fun femalePicksNameContainingFemale() {
        val voices = listOf(VoiceInfo("tr-tr-x-trm-network", 400), VoiceInfo("tr-tr-x-trf-network", 450))
        assertEquals("tr-tr-x-trf-network", VoiceGenderResolver.pickVoiceName(voices, "female"))
    }
    @Test fun malePicksNonFemaleMatch() {
        val voices = listOf(VoiceInfo("tr-tr-x-trf-network", 450), VoiceInfo("tr-tr-x-trm-local", 300))
        assertEquals("tr-tr-x-trm-local", VoiceGenderResolver.pickVoiceName(voices, "male"))
    }
    @Test fun fallbackReturnsHighestQuality() {
        val voices = listOf(VoiceInfo("a", 100), VoiceInfo("b", 300))
        assertEquals("b", VoiceGenderResolver.pickVoiceName(voices, "female"))
    }
    @Test fun pitchDefaults() { assertEquals(1.15f, VoiceGenderResolver.pitchFor("female")); assertEquals(0.88f, VoiceGenderResolver.pitchFor("male")); assertEquals(1.0f, VoiceGenderResolver.pitchFor("x")) }
}
