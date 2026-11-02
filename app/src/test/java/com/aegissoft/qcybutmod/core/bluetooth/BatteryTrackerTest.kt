package com.aegissoft.qcybutmod.core.bluetooth

import com.aegissoft.qcybutmod.core.models.QcyBatteryInfo
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class BatteryTrackerTest {

    private lateinit var tracker: BatteryTracker

    @Before
    fun setUp() {
        tracker = BatteryTracker()
    }

    @Test
    fun testLiveHexParsing_23_45_00() {
        // Hex: 0x23 = 35%, 0x45 = 69%, 0x00 = unavailable/not in case
        val raw = byteArrayOf(0x23, 0x45, 0x00)
        val batt = tracker.onGattRaw(raw)

        assertEquals(35, batt.leftLevel)
        assertFalse(batt.isLeftCharging)
        assertEquals(69, batt.rightLevel)
        assertFalse(batt.isRightCharging)
        assertEquals(-1, batt.caseLevel)
        assertFalse(batt.isCaseCharging)
    }

    @Test
    fun testChargingBits_A3_45_64() {
        // 0xA3 = 0x80 (charging) | 0x23 (35%)
        // 0x45 = 69%
        // 0x64 = 100%
        val raw = byteArrayOf(0xA3.toByte(), 0x45, 0x64)
        val batt = tracker.onGattRaw(raw)

        assertEquals(35, batt.leftLevel)
        assertTrue(batt.isLeftCharging)
        assertEquals(69, batt.rightLevel)
        assertFalse(batt.isRightCharging)
        assertEquals(100, batt.caseLevel)
        assertFalse(batt.isCaseCharging)
    }

    @Test
    fun testEarbudRemovedOrInCaseZeroes_NeverKeepsStale() {
        // First both earbuds are out
        tracker.onGattRaw(byteArrayOf(0x23, 0x45, 0x00))
        assertEquals(35, tracker.currentBattery.leftLevel)
        assertEquals(69, tracker.currentBattery.rightLevel)

        // Now left earbud goes to case / disconnects (raw byte 0x00)
        val updated = tracker.onGattRaw(byteArrayOf(0x00, 0x45, 0x00))
        assertEquals(-1, updated.leftLevel)
        assertFalse(updated.isLeftCharging)
        assertEquals(69, updated.rightLevel)
    }

    @Test
    fun testSystemHfpDoesNotOverwriteLeftAndRight() {
        tracker.onGattRaw(byteArrayOf(0x23, 0x45, 0x00))
        assertEquals(35, tracker.currentBattery.leftLevel)
        assertEquals(69, tracker.currentBattery.rightLevel)

        // System HFP sends 50%
        val batt = tracker.onSystemHfp(50)
        assertEquals(50, batt.systemLevel)
        // Left and Right must remain separate, NOT overwritten by 50!
        assertEquals(35, batt.leftLevel)
        assertEquals(69, batt.rightLevel)
    }

    @Test
    fun testGattOverridesBeaconWithinWindow() {
        // GATT reading arrives (case unknown)
        tracker.onGattRaw(byteArrayOf(0x23, 0x45, 0x00))

        // Beacon reading with old/different data arrives immediately
        val beacon = QcyBatteryInfo(leftLevel = 80, rightLevel = 80, caseLevel = 50)
        val batt = tracker.onBeacon(beacon)

        // Must still keep GATT authoritative L/R values...
        assertEquals(35, batt.leftLevel)
        assertEquals(69, batt.rightLevel)
        // ...but the beacon's case level is merged (GATT reported case as absent)
        assertEquals(50, batt.caseLevel)
    }

    @Test
    fun testFreshGattBeaconCanStillFillMissingCaseLevel() {
        // GATT reports case=0x00 (lid closed) → caseLevel -1, but GATT is fresh
        tracker.onGattRaw(byteArrayOf(0x23, 0x45, 0x00))
        assertEquals(-1, tracker.currentBattery.caseLevel)

        // Beacon advertises a real case level — must merge even inside the 20s window
        val beacon = QcyBatteryInfo(leftLevel = 11, rightLevel = 43, caseLevel = 77)
        val batt = tracker.onBeacon(beacon)

        assertEquals(77, batt.caseLevel)
        // L/R still owned by fresh GATT — beacon must NOT overwrite them
        assertEquals(35, batt.leftLevel)
        assertEquals(69, batt.rightLevel)
    }

    @Test
    fun testFreshGattBeaconCannotTouchAnythingWhenCaseAlreadyKnown() {
        tracker.onGattRaw(byteArrayOf(0x23, 0x45, 0x64)) // case = 100%
        val beacon = QcyBatteryInfo(leftLevel = 11, rightLevel = 43, caseLevel = 77)
        val batt = tracker.onBeacon(beacon)

        assertEquals(100, batt.caseLevel)
        assertEquals(35, batt.leftLevel)
        assertEquals(69, batt.rightLevel)
    }

    @Test
    fun testResetClearsEverything() {
        tracker.onGattRaw(byteArrayOf(0x23, 0x45, 0x64))
        tracker.onSystemHfp(50)
        assertEquals(35, tracker.currentBattery.leftLevel)

        tracker.reset()
        val resetBatt = tracker.currentBattery
        assertEquals(-1, resetBatt.leftLevel)
        assertEquals(-1, resetBatt.rightLevel)
        assertEquals(-1, resetBatt.caseLevel)
        assertEquals(-1, resetBatt.systemLevel)
        assertFalse(resetBatt.hasValidBattery)
    }

    @Test
    fun testCaseLevelSurvivesZeroGattCaseByte() {
        tracker.onGattRaw(byteArrayOf(0x23, 0x45, 0x64))       // case 100 biliniyor
        val updated = tracker.onGattRaw(byteArrayOf(0x23, 0x45, 0x00)) // 0x00 = bilinmiyor → SİLME
        assertEquals(100, updated.caseLevel)
    }

    @Test
    fun testChargingBitKeptWhenLevelInvalid() {
        val batt = tracker.onGattRaw(byteArrayOf(0x80.toByte(), 0x45, 0x00))
        assertEquals(-1, batt.leftLevel)       // lvl 0 → geçersiz
        assertTrue(batt.isLeftCharging)        // şarj biti korunur
    }

    @Test
    fun testBeaconCase101NormalizesTo100() {
        val batt = tracker.onBeacon(QcyBatteryInfo(leftLevel = 80, rightLevel = 80, caseLevel = 101))
        assertEquals(100, batt.caseLevel)
    }

    @Test
    fun testCaseOnlyCountsAsValidBattery() {
        tracker.onGattRaw(byteArrayOf(0x00, 0x00, 0x64))
        assertTrue(tracker.currentBattery.hasValidBattery)
    }

    @Test
    fun testCaseSurvives1ByteGattPayload() {
        tracker.onGattRaw(byteArrayOf(0x23, 0x45, 0x64)) // case = 100%
        tracker.onGattRaw(byteArrayOf(0x80.toByte()))     // 1-byte frame: case hakkında bilgi taşımaz
        assertEquals(100, tracker.currentBattery.caseLevel)
    }
}
