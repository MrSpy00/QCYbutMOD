package com.aegissoft.qcybutmod.core.bluetooth

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionMemoryTest {

    private fun snapshot(feature: String, attemptedAt: Long = 1_000L, success: Boolean = true) =
        ActionSnapshot(
            feature = feature,
            targetDevice = "QCY MeloBuds Pro",
            attemptedAt = attemptedAt,
            success = success,
            detail = ""
        )

    @Test
    fun capKeeps20NewestFirst() {
        var history = emptyList<ActionSnapshot>()
        repeat(25) { index ->
            history = ActionMemory.appendCapped(
                history,
                snapshot(feature = "işlemin_$index", attemptedAt = index.toLong())
            )
        }
        assertEquals(20, history.size)
        // newest (index 24) must be first, oldest retained index is 5
        assertEquals("işlemin_24", history.first().feature)
        assertEquals("işlemin_5", history.last().feature)
        // retained timestamps must stay strictly descending
        assertTrue(
            history.zipWithNext().all { (a, b) -> a.attemptedAt > b.attemptedAt }
        )
    }

    @Test
    fun parseHistoryRoundtripPreservesTwoItemOrderAndValues() {
        val original = listOf(
            snapshot(feature = "ANC modu", attemptedAt = 42_000L, success = true),
            snapshot(feature = "Oyun modu", attemptedAt = 10_000L, success = false)
        )
        val parsed = ActionMemory.parseHistory(Gson().toJson(original))
        assertEquals(2, parsed.size)
        assertEquals(original, parsed)
    }

    @Test
    fun parseHistoryGarbageYieldsEmptyList() {
        assertTrue(ActionMemory.parseHistory("garbage").isEmpty())
        assertTrue(ActionMemory.parseHistory(null).isEmpty())
        assertTrue(ActionMemory.parseHistory("").isEmpty())
    }

    @Test
    fun historyLimitIsTwenty() {
        assertEquals(20, ActionMemory.HISTORY_LIMIT)
    }
}
