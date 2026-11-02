package com.aegissoft.qcybutmod.core.bluetooth

import com.aegissoft.qcybutmod.core.bluetooth.QcyProtocol.ConnectedPhone

fun applyPhoneStatusPush(
    existing: List<ConnectedPhone>,
    mac: String,
    isAudioActive: Boolean
): List<ConnectedPhone> {
    val known = existing.any { it.mac.equals(mac, ignoreCase = true) }
    val merged = existing.map { p ->
        when {
            p.mac.equals(mac, ignoreCase = true) -> p.copy(isAudioActive = isAudioActive, isConnected = true)
            isAudioActive -> p.copy(isAudioActive = false)
            else -> p
        }
    }
    return if (known) merged
    else merged + ConnectedPhone(mac, "Bilinmeyen cihaz ($mac)", isConnected = true, isAudioActive = isAudioActive)
}
