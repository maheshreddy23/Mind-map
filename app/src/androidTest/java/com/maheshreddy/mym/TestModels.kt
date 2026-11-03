package com.maheshreddy.mym

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** Streams a model pushed to /data/local/tmp/models (see scripts/push-test-models.sh) into [dest]. */
object TestModels {
    fun copyTo(name: String, dest: File): File {
        if (dest.isFile && dest.length() > 0) return dest
        dest.parentFile?.mkdirs()
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("cat /data/local/tmp/models/$name")
        ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input -> dest.outputStream().use { input.copyTo(it, 1 shl 20) } }
        check(dest.length() > 0) { "Test model $name is missing on the device" }
        return dest
    }
}
