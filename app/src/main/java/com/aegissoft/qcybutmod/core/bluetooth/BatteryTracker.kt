package com.aegissoft.qcybutmod.core.bluetooth

import com.aegissoft.qcybutmod.core.models.QcyBatteryInfo
import com.aegissoft.qcybutmod.core.utils.AegisLogger

enum class BatterySource(val priority: Int) {
    GATT_0008(3),
    GATT_FRAME_2F(3),
    BEACON(2),
    SYSTEM_HFP(1)
}

class BatteryTracker {

    companion object {
        private const val TAG = "BatteryTracker"
        private const val GATT_FRESHNESS_WINDOW_MS = 20_000L
    }

    private var leftLevel: Int = -1
    private var isLeftCharging: Boolean = false

    private var rightLevel: Int = -1
    private var isRightCharging: Boolean = false

    private var caseLevel: Int = -1
    private var isCaseCharging: Boolean = false

    private var systemLevel: Int = -1

    private var lastGattTimestamp: Long = 0L

    val currentBattery: QcyBatteryInfo
        @Synchronized get() = QcyBatteryInfo(
            leftLevel = leftLevel,
            isLeftCharging = isLeftCharging,
            rightLevel = rightLevel,
            isRightCharging = isRightCharging,
            caseLevel = caseLevel,
            isCaseCharging = isCaseCharging,
            systemLevel = systemLevel
        )

    /**
     * Authoritative GATT battery frame update (Characteristic 00000008 or CMD 0x2F).
     * Format:
     * - Byte 0: Left Earbud (bit 7 = charging, bits 0-6 = percent). 0 => absent / in case / unavailable (-1).
     * - Byte 1: Right Earbud (bit 7 = charging, bits 0-6 = percent). 0 => absent / in case / unavailable (-1).
     * - Byte 2: Charging Case (bit 7 = charging, bits 0-6 = percent). 0 => unknown/lid closed → keep last known level.
     */
    @Synchronized
    fun onGattRaw(raw: ByteArray): QcyBatteryInfo {
        if (raw.isEmpty()) return currentBattery

        lastGattTimestamp = System.currentTimeMillis()

        if (raw.size == 1) {
            val b0 = raw[0].toInt() and 0xFF
            val chg = (b0 and 0x80) != 0
            val lvl = b0 and 0x7F
            val valid = if (lvl in 1..100) lvl else -1
            leftLevel = valid
            isLeftCharging = chg
            rightLevel = valid
            isRightCharging = chg
            caseLevel = -1
            isCaseCharging = false
            AegisLogger.d(TAG, "GATT 1-byte battery: lvl=$valid%, charging=$chg")
            return currentBattery
        }

        val bLeft = raw[0].toInt() and 0xFF
        val bRight = if (raw.size > 1) raw[1].toInt() and 0xFF else 0
        val bCase = if (raw.size > 2) raw[2].toInt() and 0xFF else 0

        // Parse Left
        val leftChg = (bLeft and 0x80) != 0
        val leftVal = bLeft and 0x7F
        leftLevel = when {
            leftVal in 1..100 -> leftVal
            leftVal == 101 -> 100
            else -> -1 // 0 means not active/in case/absent
        }
        isLeftCharging = leftChg

        // Parse Right
        val rightChg = (bRight and 0x80) != 0
        val rightVal = bRight and 0x7F
        rightLevel = when {
            rightVal in 1..100 -> rightVal
            rightVal == 101 -> 100
            else -> -1
        }
        isRightCharging = rightChg

        // Parse Case
        val caseChg = (bCase and 0x80) != 0
        val caseVal = bCase and 0x7F
        caseLevel = when {
            caseVal in 1..100 -> caseVal
            caseVal == 101 -> 100
            else -> caseLevel
        }
        isCaseCharging = caseChg

        AegisLogger.i(TAG, "GATT Authoritative Battery: L=${leftLevel}% R=${rightLevel}% Case=${caseLevel}% (sys=${systemLevel}%)")
        return currentBattery
    }

    /**
     * BLE Beacon broadcast battery update.
     * Ignored if GATT reading was received within the last 20 seconds.
     */
    @Synchronized
    fun onBeacon(beacon: QcyBatteryInfo): QcyBatteryInfo {
        val now = System.currentTimeMillis()
        if ((now - lastGattTimestamp) < GATT_FRESHNESS_WINDOW_MS && (leftLevel != -1 || rightLevel != -1)) {
            // Active GATT connection owns L/R; but GATT reports case=0x00 (lid closed/absent)
            // almost always — let the beacon fill in a case level GATT never provided.
            if (caseLevel == -1 && beacon.caseLevel in 1..101) {
                caseLevel = minOf(beacon.caseLevel, 100)
                isCaseCharging = beacon.isCaseCharging
                AegisLogger.d(TAG, "Beacon case-level merged under fresh GATT state: Case=$caseLevel%")
            }
            return currentBattery
        }

        if (beacon.leftLevel in 1..101) {
            leftLevel = minOf(beacon.leftLevel, 100)
            isLeftCharging = beacon.isLeftCharging
        }
        if (beacon.rightLevel in 1..101) {
            rightLevel = minOf(beacon.rightLevel, 100)
            isRightCharging = beacon.isRightCharging
        }
        if (beacon.caseLevel in 1..101) {
            caseLevel = minOf(beacon.caseLevel, 100)
            isCaseCharging = beacon.isCaseCharging
        }

        AegisLogger.d(TAG, "Beacon Battery applied: L=${leftLevel}% R=${rightLevel}% Case=${caseLevel}%")
        return currentBattery
    }

    /**
     * System HFP / BluetoothProfile single battery level (1..100).
     * Kept purely as fallback in [systemLevel]. NEVER overwrites individual earbud levels!
     */
    @Synchronized
    fun onSystemHfp(level: Int): QcyBatteryInfo {
        systemLevel = if (level in 1..100) level else -1
        AegisLogger.d(TAG, "System HFP Battery updated: $systemLevel%")
        return currentBattery
    }

    @Synchronized
    fun reset() {
        leftLevel = -1
        isLeftCharging = false
        rightLevel = -1
        isRightCharging = false
        caseLevel = -1
        isCaseCharging = false
        systemLevel = -1
        lastGattTimestamp = 0L
        AegisLogger.d(TAG, "BatteryTracker reset to disconnected state")
    }
}
