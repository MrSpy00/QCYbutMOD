package com.aegissoft.qcybutmod.core.models

data class QcyBatteryInfo(
    val leftLevel: Int = -1,
    val isLeftCharging: Boolean = false,
    val rightLevel: Int = -1,
    val isRightCharging: Boolean = false,
    val caseLevel: Int = -1,
    val isCaseCharging: Boolean = false,
    val systemLevel: Int = -1
) {
    val hasValidBattery: Boolean
        get() = leftLevel in 0..100 || rightLevel in 0..100 || caseLevel in 0..100 || systemLevel in 0..100
}

enum class AncMode(val id: Int, val title: String) {
    OFF(0, "Normal (Kapalı)"),
    ANC(1, "Gürültü Engelleme"),
    TRANSPARENCY(2, "Şeffaf Mod")
}

enum class AncSubScene(val id: Int, val title: String) {
    ADAPTIVE(5, "Adaptif"),
    INDOOR(1, "İç Mekan"),
    COMMUTE(2, "Yolculuk"),
    NOISY(3, "Kalabalık"),
    ANTI_WIND(4, "Rüzgar Önleme")
}

data class QcyDeviceState(
    val vendorId: Int = 19786, // Default QCY MeloBuds Pro
    val modelName: String = "QCY MeloBuds Pro",
    val bleAddress: String = "",
    val classicMac: String = "",
    val isConnected: Boolean = false,
    val battery: QcyBatteryInfo = QcyBatteryInfo(),
    val ancMode: AncMode = AncMode.ANC,
    val ancSubScene: AncSubScene = AncSubScene.ADAPTIVE,
    val ancLevel: Int = 3, // 1 to 5
    val transparencyLevel: Int = 3, // 1 to 6
    val isAiAdaptiveAncOn: Boolean = false,
    val isGameModeOn: Boolean = false,
    val isLdacOn: Boolean = true,
    val isInEarDetectionOn: Boolean = true,
    val isSleepModeOn: Boolean = false,
    val isMultipointOn: Boolean = true,
    val channelBalance: Int = 10, // 0 (Left) to 20 (Right), 10 = Centered
    val promptVolume: Int = 8, // 0 to 15
    val firmwareVersion: String = "",
    val lastAutoConnectAttempt: Long? = null,
    val lastAutoConnectResult: String? = null,
    val lastKnownLat: Double? = null,
    val lastKnownLon: Double? = null,
    val rssi: Int = -100,
    val isConnecting: Boolean = false,
    val connectingAddress: String = "",
    val pairedPhones: List<com.aegissoft.qcybutmod.core.bluetooth.QcyProtocol.ConnectedPhone> = emptyList(),
    val keyMappings: Map<Int, Int> = emptyMap(),
    val eqPresetId: String = "flat",
    val eqGains: List<Float> = List(10) { 0f }
)

data class TouchGestureAction(
    val keyId: Int,
    val functionId: Int,
    val name: String
)

object GestureFunctions {
    const val NONE = 0
    const val PLAY_PAUSE = 1
    const val PREV_TRACK = 2
    const val NEXT_TRACK = 3
    const val VOICE_ASSISTANT = 4
    const val VOLUME_UP = 5
    const val VOLUME_DOWN = 6
    const val GAME_MODE = 7
    const val ANSWER_CALL = 8
    const val REJECT_CALL = 9
    const val ANC_TOGGLE = 11 // Official melobuds_pro_19786_panel.json cmd="11" (0x0B) for ANC mode

    val functionList = listOf(
        NONE to "İşlevsiz",
        PLAY_PAUSE to "Oynat / Duraklat",
        NEXT_TRACK to "Sonraki Parça",
        PREV_TRACK to "Önceki Parça",
        VOLUME_UP to "Ses Aç (+)",
        VOLUME_DOWN to "Ses Kıs (-)",
        ANC_TOGGLE to "ANC / Şeffaf Geçişi",
        GAME_MODE to "Oyun Modu (Düşük Gecikme)",
        VOICE_ASSISTANT to "Sesli Asistan"
    )
}
