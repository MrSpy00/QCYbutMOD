package com.aegissoft.qcybutmod.core.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityParserTest {
    private fun n20Features() = mapOf(
        "anc" to mapOf("modes" to listOf<Any>()),
        "find_earphone" to true,
        "eq" to mapOf("bands" to 10),
        "settings" to listOf(mapOf("name" to "Dual Device Connection", "cmdid" to 36))
    )
    @Test fun n20FullFeatureSet() {
        val c = CapabilityParser.parse(32884, n20Features(), ldacSupported = false)
        assertTrue(c.hasAnc); assertTrue(c.hasFind); assertTrue(c.hasMultipoint)
        assertEquals(TouchSides.BOTH, c.touchSides); assertEquals(10, c.eqBands)
    }
    @Test fun h4TouchIsRightOnly() {
        val c = CapabilityParser.parse(16849, mapOf("anc" to mapOf<Any, Any>()), ldacSupported = true)
        assertEquals(TouchSides.RIGHT, c.touchSides); assertTrue(c.hasLdac)
    }
    @Test fun longPressDetectedFromKeyFunction() {
        val feats = mapOf("key_function" to mapOf("events" to listOf(mapOf("name" to "Long press", "functions" to listOf("Not work")))))
        assertTrue(CapabilityParser.parse(1, feats, false).hasLongPress)
    }
    @Test fun noKeyFunctionDefaultsLongPressShown() {
        assertTrue(CapabilityParser.parse(19786, null, true).hasLongPress) // bilinmiyorsa göster (0x2B canlı kanıt)
    }
    @Test fun noMultipointSettingMeansDisabled() {
        val c = CapabilityParser.parse(1, mapOf("anc" to mapOf<Any, Any>(), "settings" to listOf(mapOf("name" to "Sleeping Mode", "cmdid" to 16))), false)
        assertFalse(c.hasMultipoint)
    }
    @Test fun missingSettingsKeyKeepsMultipointOpen() {
        val caps = CapabilityParser.parse(32884, mapOf("anc" to Any()), false) // settings anahtarı YOK
        assertTrue(caps.hasMultipoint)
    }
    @Test fun settingsWithoutCmdid36DisablesMultipoint() {
        val caps = CapabilityParser.parse(16849, mapOf("settings" to emptyList<Any>()), false)
        assertFalse(caps.hasMultipoint)
    }

    @Test fun runtimeKeyMappingUnlocksLongPress() {
        val features = mapOf("key_function" to mapOf("events" to listOf(mapOf("name" to "Touch"))))
        val caps = CapabilityParser.parse(19786, features, false)
        assertFalse(caps.hasLongPress)                                  // products.json olay listesinde "Long press" yok
        assertTrue(caps.longPressAvailable(mapOf(7 to 4)))             // canlı 0x2B okuması 7/8 getiriyor
        assertFalse(caps.longPressAvailable(emptyMap()))
    }
}
