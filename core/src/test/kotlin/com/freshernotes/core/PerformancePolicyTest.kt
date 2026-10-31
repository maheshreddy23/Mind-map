package com.freshernotes.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformancePolicyTest {
    @Test fun normalUsesAllThreads() {
        val b = PerformancePolicy.budget(6, 0, powerSave = false, batteryPercent = 80, charging = false)
        assertEquals(6, b.llmThreads); assertTrue(b.allowContinuousListening); assertNull(b.warning)
    }

    @Test fun severeHalvesThreadsAndDisablesContinuous() {
        val b = PerformancePolicy.budget(6, 3, powerSave = false, batteryPercent = 80, charging = false)
        assertEquals(3, b.llmThreads); assertFalse(b.allowContinuousListening)
    }

    @Test fun criticalPausesLlm() {
        assertTrue(PerformancePolicy.shouldPauseLlm(4))
        assertEquals(1, PerformancePolicy.budget(6, 4, false, 80, false).llmThreads)
    }

    @Test fun lowBatteryThrottlesUnlessCharging() {
        assertEquals(3, PerformancePolicy.budget(6, 0, false, 10, charging = false).llmThreads)
        assertEquals(6, PerformancePolicy.budget(6, 0, false, 10, charging = true).llmThreads)
    }
}
