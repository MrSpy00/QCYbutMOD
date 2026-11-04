package com.aegissoft.qcybutmod.core.audio

import com.aegissoft.qcybutmod.core.models.QcyDeviceState
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FitTestGateTest {

    @Test
    fun `bagli degilse test baslatilamaz - NOT_CONNECTED`() {
        val failed = FitTestManager.gateFailureFor(QcyDeviceState(isConnected = false))
        assertNotNull(failed)
        assertTrue(failed!!.kind == FitTestError.NOT_CONNECTED)
        assertTrue(failed.reason.startsWith("Kulaklık bağlı değil"))
    }

    @Test
    fun `bagliysa gate gecirir`() {
        assertNull(FitTestManager.gateFailureFor(QcyDeviceState(isConnected = true)))
    }

    @Test
    fun `varsayilan state bagli degildir`() {
        val failed = FitTestManager.gateFailureFor(QcyDeviceState())
        assertNotNull(failed)
        assertTrue(failed!!.kind == FitTestError.NOT_CONNECTED)
    }
}
