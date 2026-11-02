package com.aegissoft.qcybutmod.core.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards for connect-target filtering: connect() and reconnectSelectedDevice()
 * reject any device failing isQcyDeviceName before touching state.
 */
class BleAdvertisementParserTest {

    @Test
    fun qcyNamesAreAccepted() {
        assertTrue(BleAdvertisementParser.isQcyDeviceName("QCY MeloBuds Pro"))
        assertTrue(BleAdvertisementParser.isQcyDeviceName("QCY H3"))
        assertTrue(BleAdvertisementParser.isQcyDeviceName("MeloBuds ANC"))
        assertTrue(BleAdvertisementParser.isQcyDeviceName("QCY-HT1"))
    }

    @Test
    fun nonQcyNamesAreRejected() {
        assertFalse(BleAdvertisementParser.isQcyDeviceName("REDMI Buds 8 Lite"))
        assertFalse(BleAdvertisementParser.isQcyDeviceName("Huawei FreeBuds 5i"))
        assertFalse(BleAdvertisementParser.isQcyDeviceName("Peugeot Mirror"))
        assertFalse(BleAdvertisementParser.isQcyDeviceName(null))
        assertFalse(BleAdvertisementParser.isQcyDeviceName(""))
        assertFalse(BleAdvertisementParser.isQcyDeviceName("   "))
    }

    @Test
    fun unknownNameYieldsVendorIdZero() {
        // eşleşmeyen isim → 19786 DEĞİL (Pro yeteneklerini miras almasın)
        assertEquals(0, BleAdvertisementParser.guessVendorIdFromName("My Buds"))
    }
}
