package com.aegissoft.qcybutmod.core.bluetooth

/**
 * 0x17 ANC yankı (echo) koruması: gönderilen son komutun ham paramlarını tutar,
 * pencere içinde gelen ve farklı değer taşıyan geç/geçersiz yankıları reddeder.
 */
class AncEchoGuard(private val windowMs: Long = 2500) {

    private var lastSentParams: IntArray? = null
    private var lastSentAt: Long = 0L

    /** Gönderilen frame'in ham 3 paramını (mode, subScene, byte2) kaydeder. */
    fun onSent(params: IntArray, at: Long) {
        lastSentParams = params
        lastSentAt = at
    }

    /**
     * Yankı uygulanmalı mı? Pencere içindeki ve gönderilenle eşleşmeyen yankı
     * eski/ geçersizdir → false (bekleyen kayıt korunur). Eşleşen → true + kayıt temizlenir.
     * Bekleyen kayıt yoksa veya pencere dışındaysa → true.
     */
    fun shouldApply(params: IntArray, at: Long): Boolean {
        val pending = lastSentParams ?: return true
        if (at - lastSentAt > windowMs) {
            lastSentParams = null // pencere doldu, harici değişiklik serbest
            return true
        }
        if (!params.contentEquals(pending)) return false // stale yankı yok sayılır
        lastSentParams = null // eşleşen yankı kabul edildi, bekleyen kayıt temizlendi
        return true
    }
}
