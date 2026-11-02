package com.aegissoft.qcybutmod.core.bluetooth

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun phoneListResponseEmptyPayloadKeepsState() {
        assertNull(QcyProtocol.parsePhoneListResponse(ByteArray(0)))
    }

    @Test
    fun phoneListResponseCountZeroClears() {
        // payload[0]=0 (kayıt sayısı) → açık boş yanıt
        val r = QcyProtocol.parsePhoneListResponse(byteArrayOf(0))
        assertTrue(r != null && r.isEmpty())
    }

    @Test
    fun multipointOffFrameMatchesOfficial() {
        // resmi uygulama: PairPhoneConfigActivity :487 → FF 03 24 01 00
        assertArrayEquals(
            b(0xFF, 0x03, 0x24, 0x01, 0x00),
            QcyProtocol.createMultipointCommand(false)
        )
    }

    @Test
    fun phoneListResponseOneRecordParses() {
        // gerçek tek kayıt (parsePairPhoneList düzeni): [count=1, len, status=2, mac×6, 'T','e','s','t',0]
        val name = "Test".toByteArray(); val len = (1 + 6 + name.size + 1).toByte()
        val p = byteArrayOf(1, len, 2, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66) + name + 0
        val r = QcyProtocol.parsePhoneListResponse(p)
        assertEquals(1, r!!.size); assertEquals("Test", r[0].name)
    }
}
