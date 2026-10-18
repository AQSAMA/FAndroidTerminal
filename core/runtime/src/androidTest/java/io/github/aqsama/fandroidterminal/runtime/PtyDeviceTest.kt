// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal.runtime

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PtyDeviceTest {
    @Test fun androidShellHasATerminalAndReturnsItsExitStatus() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val base = AndroidShell.specification(context.filesDir, context.cacheDir)
        val process = PtyTransport.open(base.copy(arguments = listOf("/system/bin/sh", "-c", "test -t 0 && test -t 1 && stty size; exit 23")), 31, 97)
        try {
            val text = StringBuilder()
            withTimeout(8000) {
                val bytes = ByteArray(4096)
                while (true) { val count = process.read(bytes); if (count < 0) break; text.append(String(bytes, 0, count)) }
                assertEquals(23, process.awaitExit())
            }
            assertTrue(text.toString(), text.contains("31 97"))
            process.close() // idempotent cleanup
        } finally { process.close() }
    }
}
