package com.aegissoft.qcybutmod.core.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoConnectDecisionTest {
    @Test fun rejectsNonQcyDevice() {
        assertFalse(AutoConnectDecision.shouldConnect(isQcy = false, isAclConnected = true, lastAttemptMs = null, nowMs = 10_000))
    }

    @Test fun allowsAclConnectedQcyWithNoPriorAttempt() {
        assertTrue(AutoConnectDecision.shouldConnect(isQcy = true, isAclConnected = true, lastAttemptMs = null, nowMs = 10_000))
    }

    @Test fun rateLimitsRepeatAttemptWithin10s() {
        assertFalse(AutoConnectDecision.shouldConnect(isQcy = true, isAclConnected = true, lastAttemptMs = 5_000, nowMs = 10_000))
    }

    @Test fun allowsRetryExactlyAt10sBoundary() {
        assertTrue(AutoConnectDecision.shouldConnect(isQcy = true, isAclConnected = true, lastAttemptMs = 0, nowMs = 10_000))
    }

    @Test fun rejectsWhenAclNotConnected() {
        assertFalse(AutoConnectDecision.shouldConnect(isQcy = true, isAclConnected = false, lastAttemptMs = null, nowMs = 10_000))
    }

    @Test fun rejectsWhenUserStoppedAutoConnect() {
        assertFalse(AutoConnectDecision.shouldConnect(isQcy = true, isAclConnected = true, lastAttemptMs = null, nowMs = 10_000, userStopped = true))
    }

    @Test fun deriveLeAddressMatchesObservedPair() {
        // Gözlemlenen çift: MeloBuds Pro BR/EDR 84:…:32 ↔ LE C4:…:33
        assertEquals("C4:AC:60:E2:6B:33", AutoConnectDecision.deriveLeAddress("84:AC:60:E2:6B:32"))
    }

    @Test fun deriveLeAddressNormalizesLowercase() {
        assertEquals("C4:AC:60:E2:6B:33", AutoConnectDecision.deriveLeAddress("84:ac:60:e2:6b:32"))
    }

    @Test fun deriveLeAddressRejectsMalformedInput() {
        assertNull(AutoConnectDecision.deriveLeAddress("not-a-mac"))
        assertNull(AutoConnectDecision.deriveLeAddress("84:AC:60:E2:6B"))
        assertNull(AutoConnectDecision.deriveLeAddress("84:AC:60:E2:6B:GG"))
    }
}
