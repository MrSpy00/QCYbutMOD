package com.aegissoft.qcybutmod.core.audio

data class VoiceInfo(val name: String, val quality: Int)

object VoiceGenderResolver {
    private const val GENDER_FEMALE = "female"
    private const val GENDER_MALE = "male"

    // Android Turkish voices are named "tr-tr-x-trf-…" (female) / "tr-tr-x-trm-…" (male),
    // so the literal "female" substring never appears in those engine voice ids.
    private fun VoiceInfo.isFemaleCoded(): Boolean =
        name.contains("female", ignoreCase = true) || name.contains("trf", ignoreCase = true)

    fun hasFemaleCandidate(candidates: List<VoiceInfo>): Boolean =
        candidates.any { it.isFemaleCoded() }

    /** Highest-quality female-coded voice, else highest-quality overall; null only when empty. */
    fun pickVoiceName(candidates: List<VoiceInfo>, gender: String): String? {
        if (candidates.isEmpty()) return null
        val best = candidates.maxByOrNull { it.quality } ?: return null
        val picked = when (gender) {
            GENDER_FEMALE -> candidates.filter { it.isFemaleCoded() }.maxByOrNull { it.quality }
            GENDER_MALE -> candidates.filter { !it.isFemaleCoded() }.maxByOrNull { it.quality }
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
