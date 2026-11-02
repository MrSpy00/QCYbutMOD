package com.aegissoft.qcybutmod.core.bluetooth

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class QcyProtocolTest {

    private fun b(vararg v: Int) = ByteArray(v.size) { i -> v[i].toByte() }

    @Test
    fun toneVolume_mute_usesOfficialTwoByteForm() {
        // Official ToneVolumeManagerActivity else-branch (vid != 16737):
        // setBytesValue(mac, 29, new byte[]{value, 0}) -> FF 04 1D 02 00 00
        // The old single-byte form FF 03 1D 01 00 was ignored by MeloBuds Pro firmware.
        assertArrayEquals(
            b(0xFF, 0x04, 0x1D, 0x02, 0x00, 0x00),
            QcyProtocol.createToneVolumeCommand(0)
        )
    }

    @Test
    fun toneVolume_level6_keepsSecondByteZero() {
        assertArrayEquals(
            b(0xFF, 0x04, 0x1D, 0x02, 0x06, 0x00),
            QcyProtocol.createToneVolumeCommand(6)
        )
    }

    @Test
    fun toneVolume_clampsToValidRange() {
        assertArrayEquals(
            b(0xFF, 0x04, 0x1D, 0x02, 0x0F, 0x00),
            QcyProtocol.createToneVolumeCommand(99)
        )
        assertArrayEquals(
            b(0xFF, 0x04, 0x1D, 0x02, 0x00, 0x00),
            QcyProtocol.createToneVolumeCommand(-5)
        )
    }

    @Test
    fun requestAllData_forToneVolume_matchesLiveDeviceLog() {
        // Live log TX: FF 03 FE 01 1D (query tone volume after write)
        assertArrayEquals(
            b(0xFF, 0x03, 0xFE, 0x01, 0x1D),
            QcyProtocol.packFrame(QcyProtocol.CMD_REQUEST_ALL_DATA, 0x1D.toByte())
        )
    }
}
