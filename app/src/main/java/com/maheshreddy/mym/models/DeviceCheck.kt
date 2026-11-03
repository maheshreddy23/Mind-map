package com.maheshreddy.mym.models

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.maheshreddy.mym.core.models.DeviceProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** The device check for model recommendations: total RAM, chip (SoC) and CPU cores. */
@Singleton
class DeviceCheck @Inject constructor(@ApplicationContext private val context: Context) {
    val profile: DeviceProfile by lazy {
        val mi = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
        val soc = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else Build.HARDWARE
        DeviceProfile.of((mi.totalMem / 1_048_576).toInt(), soc, Runtime.getRuntime().availableProcessors())
    }

    /** e.g. "OnePlus CPH2691 · Snapdragon (SM8650) · 12 GB RAM". */
    val description: String by lazy {
        val p = profile
        listOfNotNull("${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}", p.socModel, "${p.ramGb} GB RAM", "${p.cores} cores").joinToString(" · ")
    }
}
