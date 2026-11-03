package com.maheshreddy.mym.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.maheshreddy.mym.core.PerformancePolicy
import com.maheshreddy.mym.llama.LlamaEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class DeviceState(val thermalStatus: Int, val powerSave: Boolean, val batteryPercent: Int, val charging: Boolean) {
    val budget: PerformancePolicy.Budget
        get() = PerformancePolicy.budget(LlamaEngine.defaultThreads(), thermalStatus, powerSave, batteryPercent, charging)
}

/** Tracks thermal status, battery saver and battery level so inference can back off before the phone throttles. */
@Singleton
class DeviceMonitor @Inject constructor(@ApplicationContext private val context: Context) {
    private val power = context.getSystemService(PowerManager::class.java)
    private val _state = MutableStateFlow(read(null))
    val state: StateFlow<DeviceState> = _state.asStateFlow()

    private var started = false

    @Synchronized
    fun start() {
        if (started) return
        started = true
        power.addThermalStatusListener(context.mainExecutor) { refresh(null) }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) = refresh(intent.takeIf { it.action == Intent.ACTION_BATTERY_CHANGED })
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun refresh(battery: Intent?) {
        _state.value = read(battery)
    }

    private fun read(battery: Intent?): DeviceState {
        val bm = context.getSystemService(BatteryManager::class.java)
        val pct = battery?.let { (it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100) / it.getIntExtra(BatteryManager.EXTRA_SCALE, 100) }
            ?: bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return DeviceState(
            thermalStatus = power.currentThermalStatus,
            powerSave = power.isPowerSaveMode,
            batteryPercent = pct,
            charging = bm.isCharging,
        )
    }
}
