package com.aegissoft.qcybutmod.core.bluetooth

/**
 * Firmware'in bildirim (prompt) ses seviyesini kendiliğinden yeniden açması durumunda
 * uygulamanın tekrar susturması gerekip gerekmediği kararı.
 *
 * Kural: kulaklık modu değilse ("firmware_en"), ses sıfırdan farklıysa ve son susturma
 * işleminden [debounceMs] geçtiyse (fark yankı fırtınasını engeller) → yeniden sustur.
 */
object PromptMutePolicy {
    fun shouldRemute(sourceId: String, vol: Int, now: Long, lastRemuteAt: Long, debounceMs: Long = 2000): Boolean {
        return sourceId != "firmware_en" && vol in 1..15 && now - lastRemuteAt > debounceMs
    }
}
