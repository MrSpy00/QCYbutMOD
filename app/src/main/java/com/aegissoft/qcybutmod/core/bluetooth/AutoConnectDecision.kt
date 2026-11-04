package com.aegissoft.qcybutmod.core.bluetooth

/**
 * Saf karar fonksiyonu: bonded QCY cihazına otomatik bağlanılmalı mı?
 * Cihaza özgü 10 s rate-limit uygular; UI/BLE katmanından bağımsızdır (JVM test edilebilir).
 */
object AutoConnectDecision {
    const val RATE_LIMIT_MS = 10_000L

    fun shouldConnect(
        isQcy: Boolean,
        isAclConnected: Boolean,
        lastAttemptMs: Long?,
        nowMs: Long,
        userStopped: Boolean = false
    ): Boolean {
        if (!isQcy) return false
        if (userStopped) return false
        if (!isAclConnected) return false
        val last = lastAttemptMs ?: return true
        return nowMs - last >= RATE_LIMIT_MS
    }

    /**
     * Gözlemlenen çift: 84:AC:60:E2:6B:32 (BR/EDR) ↔ C4:AC:60:E2:6B:33 (LE).
     * Wuqi TWS çift adres kullanır: ilk bayt XOR 0x40, son bayt XOR 0x01.
     * shortcut: tek örnekle türetilen desen — yanlış adresten GATT bağlanamaz,
     * mevcut klasik-MAC/scan yedeklerine güvenli şekilde düşer.
     */
    fun deriveLeAddress(classicMac: String): String? {
        val parts = classicMac.split(":")
        if (parts.size != 6) return null
        val bytes = IntArray(6) { i ->
            val p = parts[i]
            if (p.isEmpty() || p.length > 2) return null
            p.toIntOrNull(16)?.takeIf { it in 0..255 } ?: return null
        }
        bytes[0] = bytes[0] xor 0x40
        bytes[5] = bytes[5] xor 0x01
        val result = bytes.joinToString(":") { "%02X".format(it) }
        return if (result.equals(classicMac, ignoreCase = true)) null else result
    }
}
