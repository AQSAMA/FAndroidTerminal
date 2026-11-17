// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal.sessions

import android.os.*
import android.system.Os
import android.system.OsConstants
import com.termux.terminal.JNI
import io.github.aqsama.fandroidterminal.runtime.SessionTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// User-service lifecycle follows Shizuku-API demo at a27f6e4151ba7b39965ca47edb2bf0aeed7102e5 (MIT).
/** Shizuku user-service entry point. One service process per session, killed with its owner. */
class RemoteTerminal : IRemoteTerminal.Stub() {
    private var pid = 0
    private var master: ParcelFileDescriptor? = null
    override fun uid() = Os.getuid()
    @Synchronized override fun open(owner: IBinder, library: String, cwd: String): ParcelFileDescriptor {
        check(master == null)
        System.load(library)
        owner.linkToDeath({ destroy() }, 0)
        val ids = IntArray(1)
        val fd = JNI.createSubprocess("/system/bin/sh", cwd, arrayOf("/system/bin/sh", "-i"),
            arrayOf("HOME=/data/local/tmp", "PATH=/system/bin:/system/xbin:/vendor/bin", "TERM=xterm-256color", "ANDROID_ROOT=/system", "ANDROID_DATA=/data"), ids, 24, 80, 0, 0)
        check(fd >= 0 && ids[0] > 0) { "Shizuku PTY creation failed" }
        pid = ids[0]; master = ParcelFileDescriptor.adoptFd(fd)
        return ParcelFileDescriptor.dup(master!!.fileDescriptor)
    }
    @Synchronized override fun resize(rows: Int, columns: Int, width: Int, height: Int) {
        require(rows in 1..1000 && columns in 1..1000)
        master?.let { JNI.setPtyWindowSize(it.fd, rows, columns, width / columns, height / rows) }
    }
    override fun waitForExit() = JNI.waitFor(pid)
    @Synchronized override fun close() { if (pid > 0) { runCatching { Os.kill(-pid, OsConstants.SIGKILL) }; pid = 0 }; master?.close(); master = null }
    override fun destroy() { close(); kotlin.system.exitProcess(0) }
}

internal class RemoteTransport(private val remote: IRemoteTerminal, private val fd: ParcelFileDescriptor, private val owner: Binder, private val release: () -> Unit) : SessionTransport {
    private val input = ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(fd.fileDescriptor))
    private val output = ParcelFileDescriptor.AutoCloseOutputStream(ParcelFileDescriptor.dup(fd.fileDescriptor))
    override suspend fun read(buffer: ByteArray) = withContext(Dispatchers.IO) { try { input.read(buffer) } catch (_: java.io.IOException) { -1 } }
    override suspend fun write(bytes: ByteArray) = withContext(Dispatchers.IO) { output.write(bytes); output.flush() }
    override fun resize(rows: Int, columns: Int, widthPixels: Int, heightPixels: Int) { remote.resize(rows, columns, widthPixels, heightPixels) }
    override suspend fun awaitExit() = withContext(Dispatchers.IO) { remote.waitForExit() }
    override fun close() { runCatching { remote.destroy() }; runCatching { input.close() }; runCatching { output.close() }; runCatching { fd.close() }; release() }
}
