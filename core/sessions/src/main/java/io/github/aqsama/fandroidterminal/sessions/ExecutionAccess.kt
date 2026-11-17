// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal.sessions

import android.content.*
import android.content.pm.PackageManager
import android.os.*
import io.github.aqsama.fandroidterminal.runtime.*
import io.github.aqsama.fandroidterminal.runtime.linux.*
import kotlinx.coroutines.*
import rikka.shizuku.Shizuku
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

class ExecutionAccess(private val context: Context) {
    fun rootBinary(): String? = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su").firstOrNull { File(it).canExecute() }
    suspend fun authorizeRoot(): String = withContext(Dispatchers.IO) {
        val su = rootBinary() ?: error("No root provider found")
        val process = ProcessBuilder(su, "-c", "id -u").redirectErrorStream(true).start()
        try {
            check(process.waitFor(60, TimeUnit.SECONDS)) { "Root authorization timed out" }
            check(process.exitValue() == 0 && process.inputStream.bufferedReader().readText().trim() == "0") { "Device root authorization denied" }
            su
        } finally { process.destroy() }
    }
    fun shizukuStatus(): String = when {
        !Shizuku.pingBinder() -> "Shizuku is not running. Start it in the Shizuku app."
        Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> "Authorization required"
        else -> "Authorized · UID ${Shizuku.getUid()}"
    }
    suspend fun authorizeShizuku() {
        check(Shizuku.pingBinder()) { shizukuStatus() }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) return
        var callback: Shizuku.OnRequestPermissionResultListener? = null
        try { withTimeout(60_000) {
            suspendCancellableCoroutine<Unit> { continuation ->
                val listener = Shizuku.OnRequestPermissionResultListener { code, result ->
                    if (code == 431 && continuation.isActive) {
                        if (result == PackageManager.PERMISSION_GRANTED) continuation.resumeWith(Result.success(Unit))
                        else continuation.resumeWith(Result.failure(IllegalStateException("Shizuku authorization denied")))
                    }
                }
                callback = listener
                Shizuku.addRequestPermissionResultListener(listener)
                continuation.invokeOnCancellation { Shizuku.removeRequestPermissionResultListener(listener) }
                Shizuku.requestPermission(431)
            }
        } } finally { callback?.let { Shizuku.removeRequestPermissionResultListener(it) } }
    }
    suspend fun shizukuTransport(directory: String?): Pair<SessionTransport, Int> {
        authorizeShizuku()
        val args = Shizuku.UserServiceArgs(ComponentName(context.packageName, RemoteTerminal::class.java.name))
            .tag(UUID.randomUUID().toString()).daemon(false).processNameSuffix("terminal").version(1)
        var connection: ServiceConnection? = null
        try {
            val remote = withTimeout(20_000) { suspendCancellableCoroutine<IRemoteTerminal> { cont ->
                val conn = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, binder: IBinder) { if (cont.isActive) cont.resumeWith(Result.success(IRemoteTerminal.Stub.asInterface(binder))) }
                    override fun onServiceDisconnected(name: ComponentName) { if (cont.isActive) cont.resumeWith(Result.failure(IllegalStateException("Shizuku disconnected"))) }
                }
                connection = conn
                Shizuku.bindUserService(args, conn)
            } }
            return withContext(Dispatchers.IO) {
                val owner = Binder()
                val fd = remote.open(owner, "${context.applicationInfo.nativeLibraryDir}/libtermux.so", directory ?: "/data/local/tmp")
                RemoteTransport(remote, fd, owner) { connection?.let { runCatching { Shizuku.unbindUserService(args, it, true) } } } to remote.uid()
            }
        } catch (e: Throwable) { connection?.let { runCatching { Shizuku.unbindUserService(args, it, true) } }; throw e }
    }
    suspend fun androidSpec(mode: ExecutionMode, workspace: SharedWorkspace): ShellSpec {
        val shared = withContext(Dispatchers.IO) { workspace.folderFor(null) }
        val base = AndroidShell.specification(context.filesDir, context.cacheDir)
        val cwd = if (shared != null && workspace.grant(null).start) shared.path else base.cwd
        if (mode == ExecutionMode.Android) return base.copy(cwd = cwd)
        val su = authorizeRoot()
        return base.copy(executable = su, arguments = listOf(su, "-c", "cd ${quote(if (shared != null && workspace.grant(null).start) shared.path else "/")} && exec /system/bin/env -i PATH=/system/bin:/system/xbin:/vendor/bin HOME=/ TERM=xterm-256color ANDROID_ROOT=/system ANDROID_DATA=/data /system/bin/sh -i"))
    }
    suspend fun chrootSpec(root: File, environment: LinuxEnvironment, shared: File?): ShellSpec {
        val su = authorizeRoot()
        val native = context.applicationInfo.nativeLibraryDir
        val argv = listOf("/system/bin/app_process", "/system/bin", RootChroot::class.java.name, "$native/libchroot.so", root.path,
            context.applicationInfo.uid.toString(), shared?.path ?: "", environment.startDirectory, environment.shell) + environment.guestVariables()
        return ShellSpec(su, listOf(su, "-c", "CLASSPATH=${quote(context.applicationInfo.sourceDir)} " + argv.joinToString(" ", transform = ::quote)), context.filesDir.path,
            mapOf("PATH" to "/system/bin:/system/xbin", "ANDROID_ROOT" to "/system", "ANDROID_DATA" to "/data"), cleanupOnHangup = true)
    }
    suspend fun repairOwnership(environment: LinuxEnvironment): String = withContext(Dispatchers.IO) {
        val root = EnvironmentFiles(File(context.filesDir, "environments")).root(environment.id)
        val spec = chrootSpec(root, environment.copy(startDirectory = "!repair"), null)
        val process = ProcessBuilder(spec.arguments).redirectErrorStream(true).apply { environment().putAll(spec.environment) }.start()
        try {
            check(process.waitFor(60, TimeUnit.SECONDS)) { "Ownership recovery is still running; retry after it finishes" }
            check(process.exitValue() == 0) { "Ownership recovery failed or root-chroot is still running" }
            "App ownership restored"
        } finally { process.destroy() }
    }
    companion object { fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'" }
}
