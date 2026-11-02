package com.aegissoft.qcybutmod.core.audio

object VoiceMessages {
    const val ANC_OFF = "Normal mod"
    const val ANC_ON = "Gürültü engelleme"
    const val TRANSPARENCY = "Şeffaf mod"

    const val GAME_MODE = "Oyun modu"
    const val IN_EAR = "Kulak algılama"
    const val SLEEP_MODE = "Uyku modu"
    const val LDAC = "LDAC yüksek çözünürlüklü ses"
    const val MULTIPOINT = "Çift cihaz bağlantısı"

    const val WARNING_BOTH_EARBUDS = "Gürültü engelleme için her iki kulaklığın da takılı olması gerekir"
    const val STATE_ON = "açıldı"
    const val STATE_OFF = "kapatıldı"

    fun featureChanged(featureName: String, enabled: Boolean): String {
        return "$featureName ${if (enabled) STATE_ON else STATE_OFF}"
    }

    fun connection(modelName: String, batteryPercent: Int): String {
        val battText = if (batteryPercent in 1..100) ", şarj yüzde $batteryPercent" else ""
        return "$modelName bağlandı$battText"
    }

    fun disconnection(modelName: String): String {
        return "$modelName bağlantısı kesildi"
    }

    fun battery(left: Int, right: Int, caseLevel: Int): String {
        val parts = mutableListOf<String>()
        if (left in 0..100) parts.add("sol kulaklık yüzde $left")
        if (right in 0..100) parts.add("sağ kulaklık yüzde $right")
        if (caseLevel in 0..100) parts.add("şarj kutusu yüzde $caseLevel")
        return if (parts.isNotEmpty()) {
            "Pil durumu: ${parts.joinToString(", ")}"
        } else {
            "Pil seviyesi okunamıyor"
        }
    }

    fun keyAction(earbud: String, gestureType: String, actionName: String): String {
        val cleanEarbud = earbud.trim()
        val cleanGesture = gestureType.trim()
        val cleanAction = actionName.trim()
        return buildString {
            if (cleanEarbud.isNotEmpty()) append("$cleanEarbud, ")
            if (cleanGesture.isNotEmpty()) append("$cleanGesture için ")
            append("$cleanAction olarak ayarlandı")
        }
    }
}
