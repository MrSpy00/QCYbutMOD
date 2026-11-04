package com.aegissoft.qcybutmod.core.bluetooth

import org.junit.Assert.assertFalse
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
}
