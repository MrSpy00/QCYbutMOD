package com.aegissoft.qcybutmod.core.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class NoiseMonitorTest {
    @Test fun fullScaleRmsMapsToZeroDb() = assertEquals(0f, rmsToDbfs(32767.0), 0.01f)
    @Test fun tenthScaleRmsMapsToMinus20Db() = assertEquals(-20f, rmsToDbfs(3276.7), 0.01f)
    @Test fun silenceFloorsAtMinus90Db() = assertEquals(-90f, rmsToDbfs(0.0), 0.01f)
    @Test fun subFloorInputClampsToMinus90Db() = assertEquals(-90f, rmsToDbfs(1.0), 0.01f)
}
