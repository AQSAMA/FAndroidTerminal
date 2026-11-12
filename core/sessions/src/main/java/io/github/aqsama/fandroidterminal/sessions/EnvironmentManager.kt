// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal.sessions

import android.content.Context
import android.os.Build
import io.github.aqsama.fandroidterminal.runtime.linux.*
import java.io.File
import java.security.KeyStore
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Service-owned installer. Its jobs outlive Activity recreation and stop cooperatively. */
class EnvironmentManager(private val context: Context, private val scope: CoroutineScope) {
    val files = EnvironmentFiles(File(context.filesDir, "environments"))
    private val mutableEnvironments = MutableStateFlow<List<LinuxEnvironment>>(emptyList())
    val environments = mutableEnvironments.asStateFlow()
    private val mutableProgress = MutableStateFlow<Map<String, String>>(emptyMap())
    val progress = mutableProgress.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    private var loaded = false
    private var job: Job? = null
    private var installingId: String? = null
    val supported: Boolean get() = Build.SUPPORTED_ABIS.firstOrNull() == "arm64-v8a"

    init {
        scope.launch {
            try {
                mutableEnvironments.value = withContext(Dispatchers.IO) {
                    files.load().map { environment ->
                        if ((environment.state == "Installing" || environment.state.startsWith("Interrupted")) &&
                            File(files.root(environment.id), ".fandroid-ready").isFile) environment.copy(state = "Ready") else environment
                    }
                }
                loaded = true
            } catch (error: Exception) { mutableError.value = "Cannot read environments: ${error.message}. Restart after checking storage." }
        }
    }
    // Main-thread admission/state publication; durable metadata operations run on IO.
    private fun publish(environment: LinuxEnvironment) {
        mutableEnvironments.value = mutableEnvironments.value.filterNot { it.id == environment.id } + environment
    }
    private suspend fun update(environment: LinuxEnvironment) = withContext(NonCancellable) {
        withContext(Dispatchers.IO) { files.save(environment) }
        publish(environment)
    }
    private fun admit() {
        check(loaded) { "Environments are still loading. Try again shortly." }
        check(job?.isActive != true && progress.value.isEmpty()) { "Wait for the current operation to finish" }
        mutableError.value = null
    }
    fun create(name: String) {
        admit()
        check(supported) { "Linux requires an ARM64 Android device" }
        check(environments.value.size < 20) { "Environment limit reached (20)" }
        val environment = LinuxEnvironment(UUID.randomUUID().toString(), EnvironmentFiles.validateName(name))
        publish(environment)
        install(environment.id)
    }
    fun rename(id: String, name: String) {
        admit()
        val environment = environments.value.first { it.id == id }
        val renamed = environment.copy(name = EnvironmentFiles.validateName(name))
        mutableProgress.value = mapOf(id to "Saving name…")
        job = scope.launch {
            try { update(renamed) }
            catch (error: Exception) { mutableError.value = "Could not save name: ${error.message}. Check storage and retry." }
            finally { mutableProgress.value = emptyMap() }
        }
    }
    fun install(id: String) {
        admit()
        check(supported) { "Linux requires an ARM64 Android device" }
        val environment = environments.value.first { it.id == id }
        check(!environment.ready) { "Environment is already installed" }
        TerminalService.ensureStarted(context)
        installingId = id
        publish(environment.copy(state = "Installing"))
        mutableProgress.value = mapOf(id to "Preparing installation…")
        job = scope.launch {
            try {
                update(environment.copy(state = "Installing"))
                withContext(Dispatchers.IO) {
                    val worker = currentCoroutineContext()
                    DebianInstaller(files, { worker.ensureActive() }, { message -> mutableProgress.value = mapOf(id to message) })
                        .install(environment, { configure(it, environment) }, { probe(it, environment) { worker.ensureActive() } })
                }
                update(environment.copy(state = "Ready"))
            } catch (error: Exception) {
                recoverInstallation(environment, error, files, ::update) { result, saveError ->
                    publish(result)
                    mutableError.value = "Cannot save environment status: ${saveError.message}. Files are preserved; check storage and reopen the app."
                }
            } finally {
                installingId = null
                mutableProgress.value = emptyMap()
            }
        }
    }
    fun cancel(id: String) {
        if (id in progress.value) job?.cancel()
    }
    fun delete(id: String, sessions: SessionManager) {
        admit()
        check(sessions.sessions.value.none { it.environmentId == id }) { "Close all sessions for this environment before deleting it" }
        val environment = environments.value.first { it.id == id }
        TerminalService.ensureStarted(context)
        publish(environment.copy(state = "Deleting"))
        mutableProgress.value = mapOf(id to "Deleting environment files…")
        job = scope.launch {
            try {
                update(environment.copy(state = "Deleting"))
                withContext(Dispatchers.IO) { files.deleteInstance(id) }
                mutableEnvironments.value = environments.value.filterNot { it.id == id }
            } catch (error: Exception) {
                val result = environment.copy(state = "Deletion incomplete: ${error.message}. Retry delete.")
                try { update(result) }
                catch (saveError: Exception) {
                    publish(result)
                    mutableError.value = "Cannot save deletion status: ${saveError.message}. Check storage and retry deletion."
                }
            } finally { mutableProgress.value = emptyMap() }
        }
    }
    fun operation(id: String, label: String, work: suspend (EnvironmentFiles, () -> Unit, (String) -> Unit) -> Unit) {
        admit()
        TerminalService.ensureStarted(context)
        mutableProgress.value = mapOf(id to label)
        job = scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val worker = currentCoroutineContext()
                    work(files, { worker.ensureActive() }, { mutableProgress.value = mapOf(id to it) })
                }
                mutableEnvironments.value = withContext(Dispatchers.IO) { files.load() }
            } catch (e: Exception) {
                mutableError.value = if (e is CancellationException) "Operation cancelled. Completed deletions cannot be undone; partial exports can be discarded." else "${e.message}. Check storage and retry."
            } finally { mutableProgress.value = emptyMap() }
        }
    }
    fun saveSettings(environment: LinuxEnvironment, sessions: SessionManager) {
        environment.validated()
        check(sessions.sessions.value.none { it.environmentId == environment.id }) { "Close this environment's sessions before changing guest settings. New sessions use these values." }
        operation(environment.id, "Saving settings…") { files, _, _ ->
            val guest = GuestFiles(files.root(environment.id))
            require(java.nio.file.Files.isDirectory(guest.resolve(environment.startDirectory))) { "Starting directory does not exist" }
            require(java.nio.file.Files.isExecutable(guest.resolve(environment.shell))) { "Guest shell is not executable" }
            guest.write("/etc/resolv.conf", "nameserver ${environment.dns}\n".byteInputStream(), true)
            files.save(environment)
        }
    }
    fun snapshot(id: String, sessions: SessionManager, output: (() -> java.io.OutputStream)? = null) {
        val environment = environments.value.first { it.id == id }
        check(environment.ready)
        check(sessions.sessions.value.none { it.environmentId == id }) { "Close all sessions for this environment for a consistent snapshot. Nothing will be terminated automatically." }
        operation(id, if (output == null) "Cloning…" else "Exporting backup…") { files, cancelled, progress ->
            val backup = EnvironmentBackup(files, cancelled, progress)
            if (output != null) output().use { backup.export(environment, it) }
            else {
                val archive = File.createTempFile("clone-", ".partial", context.cacheDir)
                try {
                    archive.outputStream().use { backup.export(environment, it) }
                    archive.inputStream().use { backup.restore(it, environment.name.take(50) + " copy") }
                } finally { archive.delete() }
            }
        }
    }
    fun restore(input: () -> java.io.InputStream) {
        check(environments.value.size < 20) { "Environment limit reached (20)" }
        operation("restore", "Restoring backup…") { files, cancelled, progress ->
            input().use { EnvironmentBackup(files, cancelled, progress).restore(it) }
        }
    }
    fun specification(id: String, directory: String? = null): io.github.aqsama.fandroidterminal.runtime.ShellSpec {
        val environment = environments.value.first { it.id == id }
        check(environment.ready && id !in progress.value) { "Environment is not ready" }
        return LinuxLaunch.specification(File(context.applicationInfo.nativeLibraryDir), files.root(id),
            File(files.instance(id), "runtime-tmp"), if (directory == null) environment else environment.copy(startDirectory = directory))
    }
    private fun configure(root: File, environment: LinuxEnvironment) {
        fun write(path: String, text: String) {
            val target = File(root, path)
            check(target.canonicalFile.toPath().startsWith(root.canonicalFile.toPath())) { "Unsafe guest configuration path" }
            check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs())
            target.writeText(text)
        }
        listOf("tmp", "var/tmp", "root", "dev", "proc", "sys").forEach { path ->
            val directory = File(root, path)
            check(directory.canonicalFile.toPath().startsWith(root.canonicalFile.toPath()))
            check(directory.isDirectory || directory.mkdirs())
        }
        write("etc/resolv.conf", "nameserver ${EnvironmentFiles.validateDns(environment.dns)}\n")
        write("usr/local/bin/fandroid-tools", context.assets.open("fandroid-tools").bufferedReader().use { it.readText() })
        File(root, "usr/local/bin/fandroid-tools").setExecutable(true, false)
        write("etc/hosts", "127.0.0.1 localhost\n::1 localhost\n")
        // Rootless dpkg cannot chown to apt's sandbox user. Run downloads as guest root.
        write("etc/apt/apt.conf.d/99fandroid", "APT::Sandbox::User \"root\";\n")
        write("usr/sbin/policy-rc.d", "#!/bin/sh\nexit 101\n")
        File(root, "usr/sbin/policy-rc.d").setExecutable(true, false)
        // Bootstrap HTTPS until Debian's ca-certificates package is installed. Android's
        // accepted roots are used; TLS verification is never disabled.
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        val certificates = factory.trustManagers.filterIsInstance<X509TrustManager>().flatMap { it.acceptedIssuers.toList() }
        check(certificates.isNotEmpty()) { "Android trust store has no certificates" }
        val encoder = Base64.getMimeEncoder(64, byteArrayOf(10))
        write("etc/ssl/certs/ca-certificates.crt", certificates.joinToString("") {
            "-----BEGIN CERTIFICATE-----\n${encoder.encodeToString(it.encoded)}\n-----END CERTIFICATE-----\n"
        })
    }
    private fun probe(root: File, environment: LinuxEnvironment, cancelled: () -> Unit) {
        val spec = LinuxLaunch.specification(File(context.applicationInfo.nativeLibraryDir), root,
            File(files.instance(environment.id), "runtime-tmp"), environment,
            listOf("/bin/sh", "-ec", "test \"\$(dpkg --print-architecture)\" = arm64; test -x /usr/bin/apt-get; test -s /etc/ssl/certs/ca-certificates.crt; test -s /etc/resolv.conf; touch /root/.fandroid-probe /tmp/.fandroid-probe; rm /root/.fandroid-probe /tmp/.fandroid-probe"))
        val output = File(files.instance(environment.id), "probe.txt")
        val builder = ProcessBuilder(spec.arguments).directory(File(spec.cwd)).redirectErrorStream(true).redirectOutput(output)
        builder.environment().clear(); builder.environment().putAll(spec.environment)
        val process = builder.start()
        try {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45)
            while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
                cancelled()
                check(System.nanoTime() < deadline && output.length() < 65536) { "Guest probe timed out. This phone may restrict PRoot tracing" }
            }
            check(process.exitValue() == 0) { "Guest probe failed (${process.exitValue()}): ${output.readText().take(2000)}" }
        } finally {
            process.destroy()
            if (!process.waitFor(500, TimeUnit.MILLISECONDS)) process.destroyForcibly()
            output.delete()
        }
    }
}

/** Recovery must publish even when its calling install job has already been cancelled. */
internal suspend fun recoverInstallation(
    environment: LinuxEnvironment,
    failure: Exception,
    files: EnvironmentFiles,
    save: suspend (LinuxEnvironment) -> Unit,
    saveFailed: (LinuxEnvironment, Exception) -> Unit,
) = withContext(NonCancellable) {
    val ready = withContext(Dispatchers.IO) { File(files.root(environment.id), ".fandroid-ready").isFile }
    val result = environment.copy(state = if (ready) "Ready" else if (failure is CancellationException)
        "Cancelled — retry when ready" else "${failure.message ?: "Installation failed"}. Check storage/network and retry.")
    try { save(result) }
    catch (error: Exception) { saveFailed(result, error) }
}
