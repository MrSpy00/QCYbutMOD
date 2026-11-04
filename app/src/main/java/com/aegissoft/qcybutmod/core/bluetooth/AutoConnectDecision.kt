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
}
