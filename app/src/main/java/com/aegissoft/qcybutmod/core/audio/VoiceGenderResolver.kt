package com.aegissoft.qcybutmod.core.audio

data class VoiceInfo(val name: String, val quality: Int)

object VoiceGenderResolver {
    private const val GENDER_FEMALE = "female"
    private const val GENDER_MALE = "male"

    // Eski Android TR kodları: "tr-tr-x-trf-…" (female) / "tr-tr-x-trm-…" (male).
    // Yeni Google TTS TR paketi farklı kimlikler kullanır — crosstales RT-Voice ses
    // tablosuyla doğrulandı: cfs/efu/mfm = FEMALE; tmc/ama = MALE; "tr-TR-language" = nötr.
    private fun VoiceInfo.isFemaleCoded(): Boolean =
        name.contains("female", ignoreCase = true) || name.contains("trf", ignoreCase = true) ||
            name.contains("-x-cfs-", ignoreCase = true) || name.contains("-x-efu-", ignoreCase = true) ||
            name.contains("-x-mfm-", ignoreCase = true)

    private fun VoiceInfo.isMaleCoded(): Boolean =
        (!name.contains("female", ignoreCase = true) && name.contains("male", ignoreCase = true)) ||
            name.contains("trm", ignoreCase = true) ||
            name.contains("-x-tmc-", ignoreCase = true) || name.contains("-x-ama-", ignoreCase = true)

    fun hasFemaleCandidate(candidates: List<VoiceInfo>): Boolean =
        candidates.any { it.isFemaleCoded() }

    /** Highest-quality gender-coded voice, else best fit; null only when empty. */
    fun pickVoiceName(candidates: List<VoiceInfo>, gender: String): String? {
        if (candidates.isEmpty()) return null
        val best = candidates.maxByOrNull { it.quality } ?: return null
        val picked = when (gender) {
            GENDER_FEMALE -> candidates.filter { it.isFemaleCoded() }.maxByOrNull { it.quality }
            GENDER_MALE -> candidates.filter { it.isMaleCoded() }.maxByOrNull { it.quality }
                ?: candidates.filter { !it.isFemaleCoded() }.maxByOrNull { it.quality }
            else -> null
        } ?: best
        return picked.name
    }

    /**
     * Kadın sesi cihazda yoksa tr-TR erkek sesi 1.35 perde ile "simülasyon" kadın sesi verir.
     * hasFemaleVoice varsayılanı true → mevcut çağrılar/testler eski davranışı korur.
     */
    fun pitchFor(gender: String, hasFemaleVoice: Boolean = true): Float = when {
        gender == GENDER_FEMALE && !hasFemaleVoice -> 1.35f
        gender == GENDER_FEMALE -> 1.15f
        gender == GENDER_MALE -> 0.88f
        else -> 1.0f
    }
}
