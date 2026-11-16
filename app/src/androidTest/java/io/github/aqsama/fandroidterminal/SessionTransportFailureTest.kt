// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import android.os.DeadObjectException
import androidx.test.platform.app.InstrumentationRegistry
import io.github.aqsama.fandroidterminal.runtime.SessionTransport
import io.github.aqsama.fandroidterminal.sessions.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class SessionTransportFailureTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private class DisconnectedTransport(private val exitFails: Boolean) : SessionTransport {
        var failResize = false
        var closed = false
        override suspend fun read(buffer: ByteArray): Int = awaitCancellation()
        override suspend fun write(bytes: ByteArray) {}
        override fun resize(rows: Int, columns: Int, widthPixels: Int, heightPixels: Int) { if (failResize) throw DeadObjectException() }
        override suspend fun awaitExit(): Int { if (exitFails) throw DeadObjectException(); return awaitCancellation() }
        override fun close() { closed = true }
    }
    private fun exercise(exitFails: Boolean) = runBlocking {
        lateinit var manager: SessionManager
        lateinit var session: TerminalSession
        val transport = DisconnectedTransport(exitFails)
        instrumentation.runOnMainSync {
            manager = SessionManager(instrumentation.targetContext)
            session = manager.create(transport = { transport })
        }
        try {
            if (!exitFails) {
                withTimeout(5000) { session.status.first { it == SessionStatus.Running } }
                instrumentation.runOnMainSync { transport.failResize = true; session.emulator.resize(30, 90) }
            }
            withTimeout(5000) { session.status.first { it is SessionStatus.Failed } }
            instrumentation.runOnMainSync { assertTrue(transport.closed) }
        } finally { instrumentation.runOnMainSync { manager.close() } }
    }
    @Test fun binderDeathDuringExitFailsOnlyItsSession() = exercise(true)
    @Test fun binderDeathDuringResizeFailsOnlyItsSession() = exercise(false)
}
