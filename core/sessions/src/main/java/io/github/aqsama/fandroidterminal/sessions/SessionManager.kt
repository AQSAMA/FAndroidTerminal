// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal.sessions

import android.content.Context
import androidx.compose.ui.graphics.Color
import io.github.aqsama.fandroidterminal.runtime.AndroidShell
import io.github.aqsama.fandroidterminal.runtime.PtyTransport
import io.github.aqsama.fandroidterminal.runtime.SessionTransport
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.connectbot.terminal.TerminalEmulator
import org.connectbot.terminal.TerminalEmulatorFactory

sealed interface SessionStatus {
    data object Starting : SessionStatus
    data object Running : SessionStatus
    data class Exited(val code: Int) : SessionStatus
    data class Failed(val message: String) : SessionStatus
}

/** One owner per terminal; the UI borrows this record and never disposes it. */
class TerminalSession internal constructor(val title: String) : AutoCloseable {
    val id: String = UUID.randomUUID().toString()
    private val closed = AtomicBoolean(false)
    private val terminalLock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val keyboard = Channel<ByteArray>(64)
    private var transport: SessionTransport? = null
    private val mutableStatus = MutableStateFlow<SessionStatus>(SessionStatus.Starting)
    val status: StateFlow<SessionStatus> = mutableStatus.asStateFlow()
    val emulator: TerminalEmulator = TerminalEmulatorFactory.create(
        defaultForeground = Color(0xFFE6E1E5), defaultBackground = Color(0xFF141218),
        onKeyboardInput = { bytes ->
            if (!closed.get() && mutableStatus.value !is SessionStatus.Exited && mutableStatus.value !is SessionStatus.Failed) scope.launch {
                try { keyboard.send(bytes.copyOf()) } catch (_: ClosedSendChannelException) { /* shell exited */ }
            }
        },
        onResize = { size ->
            transport?.resize(size.rows, size.columns, size.widthPixels, size.heightPixels)
        },
        // Remote/child output must not silently replace the Android clipboard.
        onClipboardCopy = null,
    )

    internal fun start(context: Context) {
        scope.launch {
            try {
                // A close during process creation must still dispose the returned descriptor.
                val opened = withContext(NonCancellable) {
                    PtyTransport.open(AndroidShell.specification(context.filesDir, context.cacheDir))
                }
                if (closed.get()) { opened.close(); return@launch }
                transport = opened
                opened.resize(emulator.dimensions.rows.coerceAtLeast(1), emulator.dimensions.columns.coerceAtLeast(1))
                mutableStatus.value = SessionStatus.Running
                scope.launch {
                    try {
                        for (bytes in keyboard) opened.write(bytes)
                    } catch (error: Exception) {
                        if (error !is CancellationException && !closed.get()) fail(error)
                    }
                }
                val reader = scope.launch(Dispatchers.IO) {
                    val buffer = ByteArray(16 * 1024)
                    try {
                        while (!closed.get()) {
                            val count = opened.read(buffer)
                            if (count < 0) break
                            if (count > 0) synchronized(terminalLock) {
                                if (!closed.get()) emulator.writeInput(buffer, 0, count)
                            }
                        }
                    } catch (error: Exception) {
                        if (error !is CancellationException && !closed.get()) {
                            withContext(Dispatchers.Main) { fail(error) }
                        }
                    }
                }
                scope.launch {
                    val code = opened.awaitExit()
                    keyboard.close()
                    // waitpid may finish before the reader consumes the final PTY bytes.
                    // Bound the drain: a detached descendant may still hold the slave open.
                    withTimeoutOrNull(1000) { reader.join() }
                    opened.close()
                    if (!closed.get()) {
                        mutableStatus.value = SessionStatus.Exited(code)
                        synchronized(terminalLock) {
                            if (!closed.get()) emulator.writeInput("\r\n[Session finished: $code]\r\n".toByteArray())
                        }
                    }
                }
            } catch (error: Exception) {
                if (error !is CancellationException && !closed.get()) fail(error)
            }
        }
    }

    private fun fail(error: Exception) {
        mutableStatus.value = SessionStatus.Failed(error.message ?: error.javaClass.simpleName)
        transport?.close()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        keyboard.close()
        transport?.close()
        scope.cancel()
        synchronized(terminalLock) { emulator.close() }
    }
}

class SessionManager(private val context: Context) : AutoCloseable {
    private var sequence = 0
    private val mutableSessions = MutableStateFlow<List<TerminalSession>>(emptyList())
    val sessions: StateFlow<List<TerminalSession>> = mutableSessions.asStateFlow()

    fun create(): TerminalSession {
        check(mutableSessions.value.size < 12) { "Close a session before opening another (limit: 12)" }
        val session = TerminalSession("Shell ${++sequence}")
        mutableSessions.value += session
        session.start(context)
        return session
    }

    fun remove(id: String) {
        val session = mutableSessions.value.firstOrNull { it.id == id } ?: return
        mutableSessions.value = mutableSessions.value.filterNot { it.id == id }
        session.close()
    }

    override fun close() {
        val old = mutableSessions.value
        mutableSessions.value = emptyList()
        old.forEach(TerminalSession::close)
    }
}
