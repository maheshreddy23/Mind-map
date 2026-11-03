package com.maheshreddy.mym.llama

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/**
 * CI pushes test models to /data/local/tmp/models (see scripts/push-test-models.sh). Apps cannot read that
 * directory directly, so the file is streamed through a shell command, which runs with the shell's permissions.
 */
object TestModels {
    const val DEVICE_DIR = "/data/local/tmp/models"

    fun copy(name: String): File {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val dest = File(instrumentation.targetContext.cacheDir, name)
        if (dest.isFile && dest.length() > 0) return dest
        val tmp = File(dest.path + ".part")
        val pfd = instrumentation.uiAutomation.executeShellCommand("cat $DEVICE_DIR/$name")
        ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
            tmp.outputStream().use { input.copyTo(it, 1 shl 20) }
        }
        check(tmp.length() > 0) { "Test model $DEVICE_DIR/$name is missing. Run scripts/push-test-models.sh first." }
        tmp.renameTo(dest)
        return dest
    }
}
