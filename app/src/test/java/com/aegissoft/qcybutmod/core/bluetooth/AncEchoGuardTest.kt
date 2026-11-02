package com.aegissoft.qcybutmod.core.bluetooth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AncEchoGuardTest {
    @Test fun staleEchoFromPreviousStateIgnored() {
        val g = AncEchoGuard(2500)
        g.onSent(intArrayOf(3, 1, 6), 0)             // şeffaf level 6 gönderildi
        assertFalse(g.shouldApply(intArrayOf(1, 5, 0), 100)) // dup eski ANC yankısı
    }
    @Test fun matchingEchoAccepted() {
        val g = AncEchoGuard(2500); g.onSent(intArrayOf(3, 1, 6), 0)
        assertTrue(g.shouldApply(intArrayOf(3, 1, 6), 100))
    }
    @Test fun externalChangeAfterWindowAccepted() {
        val g = AncEchoGuard(2500); g.onSent(intArrayOf(2, 0, 0), 0)
        assertTrue(g.shouldApply(intArrayOf(1, 5, 0), 3000))
    }
    @Test fun differentValueAfterMatchingAcceptedOnceCleared() {
        val g = AncEchoGuard(2500); g.onSent(intArrayOf(3, 1, 6), 0)
        assertTrue(g.shouldApply(intArrayOf(3, 1, 6), 100))    // yankı kabul, bekleyen temizlendi
        assertTrue(g.shouldApply(intArrayOf(1, 5, 0), 200))    // yeni değer artık serbest
    }
}
