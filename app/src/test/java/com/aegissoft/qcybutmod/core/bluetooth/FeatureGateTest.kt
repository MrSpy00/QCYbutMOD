package com.aegissoft.qcybutmod.core.bluetooth

import com.aegissoft.qcybutmod.core.models.QcyDeviceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureGateTest {

    private val sampleFeatures = listOf(
        "ANC modu", "Oyun modu", "LDAC codec", "Uyum testi", "Dokunma hareketi"
    )

    @Test
    fun disconnectedBlocksEveryFeature() {
        val state = QcyDeviceState(isConnected = false)
        sampleFeatures.forEach { feature ->
            val result = FeatureGate.requireConnected(state, feature)
            assertTrue(
                "Feature '$feature' must be blocked while disconnected",
                result is FeatureGate.GateResult.Blocked
            )
        }
    }

    @Test
    fun blockedReasonStartsWithConnectionMessage() {
        val state = QcyDeviceState(isConnected = false)
        sampleFeatures.forEach { feature ->
            val result = FeatureGate.requireConnected(state, feature) as FeatureGate.GateResult.Blocked
            assertTrue(
                "Reason must start with the connection warning",
                result.reason.startsWith("Kulaklık bağlı değil")
            )
            assertTrue(
                "Reason must include the blocked feature name",
                result.reason.contains(feature)
            )
        }
    }

    @Test
    fun connectedAllowsEveryFeature() {
        val state = QcyDeviceState(isConnected = true)
        sampleFeatures.forEach { feature ->
            assertEquals(
                FeatureGate.GateResult.Allowed,
                FeatureGate.requireConnected(state, feature)
            )
        }
    }
}
