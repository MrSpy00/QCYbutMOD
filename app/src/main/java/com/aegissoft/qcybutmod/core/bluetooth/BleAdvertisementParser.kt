package com.aegissoft.qcybutmod.core.bluetooth

import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import com.aegissoft.qcybutmod.core.models.ProductDatabase
import com.aegissoft.qcybutmod.core.models.ProductInfo
import com.aegissoft.qcybutmod.core.models.QcyBatteryInfo

data class DiscoveredQcyDevice(
    val deviceName: String,
    val bleAddress: String,
    val classicMac: String?,
    val otherMac: String? = null,
    val vendorId: Int,
    val productInfo: ProductInfo,
    val battery: QcyBatteryInfo,
    val rssi: Int,
    val isBonded: Boolean = false,
    val isReachable: Boolean = true,
    val isAudioConnected: Boolean = false,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)

object BleAdvertisementParser {

    fun parseScanResult(scanResult: ScanResult): DiscoveredQcyDevice? {
        val record: ScanRecord = scanResult.scanRecord ?: return null
        val device = scanResult.device ?: return null
        val rssi = scanResult.rssi

        // 1. Check all official QCY Manufacturer Data IDs:
        // 0x521C (21020), 0x522C (21036), 0x05D6 (1494), plus reversed endian variants
        val candidateIds = intArrayOf(
            QcyProtocol.QCY_COMPANY_ID,
            QcyProtocol.QCY_COMPANY_ID_21036,
            QcyProtocol.QCY_COMPANY_ID_1494,
            0x1C52,
            0x2C52
        )
        for (id in candidateIds) {
            val mfgData = record.getManufacturerSpecificData(id)
            if (mfgData != null && mfgData.size >= 8) {
                val parsed = parseManufacturerPayload(
                    data = mfgData,
                    bleAddress = device.address,
                    rawDeviceName = device.name ?: record.deviceName ?: "QCY Earbuds",
                    rssi = rssi
                )
                if (parsed != null) return parsed
            }
        }

        // Also inspect any entry in manufacturerSpecificData sparse array that has >= 17 bytes
        val sparse = record.manufacturerSpecificData
        if (sparse != null) {
            for (i in 0 until sparse.size()) {
                val key = sparse.keyAt(i)
                val mfgData = sparse.valueAt(i)
                if (key == 0x521C || key == 0x522C || (mfgData != null && mfgData.size >= 17)) {
                    val parsed = parseManufacturerPayload(
                        data = mfgData,
                        bleAddress = device.address,
                        rawDeviceName = device.name ?: record.deviceName ?: "QCY Earbuds",
                        rssi = rssi,
                        requireKnownVendorId = (key != 0x521C && key != 0x522C)
                    )
                    if (parsed != null) return parsed
                }
            }
        }

        // 2. Fallback: Parse raw scan record bytes for [0x1C, 0x52], [0x52, 0x1C], [0x2C, 0x52], [0x52, 0x2C]
        val rawBytes = record.bytes
        if (rawBytes != null) {
            val qcyIndex = findQcyBeaconHeader(rawBytes)
            if (qcyIndex != -1 && rawBytes.size >= qcyIndex + 8) {
                val payloadLen = ((rawBytes[qcyIndex - 3].toInt() and 0xFF) - 3).coerceAtMost(rawBytes.size - qcyIndex)
                if (payloadLen >= 8) {
                    val data = ByteArray(payloadLen)
                    System.arraycopy(rawBytes, qcyIndex, data, 0, payloadLen)
                    val parsed = parseManufacturerPayload(
                        data = data,
                        bleAddress = device.address,
                        rawDeviceName = device.name ?: record.deviceName ?: "QCY Earbuds",
                        rssi = rssi
                    )
                    if (parsed != null) return parsed
                }
            }
        }

        // 3. Generic QCY device matching by Bluetooth Device Name prefix or service UUID 0000A001
        val name = device.name ?: record.deviceName
        val hasQcyService = record.serviceUuids?.any {
            it.uuid.toString().lowercase().contains("a001")
        } == true

        if ((name != null && isQcyDeviceName(name)) || hasQcyService) {
            val safeName = name ?: "QCY MeloBuds Pro"
            val vendorId = guessVendorIdFromName(safeName)
            val info = ProductDatabase.getProduct(vendorId)
            return DiscoveredQcyDevice(
                deviceName = if (info.title.isNotEmpty() && !info.title.startsWith("QCY Aygıtı")) info.title else safeName,
                bleAddress = device.address,
                classicMac = null,
                vendorId = vendorId,
                productInfo = info,
                battery = QcyBatteryInfo(),
                rssi = rssi,
                isReachable = true
            )
        }

        return null
    }

    private fun parseManufacturerPayload(
        data: ByteArray,
        bleAddress: String,
        rawDeviceName: String,
        rssi: Int,
        requireKnownVendorId: Boolean = false
    ): DiscoveredQcyDevice? {
        if (data.size < 8) return null

        // Vendor ID in first two bytes: Big-Endian as decoded by official QCY Devicebind.java
        // (data[0] is High byte, data[1] is Low byte, e.g. 19786 for QCY MeloBuds Pro)
        val vidHigh = data[0].toInt() and 0xFF
        val vidLow = data[1].toInt() and 0xFF
        val vendorId = (vidHigh shl 8) or vidLow

        var productInfo = ProductDatabase.getProduct(vendorId)
        var vendorKnown = productInfo.title.isNotEmpty() && !productInfo.title.startsWith("QCY Aygıtı")
        val finalVendorId = if (vendorKnown) {
            vendorId
        } else {
            val altVendorId = (vidLow shl 8) or vidHigh
            val altProduct = ProductDatabase.getProduct(altVendorId)
            if (altProduct.title.isNotEmpty() && !altProduct.title.startsWith("QCY Aygıtı")) {
                productInfo = altProduct
                vendorKnown = true
                altVendorId
            } else {
                if (requireKnownVendorId) return null
                vendorId
            }
        }

        // Battery extraction (Index 5=L, 6=R, 7=Case) per official BleScanManager.java.
        // Unknown-vendor payloads are sparse random bytes — indices 5-7 are garbage
        // (saw X-403BT vid0 report L63/R80/Case6). Only trust battery for known products.
        val battery = if (!vendorKnown) {
            QcyBatteryInfo()
        } else {
            val leftRaw = data.getOrNull(5)?.toInt()?.and(0xFF) ?: -1
            val rightRaw = data.getOrNull(6)?.toInt()?.and(0xFF) ?: -1
            val caseRaw = data.getOrNull(7)?.toInt()?.and(0xFF) ?: -1

            val leftCharging = leftRaw >= 0 && (leftRaw and 0x80) != 0
            val leftVal = if (leftRaw >= 0) (leftRaw and 0x7F) else -1

            val rightCharging = rightRaw >= 0 && (rightRaw and 0x80) != 0
            val rightVal = if (rightRaw >= 0) (rightRaw and 0x7F) else -1

            val caseCharging = caseRaw >= 0 && (caseRaw and 0x80) != 0
            val caseVal = if (caseRaw >= 0) (caseRaw and 0x7F) else -1

            QcyBatteryInfo(
                leftLevel = if (leftVal in 1..100) leftVal else -1,
                isLeftCharging = leftCharging && (leftVal in 1..100),
                rightLevel = if (rightVal in 1..100) rightVal else -1,
                isRightCharging = rightCharging && (rightVal in 1..100),
                caseLevel = if (caseVal in 1..100) caseVal else -1,
                isCaseCharging = caseCharging && (caseVal in 1..100)
            )
        }

        // De-scramble Classic Bluetooth MAC if present (bytes 11..16)
        // Official BleScanManager.java formula: [12]:[11]:[13]:[16]:[15]:[14]
        val classicMac = if (data.size >= 17) {
            try {
                String.format(
                    "%02X:%02X:%02X:%02X:%02X:%02X",
                    data[12], data[11], data[13], data[16], data[15], data[14]
                )
            } catch (e: Exception) {
                null
            }
        } else null

        // Secondary MAC if present (bytes 18..23)
        // Official BleScanManager.java formula: [19]:[18]:[20]:[23]:[22]:[21]
        val otherMac = if (data.size >= 24) {
            try {
                String.format(
                    "%02X:%02X:%02X:%02X:%02X:%02X",
                    data[19], data[18], data[20], data[23], data[22], data[21]
                )
            } catch (e: Exception) {
                null
            }
        } else null

        val displayName = if (productInfo.title.isNotEmpty() && !productInfo.title.startsWith("QCY Aygıtı")) {
            productInfo.title
        } else {
            rawDeviceName
        }

        return DiscoveredQcyDevice(
            deviceName = displayName,
            bleAddress = bleAddress,
            classicMac = classicMac,
            otherMac = otherMac,
            vendorId = finalVendorId,
            productInfo = productInfo,
            battery = battery,
            rssi = rssi,
            isReachable = true
        )
    }

    private fun findQcyBeaconHeader(bytes: ByteArray): Int {
        for (i in 0 until bytes.size - 3) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = bytes[i + 1].toInt() and 0xFF
            val b2 = bytes[i + 2].toInt() and 0xFF

            if (b0 == 0xFF && (
                (b1 == 0x1C && b2 == 0x52) ||
                (b1 == 0x52 && b2 == 0x1C) ||
                (b1 == 0x2C && b2 == 0x52) ||
                (b1 == 0x52 && b2 == 0x2C)
            )) {
                return i + 3
            }
        }
        return -1
    }

    fun isQcyDeviceName(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val lower = name.lowercase().trim()

        val blacklist = listOf(
            "watch", "fit", "band", "huawei", "audi", "mmi", "throne", "m19", "ultra8",
            "mrspy", "car", "tv", "desktop", "laptop", "pc", "dsp", "speaker", "soundbar",
            "keyboard", "mouse", "controller", "gamepad", "printer", "peugeot", "redmi",
            "huaxu", "link-q", "cy03", "ws-", "e46"
        )
        if (blacklist.any { lower.contains(it) }) {
            return false
        }

        if (lower.contains("qcy") ||
            lower.contains("melobuds") ||
            lower.contains("crossky") ||
            lower.contains("ailybuds") ||
            lower.contains("arcbuds")) {
            return true
        }

        val qcyModelRegex = Regex("^qcy[-_\\s]?(ht|t|h|bh|in|mg)\\d+.*", RegexOption.IGNORE_CASE)
        val cleanName = lower.replace(" ", "").replace("-", "").replace("_", "")
        if (qcyModelRegex.matches(lower) || cleanName.startsWith("qcy")) {
            return true
        }

        return false
    }

    fun guessVendorIdFromName(name: String): Int {
        val found = ProductDatabase.findProductByName(name)
        if (found != null) return found.vendorId

        val lower = name.lowercase()
        return when {
            lower.contains("melobuds pro") || lower.contains("ht08") -> 19786
            lower.contains("melobuds anc") || lower.contains("ht05") -> 19777
            lower.contains("h4") -> 16849
            lower.contains("h3") -> 16848
            lower.contains("t13 anc") -> 19825
            lower.contains("t13") -> 19059
            lower.contains("t16") -> 19218
            lower.contains("ht03") -> 19393
            else -> 0
        }
    }
}
