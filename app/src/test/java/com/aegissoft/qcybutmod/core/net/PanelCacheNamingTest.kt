package com.aegissoft.qcybutmod.core.net

import org.junit.Assert.assertEquals
import org.junit.Test

class PanelCacheNamingTest {
    @Test fun sanitizesFirmwareForFilename() { assertEquals("panel_19786_v1_2_3.json", PanelRepository.panelFileName(19786, "v1/2.3")) }
    @Test fun plainFirmwareKeptReadable() { assertEquals("panel_32884_v2.0.1.json", PanelRepository.panelFileName(32884, "v2.0.1")) }
}
