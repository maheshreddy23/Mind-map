package com.maheshreddy.mym.core

/**
 * Adapts inference load to device state. Thermal levels follow android.os.PowerManager.THERMAL_STATUS_*
 * (0 none, 1 light, 2 moderate, 3 severe, 4 critical, 5 emergency, 6 shutdown).
 */
object PerformancePolicy {
    data class Budget(val llmThreads: Int, val allowContinuousListening: Boolean, val warning: String?)

    fun budget(baseThreads: Int, thermalStatus: Int, powerSave: Boolean, batteryPercent: Int, charging: Boolean): Budget {
        var threads = baseThreads
        var continuous = true
        var warning: String? = null
        when {
            thermalStatus >= 4 -> {
                threads = 1; continuous = false
                warning = "Phone is very hot: the fresher is paused to cool down. Notes are still saved."
            }
            thermalStatus == 3 -> {
                threads = maxOf(1, baseThreads / 2); continuous = false
                warning = "Phone is hot: using fewer cores and push-to-talk only."
            }
            thermalStatus == 2 -> threads = maxOf(1, baseThreads - 2)
        }
        if (!charging && (powerSave || batteryPercent in 0..15)) {
            threads = minOf(threads, maxOf(1, baseThreads / 2))
            continuous = false
            warning = warning ?: "Battery saver: continuous listening is off and replies may be slower."
        }
        return Budget(threads, continuous, warning)
    }

    /** True when the LLM must not run at all (critical temperature). */
    fun shouldPauseLlm(thermalStatus: Int) = thermalStatus >= 4
}
