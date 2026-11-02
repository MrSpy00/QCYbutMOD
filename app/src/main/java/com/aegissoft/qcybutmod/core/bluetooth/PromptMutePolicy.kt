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

    fun recheckDelayMs(now: Long, lastRemuteAt: Long, debounceMs: Long = 2000): Long {
        return (lastRemuteAt + debounceMs - now).coerceAtLeast(0) + 100
    }

    fun shouldAllowVoiceAssistantGesture(sourceId: String): Boolean {
        return sourceId == "firmware_en"
    }

    fun remapAssistantGestures(mappings: Map<Int, Int>, assistantFuncId: Int, noneFuncId: Int): Map<Int, Int> {
        return mappings.mapValues { (_, funcId) -> if (funcId == assistantFuncId) noneFuncId else funcId }
    }
}
