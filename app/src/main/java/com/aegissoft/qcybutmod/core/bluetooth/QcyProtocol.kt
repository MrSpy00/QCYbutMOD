package com.aegissoft.qcybutmod.core.bluetooth

import com.aegissoft.qcybutmod.core.audio.EqBand
import com.aegissoft.qcybutmod.core.models.AncMode
import com.aegissoft.qcybutmod.core.models.AncSubScene
import com.aegissoft.qcybutmod.core.models.QcyBatteryInfo
import java.util.UUID
import kotlin.math.roundToInt

object QcyProtocol {
    // Standard QCY BLE GATT UUIDs (from official com.qcymall.qcylibrary.bluetooth.QCYConnectManager)
    val SERVICE_UUID: UUID = UUID.fromString("0000a001-0000-1000-8000-00805f9b34fb")
    val SERVICE_WUQI: UUID = UUID.fromString("00007033-0000-1000-8000-00805f9b34fb")
    val SERVICE_BES: UUID = UUID.fromString("66666666-6666-6666-6666-666666666666")

    val SPP_UUID_WUQI: UUID = UUID.fromString("00007033-0000-1000-8000-00805f9b34fb")
    val SPP_UUID_STANDARD: UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")

    val CHAR_COMMAND_TX: UUID = UUID.fromString("00001001-0000-1000-8000-00805f9b34fb")
    val CHAR_NOTIFY_RX: UUID = UUID.fromString("00001002-0000-1000-8000-00805f9b34fb")
    val CHAR_WUQI_TX_2001: UUID = UUID.fromString("00002001-0000-1000-8000-00805f9b34fb")
    val CHAR_WUQI_RX_2002: UUID = UUID.fromString("00002002-0000-1000-8000-00805f9b34fb")
    val CHAR_BES_RW_7777: UUID = UUID.fromString("77777777-7777-7777-7777-777777777777")

    val CCCD_DESCRIPTOR: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // Direct Read/Write Characteristics on Service 0000A001
    val CHAR_EQ_CONFIG: UUID = UUID.fromString("0000000b-0000-1000-8000-00805f9b34fb")
    val CHAR_KEY_MAPPING: UUID = UUID.fromString("0000000d-0000-1000-8000-00805f9b34fb")
    val CHAR_BATTERY_READ: UUID = UUID.fromString("00000008-0000-1000-8000-00805f9b34fb")
    val CHAR_FIRMWARE_READ: UUID = UUID.fromString("00000007-0000-1000-8000-00805f9b34fb")
    val CHAR_V1_SETTING: UUID = UUID.fromString("0000000f-0000-1000-8000-00805f9b34fb")

    // Official QCY Manufacturer Beacon IDs (from BleScanManager.java)
    const val QCY_COMPANY_ID = 0x521C // 21020
    const val QCY_COMPANY_ID_21036 = 0x522C // 21036
    const val QCY_COMPANY_ID_1494 = 0x05D6 // 1494

    // Command Codes (from official QCY com.qcymall.qcylibrary.dataBean.DataBean)
    const val CMD_RESET_DEFAULT: Byte = 0x01
    const val CMD_CLEAR_PAIR: Byte = 0x02
    const val CMD_FACTORY_RESET: Byte = 0x03
    const val CMD_FIND_DEVICE: Byte = 0x05 // CMDID_LIGHT: 1=start, 0=stop
    const val CMD_IN_EAR_DETECTION: Byte = 0x06 // CMDID_RUER: 1=on, 2=off
    const val CMD_FIRMWARE_VERSION: Byte = 0x07
    const val CMD_GAME_MODE: Byte = 0x09 // CMDID_DIYANSHI: 1=on, 2=off
    const val CMD_ANC_LEGACY: Byte = 0x0C // CMDID_ANC (12)
    const val CMD_SLEEP_MODE: Byte = 0x10 // CMDID_SLEEPMODE: 1=on, 2=off
    const val CMD_FIT_TEST: Byte = 0x11 // CMDID_COMPACTNESS (17): [1, 1, 1] or [0, 0, 0]
    const val CMD_CHANNEL_BALANCE: Byte = 0x16 // CMDID_BALANCE (22)
    const val CMD_ANC_SETTING: Byte = 0x17 // CMDID_ANCSETTING (23): [mode, subScene, noiseValue]
    const val CMD_DEVICE_RENAME: Byte = 0x18 // CMDID_PAIRNAME (24)
    const val CMD_TONE_VOLUME: Byte = 0x1D // CMDID_TONEVOLUME (29)
    const val CMD_EQ_SELECT: Byte = 0x1E // CMDID_EQSELECT (30)
    const val CMD_MULTI_EQ: Byte = 0x20 // CMDID_MULTIEQ (32)
    const val CMD_MULTI_EQ_2: Byte = 0x22 // CMDID_MULTIEQ2 (34)
    const val CMD_LDAC_SWITCH: Byte = 0x23 // CMDID_LDAC (35): 1=on, 2=off
    const val CMD_MULTIPOINT_CONTROL: Byte = 0x24 // CMDID_MOREDEVICE (36): 1=on, 2=off
    const val CMD_ANC_RESULT: Byte = 0x28 // CMDID_ANC_RESULT (40)
    const val CMD_WEAR_RESULT: Byte = 0x29 // CMDID_WEAR_RESULT (41)
    const val CMD_KEY_FUNCTIONS: Byte = 0x2B // CMDID_KEYFUN (43)
    const val CMD_PEIDAI: Byte = 0x2C // CMDID_PEIDAI (44): [enable(1/0), musicIdx, ancIdx]
    const val CMD_BATTERY_QUERY: Byte = 0x2F // CMDID_BATTERY (47)
    const val CMD_FIND_DEVICE_ALT: Byte = 0x3F
    const val CMD_LEFT_CHANNEL_EQ: Byte = 0x46 // CMDID_MULTIEQ_LEFT (70)
    const val CMD_RIGHT_CHANNEL_EQ: Byte = 0x47 // CMDID_MULTIEQ_RIGHT (71)
    const val CMD_MULTIPOINT_QUERY: Byte = 0x4D // CMDID_PHONE_INFO (77)
    const val CMD_PHONE_STATUS: Byte = 0x4F // CMDID_PHONE_STATUS (79)
    const val CMD_ADAPTIVE_AI_ANC: Byte = 0x54 // CMDID_AI_ANC (84)
    const val CMD_REQUEST_ALL_DATA: Byte = 0xFE.toByte()

    /**
     * Packs standard QCY framed packet:
     * [0xFF, bodyLength, cmdId, paramLength, ...params]
     */
    fun packFrame(cmdId: Byte, vararg params: Byte): ByteArray {
        val paramLen = params.size
        val bodyLen = 2 + paramLen // cmdId (1) + paramLen (1) + params
        val packet = ByteArray(2 + bodyLen)
        packet[0] = 0xFF.toByte()
        packet[1] = bodyLen.toByte()
        packet[2] = cmdId
        packet[3] = paramLen.toByte()
        System.arraycopy(params, 0, packet, 4, paramLen)
        return packet
    }

    // --- Command Encoders ---

    fun createRequestAllDataCommand(): ByteArray {
        return packFrame(CMD_REQUEST_ALL_DATA, 0x00)
    }

    fun createBatteryQueryCommand(): ByteArray {
        return packFrame(CMD_BATTERY_QUERY, 0x00)
    }

    fun createFirmwareQueryCommand(): ByteArray {
        return packFrame(CMD_FIRMWARE_VERSION, 0x00)
    }

    fun createAncCommand(mode: AncMode, subScene: AncSubScene, level: Int, transparencyLevel: Int): ByteArray {
        return when (mode) {
            AncMode.OFF -> {
                // Official ANCSettingDataBean.ANC_MODE_CLOSE = 2 -> 131072 = 0x020000
                packFrame(CMD_ANC_SETTING, 0x02, 0x00, 0x00)
            }
            AncMode.ANC -> {
                // Official products.json (19786 QCY MeloBuds Pro):
                // Adaptive: 66816 = 0x010500 -> [0x01, 0x05, 0x00]
                // Anti-Wind: 66560 = 0x010400 -> [0x01, 0x04, 0x00]
                // Indoor (1): 65792..65794 = 0x010100..0x010102 -> [0x01, 0x01, 0..2]
                // Commuting (2): 66048..66050 = 0x010200..0x010202 -> [0x01, 0x02, 0..2]
                // Noisy/Crowd (3): 66304..66306 = 0x010300..0x010302 -> [0x01, 0x03, 0..2]
                when (subScene) {
                    AncSubScene.ADAPTIVE -> packFrame(CMD_ANC_SETTING, 0x01, 0x05, 0x00)
                    AncSubScene.ANTI_WIND -> packFrame(CMD_ANC_SETTING, 0x01, 0x04, 0x00)
                    else -> {
                        val sceneByte = subScene.id.toByte()
                        val hwLevel = (level - 1).coerceIn(0, 2).toByte()
                        packFrame(CMD_ANC_SETTING, 0x01, sceneByte, hwLevel)
                    }
                }
            }
            AncMode.TRANSPARENCY -> {
                // Official ANCSettingDataBean.ANC_MODE_TRANSPARENCY = 3
                // Standard Transparency: 196865..196870 = 0x030101..0x030106
                // Vocal Enhancement: 196864 = 0x030100 -> converted to [0x03, 0x02, 0x00]
                val sub = if (subScene.id == 2) 0x02.toByte() else 0x01.toByte()
                val lvlByte = if (sub == 0x02.toByte()) 0x00.toByte() else transparencyLevel.coerceIn(1, 6).toByte()
                packFrame(CMD_ANC_SETTING, 0x03, sub, lvlByte)
            }
        }
    }

    fun createLegacyAncCommand(mode: AncMode): ByteArray {
        // Official AncDataBean: 1 = ANC, 0 = OFF, 2 = Transparency
        val code: Byte = when (mode) {
            AncMode.ANC -> 0x01
            AncMode.OFF -> 0x00
            AncMode.TRANSPARENCY -> 0x02
        }
        return packFrame(CMD_ANC_LEGACY, code)
    }

    fun createAdaptiveAiAncCommand(enabled: Boolean): ByteArray {
        // Official QCYHeadsetClient.setAIANCEnable: [0x54, 0x06, z ? 0 : 4, 0, 0, 0, 0, 1]
        return packFrame(
            CMD_ADAPTIVE_AI_ANC,
            if (enabled) 0x00 else 0x04,
            0x00,
            0x00,
            0x00,
            0x00,
            0x01
        )
    }

    fun createGameModeCommand(enabled: Boolean): ByteArray {
        // Official melobuds_pro_19786_panel.json (cmdid=9): 1 = ON (FF03090101), 2 = OFF (FF03090102)
        return packFrame(CMD_GAME_MODE, if (enabled) 0x01 else 0x02)
    }

    fun createLdacCommand(enabled: Boolean): ByteArray {
        // Official melobuds_pro_19786_panel.json (cmdid=35): 1 = ON (FF03230101), 2 = OFF (FF03230102)
        return packFrame(CMD_LDAC_SWITCH, if (enabled) 0x01 else 0x02)
    }

    fun createInEarDetectionCommand(enabled: Boolean): ByteArray {
        // Official RuerDataBean: 1 = ON, 2 = OFF
        return packFrame(CMD_IN_EAR_DETECTION, if (enabled) 0x01 else 0x02)
    }

    fun createPeidaiCommand(enabled: Boolean, musicIndex: Int = 1, ancIndex: Int = 1): ByteArray {
        // Official PeidaiDataBean (cmd 44 / 0x2C): [enable(1/0), musicAction(1), ancAction(1)]
        return packFrame(CMD_PEIDAI, if (enabled) 0x01 else 0x00, musicIndex.toByte(), ancIndex.toByte())
    }

    fun createSleepModeCommand(enabled: Boolean): ByteArray {
        // Official melobuds_pro_19786_panel.json (cmdid=16): 1 = ON (FF03100101), 2 = OFF (FF03100102)
        return packFrame(CMD_SLEEP_MODE, if (enabled) 0x01 else 0x02)
    }

    fun createChannelBalanceCommand(balance: Int): ByteArray {
        // UI balance ranges from 0 (Left 100%) to 10 (Center) to 20 (Right 100%);
        // Hardware CMD 0x16 uses 0..100 scale where 50 (0x32) = Center
        val hwBalance = (balance.coerceIn(0, 20) * 5).coerceIn(0, 100).toByte()
        return packFrame(CMD_CHANNEL_BALANCE, hwBalance)
    }

    fun createToneVolumeCommand(volume: Int): ByteArray {
        // volume ranges from 0 to 15
        // Official ToneVolumeManagerActivity: vendorId==16737 -> {29,1,v}, everyone else
        // (incl. MeloBuds Pro 19786) -> {29,2,v,0}. Firmware ignores the single-byte form.
        val safe = volume.coerceIn(0, 15).toByte()
        return packFrame(CMD_TONE_VOLUME, safe, 0x00)
    }

    fun createFindDeviceCommand(start: Boolean): ByteArray {
        // Official DataBean.getFlashLightCMD: 1 = Start, 0 = Stop
        return packFrame(CMD_FIND_DEVICE, if (start) 0x01 else 0x00)
    }

    fun createFitTestCommand(start: Boolean): ByteArray {
        // Official QCYConnectManager.setCompactnessEnable(boolean z):
        // [-1, 5, 17, 3, z ? 1 : 0, z ? 1 : 0, z ? 1 : 0]
        val flag: Byte = if (start) 0x01 else 0x00
        return packFrame(CMD_FIT_TEST, flag, flag, flag)
    }

    fun createFactoryResetCommand(): ByteArray {
        // Official DataBean.getFactoryResetCMD: [3, 0]
        return packFrame(CMD_FACTORY_RESET, 0x00)
    }

    fun createResetDefaultCommand(): ByteArray {
        // Official DataBean.getResetDefaultCMD: [1, 0]
        return packFrame(CMD_RESET_DEFAULT, 0x00)
    }

    fun createClearPairCommand(): ByteArray {
        // Official DataBean.getClearPairCMD: [2, 0]
        return packFrame(CMD_CLEAR_PAIR, 0x00)
    }

    fun createDeviceRenameCommand(name: String): ByteArray {
        val bytes = name.toByteArray(Charsets.UTF_8).take(20).toByteArray()
        return packFrame(CMD_DEVICE_RENAME, *bytes)
    }

    fun createMultipointCommand(enable: Boolean): ByteArray {
        // Official PairPhoneConfigActivity:487 setSingleValue(cmdID, z ? 1 : 0) → 1 = ON (FF03240101), 0 = OFF (FF03240100)
        return packFrame(CMD_MULTIPOINT_CONTROL, if (enable) 0x01 else 0x00)
    }

    /**
     * Official QCYConnectManager.setPhoneBTAction(int action, byte[] macBytes):
     * action = 0 (disconnect) or 1 (connect)
     */
    fun createPhoneBtActionCommand(action: Int, phoneMac: String): ByteArray? {
        val parts = phoneMac.split(":")
        if (parts.size != 6) return null
        return try {
            val macBytes = parts.map { it.toInt(16).toByte() }.toByteArray()
            packFrame(
                CMD_MULTIPOINT_QUERY,
                action.toByte(),
                macBytes[0],
                macBytes[1],
                macBytes[2],
                macBytes[3],
                macBytes[4],
                macBytes[5]
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Touch Gesture Mapping encoder (Direct write to 0000000d)
     * Official QCYConnectManager.setKeyFunction(int keyId, int funcId) writes 2 bytes: [keyId, funcId]
     */
    fun createKeyMappingPayload(keyId: Int, functionId: Int): ByteArray {
        return byteArrayOf(keyId.toByte(), functionId.toByte())
    }

    /**
     * Serializes the full key mapping table into a byte array [k1, f1, k2, f2, ...].
     * Written to characteristic 0000000D with WRITE_TYPE_DEFAULT.
     */
    fun createAllKeyMappingPayload(keyMap: Map<Int, Int>): ByteArray {
        val sorted = keyMap.entries.sortedBy { it.key }
        val bytes = ByteArray(sorted.size * 2)
        var i = 0
        for ((keyId, funcId) in sorted) {
            bytes[i++] = keyId.toByte()
            bytes[i++] = funcId.toByte()
        }
        return bytes
    }

    fun createKeyMappingCommand(keyId: Int, functionId: Int): ByteArray {
        return packFrame(CMD_KEY_FUNCTIONS, keyId.toByte(), functionId.toByte())
    }

    /**
     * Official EQDataBean.getSettingEqCMD:
     * 11-byte payload written to characteristic 0000000B: [eqType, g0, g1, g2, g3, g4, g5, g6, g7, g8, g9]
     * eqType: 1=Default, 2=Pop, 3=Bass, 4=Rock, 5=Soft, 6=Classic, 10=Custom
     */
    fun createEqCharacteristicPayload(eqType: Int, gains: ByteArray): ByteArray {
        val out = ByteArray(1 + gains.size)
        out[0] = eqType.toByte()
        System.arraycopy(gains, 0, out, 1, gains.size)
        return out
    }

    /**
     * Official melobuds_pro_19786_panel.json sys_eq 6-slot acoustic baseline filters for QCY MeloBuds Pro (19786).
     * Slots 0..5 are populated with these 6 baseline filters, and Slots 6..15 hold the 10 custom bands (31..16000Hz).
     */
    fun getMeloBudsProSysEqBands(eqType: Int): List<EqBand> {
        return when (eqType) {
            2 -> listOf( // Pop
                EqBand(30, 0f, 0.4f, 4),
                EqBand(220, -8f, 1.0f, 0),
                EqBand(2800, 3.5f, 1.0f, 0),
                EqBand(12000, 3f, 0.8f, 0),
                EqBand(5300, -3f, 2.0f, 0),
                EqBand(60, -3f, 0.5f, 0)
            )
            3 -> listOf( // Heavy Bass
                EqBand(30, 0f, 0.7f, 4),
                EqBand(220, -8f, 1.0f, 0),
                EqBand(2800, 1f, 1.0f, 0),
                EqBand(12000, 8f, 0.8f, 0),
                EqBand(5300, -5f, 2.0f, 0),
                EqBand(60, 5f, 0.6f, 0)
            )
            4 -> listOf( // Rock
                EqBand(30, 0f, 0.7f, 4),
                EqBand(220, -10f, 1.0f, 0),
                EqBand(2800, 2f, 1.0f, 0),
                EqBand(12000, 3f, 0.8f, 0),
                EqBand(5300, -1f, 2.0f, 0),
                EqBand(60, 3f, 0.6f, 0)
            )
            5 -> listOf( // Soft / Vocal
                EqBand(30, 0f, 0.4f, 4),
                EqBand(220, -8f, 1.0f, 0),
                EqBand(2800, 1f, 1.0f, 0),
                EqBand(12000, 1f, 0.8f, 0),
                EqBand(5300, -3f, 2.0f, 0),
                EqBand(60, -2f, 0.5f, 0)
            )
            6 -> listOf( // Classic
                EqBand(30, 0f, 0.7f, 4),
                EqBand(220, -5f, 1.0f, 0),
                EqBand(2800, 1f, 1.0f, 0),
                EqBand(12000, 1f, 0.8f, 0),
                EqBand(5300, -5f, 2.0f, 0),
                EqBand(60, 2f, 0.6f, 0)
            )
            else -> listOf( // Default (1), Spatial (10), and Custom baseline
                EqBand(30, 0f, 0.7f, 4),
                EqBand(220, -8f, 1.0f, 0),
                EqBand(900, -2f, 1.5f, 0),
                EqBand(2800, 4f, 1.0f, 0),
                EqBand(12000, 3f, 0.8f, 0),
                EqBand(5300, -3f, 2.0f, 0)
            )
        }
    }

    /**
     * Official QCYConnectManager.setEQData & QCYHeadsetClient.setEQWtihQFG2Type (cmd 0x22 / 0x46 / 0x47):
     * Little-Endian header: [saveIndex, masterGainLo, masterGainHi]
     * Little-Endian 20 slots * 7 bytes:
     *   - Slots 0..5: 6 sys_eq acoustic baseline filters from melobuds_pro_19786_panel.json
     *   - Slots 6..15: 10 custom EQ bands (31..16000Hz, q=1.0, filterType=0)
     *   - Slots 16..19: Padded empty slots (0, 0f, 0f, 0xFF)
     */
    fun createMultiEqCommand(
        cmdId: Byte,
        saveIndex: Int,
        masterGain: Float,
        bands: List<EqBand>,
        includeEqType: Boolean = true,
        eqType: Int = 1
    ): ByteArray {
        val combinedBands = if (includeEqType) {
            val sysBands = getMeloBudsProSysEqBands(eqType)
            val userBands = if (eqType in 1..6) {
                // For pure system preset, user 10-band offsets are 0dB
                bands.map { EqBand(it.frequencyHz, 0f, 1.0f, 0) }
            } else {
                bands.map { EqBand(it.frequencyHz, it.gainDb, 1.0f, 0) }
            }
            sysBands + userBands
        } else {
            bands
        }

        val totalSlots = if (includeEqType) 20 else combinedBands.size
        val slotSize = if (includeEqType) 7 else 6
        val params = ByteArray(3 + totalSlots * slotSize)
        params[0] = saveIndex.toByte()

        val maxPositiveGain = if (eqType in 1..6) 0f else (bands.maxOfOrNull { it.gainDb } ?: 0f).coerceAtLeast(0f)
        val effectiveMasterGain = if (masterGain != 0f) masterGain else -maxPositiveGain
        val mg = (effectiveMasterGain * 100f).roundToInt()
        params[1] = (mg and 0xFF).toByte()
        params[2] = ((mg shr 8) and 0xFF).toByte()

        for (idx in 0 until totalSlots) {
            val base = 3 + idx * slotSize
            if (idx < combinedBands.size) {
                val band = combinedBands[idx]
                val fVal = band.frequencyHz
                val gVal = (band.gainDb * 100f).roundToInt().coerceIn(-1270, 1270)
                val qVal = (band.qFactor * 100f).roundToInt()
                params[base] = (fVal and 0xFF).toByte()
                params[base + 1] = ((fVal shr 8) and 0xFF).toByte()
                params[base + 2] = (gVal and 0xFF).toByte()
                params[base + 3] = ((gVal shr 8) and 0xFF).toByte()
                params[base + 4] = (qVal and 0xFF).toByte()
                params[base + 5] = ((qVal shr 8) and 0xFF).toByte()
                if (includeEqType) {
                    params[base + 6] = band.filterType.toByte()
                }
            } else {
                // Unused padded slot per QCYConnectManager (0, 0f, 0f, 255)
                params[base] = 0
                params[base + 1] = 0
                params[base + 2] = 0
                params[base + 3] = 0
                params[base + 4] = 0
                params[base + 5] = 0
                if (includeEqType) {
                    params[base + 6] = 0xFF.toByte()
                }
            }
        }
        return packFrame(cmdId, *params)
    }

    // --- Multipoint Dual Device Models ---

    data class ConnectedPhone(
        val mac: String,
        val name: String,
        val isConnected: Boolean,
        val isAudioActive: Boolean
    )

    fun parsePairPhoneList(payload: ByteArray): List<ConnectedPhone> {
        val list = mutableListOf<ConnectedPhone>()
        if (payload.isEmpty()) return list
        var offset = 1
        while (offset < payload.size) {
            val recordLen = payload[offset].toInt() and 0xFF
            val nextOffset = offset + 1 + recordLen
            if (nextOffset <= payload.size && recordLen >= 7) {
                val status = payload[offset + 1].toInt() and 0xFF
                val bMac = payload.copyOfRange(offset + 2, offset + 8)
                val macStr = String.format(
                    "%02X:%02X:%02X:%02X:%02X:%02X",
                    bMac[0], bMac[1], bMac[2], bMac[3], bMac[4], bMac[5]
                )
                val nameEnd = (offset + 8 until nextOffset).firstOrNull { payload[it] == 0.toByte() } ?: nextOffset
                val nameStr = try {
                    String(payload, offset + 8, nameEnd - (offset + 8), Charsets.UTF_8).trim()
                } catch (e: Exception) {
                    "Cihaz ($macStr)"
                }
                val isConn = (status == 2 || status == 3)
                list.add(
                    ConnectedPhone(
                        mac = macStr,
                        name = if (nameStr.isNotEmpty()) nameStr else "Cihaz ($macStr)",
                        isConnected = isConn,
                        isAudioActive = (status == 3)
                    )
                )
            }
            offset = nextOffset
        }
        return list
    }

    /**
     * 0x4D telefon listesi yanıtını state güncelleme kuralıyla ayrıştırır:
     * null = yanıt yok/geçersiz → state KORU; payload[0] (kayıt sayısı) == 0 →
     * açık boş yanıt → listeyi TEMİZLE; dolu kayıt → liste.
     */
    fun parsePhoneListResponse(payload: ByteArray): List<ConnectedPhone>? {
        if (payload.isEmpty()) return null
        val parsed = parsePairPhoneList(payload)
        if (parsed.isNotEmpty()) return parsed
        // Boş parselenen yanıtta sayaç başlığı 0 değilse bozuk/eksik yanıt sayılır (state koru)
        return if (payload[0] == 0.toByte()) emptyList() else null
    }

    fun parsePhoneStatus(payload: ByteArray): Pair<String, Boolean>? {
        if (payload.size < 7) return null
        val macStr = String.format(
            "%02X:%02X:%02X:%02X:%02X:%02X",
            payload[0], payload[1], payload[2], payload[3], payload[4], payload[5]
        )
        val status = payload[6].toInt() and 0xFF
        val isActive = (status == 2 || status == 3)
        return Pair(macStr, isActive)
    }

    // --- Packet Decoders ---

    data class DecodedFrame(
        val cmdId: Byte,
        val payload: ByteArray
    )

    fun unpackAllFrames(data: ByteArray): List<DecodedFrame> {
        val list = mutableListOf<DecodedFrame>()
        if (data.size < 4) return list

        // Case 1: Standard QCY BLE frame starting with 0xFF
        if (data[0] == 0xFF.toByte()) {
            val totalLen = data[1].toInt() and 0xFF
            var offset = 2
            val maxLen = minOf(data.size, 2 + totalLen)
            while (offset + 1 < maxLen) {
                val cmdId = data[offset]
                val paramLen = data[offset + 1].toInt() and 0xFF
                offset += 2
                val payload = if (paramLen > 0 && offset + paramLen <= maxLen) {
                    data.copyOfRange(offset, offset + paramLen)
                } else {
                    ByteArray(0)
                }
                offset += paramLen
                list.add(DecodedFrame(cmdId, payload))
            }
            return list
        }

        // Case 2: Unwrapped [cmdId, paramLen, ...payload] on some direct characteristics
        val firstCmd = data[0].toInt() and 0xFF
        val firstLen = data[1].toInt() and 0xFF
        if (firstCmd in 1..120 && 2 + firstLen == data.size) {
            list.add(DecodedFrame(data[0], data.copyOfRange(2, data.size)))
            return list
        }

        return list
    }

    fun unpackFrame(data: ByteArray): DecodedFrame? {
        return unpackAllFrames(data).firstOrNull()
    }

    fun parseBatteryPayload(payload: ByteArray): QcyBatteryInfo? {
        if (payload.isEmpty()) return null
        if (payload.size == 1) {
            val raw = payload[0].toInt() and 0xFF
            val charging = (raw and 0x80) != 0
            val lvl = raw and 0x7F
            if (lvl !in 1..100) return null
            return QcyBatteryInfo(
                leftLevel = lvl,
                isLeftCharging = charging,
                rightLevel = lvl,
                isRightCharging = charging,
                caseLevel = -1,
                isCaseCharging = false
            )
        }

        val leftRaw = payload[0].toInt() and 0xFF
        val rightRaw = if (payload.size > 1) payload[1].toInt() and 0xFF else -1
        val caseRaw = if (payload.size > 2) payload[2].toInt() and 0xFF else -1

        val leftCharging = (leftRaw and 0x80) != 0
        val leftVal = if (leftRaw >= 0) (leftRaw and 0x7F) else -1

        val rightCharging = (rightRaw and 0x80) != 0
        val rightVal = if (rightRaw >= 0) (rightRaw and 0x7F) else -1

        val caseCharging = (caseRaw and 0x80) != 0
        val caseVal = if (caseRaw >= 0) (caseRaw and 0x7F) else -1

        fun sanitize(v: Int): Int = when {
            v in 1..100 -> v
            v == 101 -> 100
            else -> -1
        }

        return QcyBatteryInfo(
            leftLevel = sanitize(leftVal),
            isLeftCharging = leftCharging && leftVal in 1..101,
            rightLevel = sanitize(rightVal),
            isRightCharging = rightCharging && rightVal in 1..101,
            caseLevel = sanitize(caseVal),
            isCaseCharging = caseCharging && caseVal in 1..101
        )
    }

    data class FitTestResult(
        val leftEarGoodSeal: Boolean,
        val rightEarGoodSeal: Boolean,
        val isCompleted: Boolean,
        val leftScore: Int = 2,
        val rightScore: Int = 2
    )

    fun parseFitTestResult(payload: ByteArray): FitTestResult {
        if (payload.isEmpty()) return FitTestResult(false, false, false)
        // Official CompactnessDataBean:
        // If 3 bytes: bArr[0]=status (0=testing, 2=result ready), bArr[1]=leftResult, bArr[2]=rightResult
        // If 2 bytes: bArr[0]=leftResult (1=good, 2=bad), bArr[1]=rightResult (1=good, 2=bad)
        if (payload.size == 2) {
            val l = payload[0].toInt() and 0xFF
            val r = payload[1].toInt() and 0xFF
            return FitTestResult(
                leftEarGoodSeal = (l == 1 || l >= 2),
                rightEarGoodSeal = (r == 1 || r >= 2),
                isCompleted = true,
                leftScore = l,
                rightScore = r
            )
        }
        val status = payload.getOrNull(0)?.toInt() ?: 0
        val left = payload.getOrNull(1)?.toInt() ?: 0
        val right = payload.getOrNull(2)?.toInt() ?: 0
        val isGoodLeft = left >= 1
        val isGoodRight = right >= 1
        return FitTestResult(
            leftEarGoodSeal = isGoodLeft,
            rightEarGoodSeal = isGoodRight,
            isCompleted = (status == 2 || (left > 0 || right > 0)),
            leftScore = left,
            rightScore = right
        )
    }
}
