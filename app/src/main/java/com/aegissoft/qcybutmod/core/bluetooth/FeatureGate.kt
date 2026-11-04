package com.aegissoft.qcybutmod.core.bluetooth

import com.aegissoft.qcybutmod.core.models.QcyDeviceState

/** Kulaklık bağlı değilken özellik çağrılarını merkezi olarak engeller. */
object FeatureGate {

    sealed interface GateResult {
        data object Allowed : GateResult
        data class Blocked(val reason: String) : GateResult
    }

    fun requireConnected(state: QcyDeviceState, feature: String): GateResult =
        if (state.isConnected) {
            GateResult.Allowed
        } else {
            GateResult.Blocked(
                "Kulaklık bağlı değil — $feature yapılamaz. Kulaklığı bağlayıp tekrar deneyin."
            )
        }
}
