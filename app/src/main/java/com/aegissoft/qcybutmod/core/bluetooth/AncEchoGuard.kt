package com.aegissoft.qcybutmod.core.bluetooth

/**
 * 0x17 ANC yankı (echo) koruması: gönderilen son komutun ham paramlarını tutar,
 * pencere içinde gelen ve farklı değer taşıyan geç/geçersiz yankıları reddeder.
 */
class AncEchoGuard(private val windowMs: Long = 2500) {

    private var lastSentParams: IntArray? = null
    private var lastSentAt: Long = 0L

    /** Gönderilen frame'in ham 3 paramını (mode, subScene, byte2) kaydeder. */
    @Synchronized
    fun onSent(params: IntArray, at: Long) {
        lastSentParams = params
        lastSentAt = at
    }

    /**
     * Yankı uygulanmalı mı? Pencere içindeki ve gönderilenle eşleşmeyen yankı
     * eski/geçersizdir → false (bekleyen kayıt korunur). Eşleşen → true, bekleyen
     * kayıt pencere dolana dek korunur. Kayıt yoksa veya pencere dışındaysa → true.
     */
    @Synchronized
    fun shouldApply(params: IntArray, at: Long): Boolean {
        val pending = lastSentParams ?: return true
        if (at - lastSentAt > windowMs) {
            lastSentParams = null // pencere doldu, harici değişiklik serbest
            return true
        }
        if (!canonicalize(params).contentEquals(canonicalize(pending))) return false
        return true
    }

    private fun canonicalize(p: IntArray): IntArray =
        intArrayOf(p[0], if (p[0] == 1 && (p[1] == 0 || p[1] == 5)) 0 else p[1], p[2])
}
