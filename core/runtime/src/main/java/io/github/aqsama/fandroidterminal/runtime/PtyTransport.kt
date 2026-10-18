// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal.runtime

import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import com.termux.terminal.JNI
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Owns all master-PTY descriptors. Exactly one reader and writer consume this transport. */
class PtyTransport private constructor(
    private val pid: Int,
    private val master: ParcelFileDescriptor,
    private val input: ParcelFileDescriptor.AutoCloseInputStream,
    private val output: ParcelFileDescriptor.AutoCloseOutputStream,
) : SessionTransport {
    private val closed = AtomicBoolean(false)
    private val descriptorLock = Any()
    @Volatile private var exited = false
    private val reaperScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val exit = reaperScope.async { JNI.waitFor(pid).also { exited = true } }
        .also { task -> task.invokeOnCompletion { reaperScope.cancel() } }

    override suspend fun read(buffer: ByteArray): Int = withContext(Dispatchers.IO) {
        try { input.read(buffer) } catch (error: IOException) {
            // Linux PTY masters report EIO when the final slave closes.
            if (closed.get() || error.cause.let { it is ErrnoException && it.errno == OsConstants.EIO }) -1 else throw error
        }
    }

    override suspend fun write(bytes: ByteArray): Unit = withContext(Dispatchers.IO) {
        if (!closed.get()) { output.write(bytes); output.flush() }
    }

    override fun resize(rows: Int, columns: Int, widthPixels: Int, heightPixels: Int) {
        require(rows in 1..1000 && columns in 1..1000)
        synchronized(descriptorLock) {
            if (!closed.get()) JNI.setPtyWindowSize(
                master.fd, rows, columns,
                if (widthPixels > 0) widthPixels / columns else 0,
                if (heightPixels > 0) heightPixels / rows else 0,
            )
        }
    }

    override suspend fun awaitExit(): Int = exit.await()

    override fun close() {
        synchronized(descriptorLock) {
            if (!closed.compareAndSet(false, true)) return
            if (!exited) {
                runCatching { Os.kill(-pid, OsConstants.SIGHUP) }
                runCatching { Os.kill(pid, OsConstants.SIGHUP) }
                killer.schedule({
                    if (!exited) {
                        runCatching { Os.kill(-pid, OsConstants.SIGKILL) }
                        runCatching { Os.kill(pid, OsConstants.SIGKILL) }
                    }
                }, 500, TimeUnit.MILLISECONDS)
            }
            runCatching { output.close() }
            runCatching { input.close() }
            runCatching { master.close() }
        }
    }

    companion object {
        private val killer = Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "pty-close").apply { isDaemon = true }
        }
        suspend fun open(spec: ShellSpec, rows: Int = 24, columns: Int = 80): PtyTransport = withContext(Dispatchers.IO) {
            require(rows in 1..1000 && columns in 1..1000)
            val processId = IntArray(1)
            val fd = JNI.createSubprocess(
                spec.executable, spec.cwd, spec.arguments.toTypedArray(),
                spec.environment.map { (key, value) -> "$key=$value" }.toTypedArray(),
                processId, rows, columns, 0, 0,
            )
            check(fd >= 0 && processId[0] > 0) { "Unable to start shell" }
            val master = ParcelFileDescriptor.adoptFd(fd)
            try {
                val input = ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(master.fileDescriptor))
                try {
                    val output = ParcelFileDescriptor.AutoCloseOutputStream(ParcelFileDescriptor.dup(master.fileDescriptor))
                    PtyTransport(processId[0], master, input, output)
                } catch (error: Throwable) { input.close(); throw error }
            } catch (error: Throwable) {
                runCatching { Os.kill(processId[0], OsConstants.SIGHUP) }
                master.close()
                withContext(kotlinx.coroutines.NonCancellable) { JNI.waitFor(processId[0]) }
                throw error
            }
        }
    }
}
