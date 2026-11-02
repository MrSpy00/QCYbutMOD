package com.aegissoft.qcybutmod.core.bluetooth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptMutePolicyTest {
    @Test fun remutesWhenNotFirmwareAndVolumeRaised() { assertTrue(PromptMutePolicy.shouldRemute("aegis_tr", 6, 10_000, 0)) }
    @Test fun remutesWhenSourceOff() { assertTrue(PromptMutePolicy.shouldRemute("off", 6, 10_000, 0)) }
    @Test fun neverRemutesInFirmwareMode() { assertFalse(PromptMutePolicy.shouldRemute("firmware_en", 6, 10_000, 0)) }
    @Test fun neverWhenVolumeZero() { assertFalse(PromptMutePolicy.shouldRemute("aegis_tr", 0, 10_000, 0)) }
    @Test fun debounceSuppressesEchoStorm() { assertFalse(PromptMutePolicy.shouldRemute("aegis_tr", 6, 1_500, 0)) }
    @Test fun debounceAllowsAfterWindow() { assertTrue(PromptMutePolicy.shouldRemute("aegis_tr", 6, 3_000, 0)) }
}
