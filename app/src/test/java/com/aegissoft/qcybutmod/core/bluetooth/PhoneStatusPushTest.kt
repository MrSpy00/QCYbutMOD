package com.aegissoft.qcybutmod.core.bluetooth

import com.aegissoft.qcybutmod.core.bluetooth.QcyProtocol.ConnectedPhone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneStatusPushTest {

    @Test
    fun unknownMacAppendedAsSecondDevice() {
        val seed = listOf(ConnectedPhone("AA:BB:CC:DD:EE:FF", "Bu Cihaz (Birincil)", true, false))
        val out = applyPhoneStatusPush(seed, "11:22:33:44:55:66", isAudioActive = true)
        assertEquals(2, out.size)
        assertEquals("11:22:33:44:55:66", out[1].mac)
        assertTrue(out[1].isAudioActive)
    }

    @Test
    fun knownMacOnlyUpdatesFlags() {
        val seed = listOf(ConnectedPhone("AA:BB:CC:DD:EE:FF", "Telefonum", true, false))
        val out = applyPhoneStatusPush(seed, "AA:BB:CC:DD:EE:FF", isAudioActive = true)
        assertEquals(1, out.size)
        assertEquals("Telefonum", out[0].name)
        assertTrue(out[0].isAudioActive)
    }
}
