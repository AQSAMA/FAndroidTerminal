// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal.sessions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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

/** Field identity survives asynchronous filesystem validation without parsing diagnostic text. */
class GuestSettingsFailure(val field: Field, message: String) : IllegalArgumentException(message) {
    enum class Field { StartingFolder, ShellProgram }
}

/** Service-owned installer. Its jobs outlive Activity recreation and stop cooperatively. */
class EnvironmentManager(private val context: Context, private val scope: CoroutineScope) {
    val defaults = GuestDefaults(context)
    val workspaceRestore = MutableStateFlow<WorkspaceBackup.PreparedRestore?>(null)
    val operationResults = MutableStateFlow<Map<String, String>>(emptyMap())
    val files = EnvironmentFiles(File(context.filesDir, "environments"))
    private val mutableEnvironments = MutableStateFlow<List<LinuxEnvironment>>(emptyList())
    val environments = mutableEnvironments.asStateFlow()
    private val mutableProgress = MutableStateFlow<Map<String, String>>(emptyMap())
    val progress = mutableProgress.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    fun closePreparedRestore() {
        val prepared = workspaceRestore.value
        workspaceRestore.value = null
        if (prepared != null) scope.launch(NonCancellable + Dispatchers.IO) { prepared.close() }
    }
    fun reportError(message: String) { mutableError.value = message }
    fun dismissError() { mutableError.value = null }
    var isLoaded by androidx.compose.runtime.mutableStateOf(false); private set
    private var loaded = false
    private var job: Job? = null
    private var batchJob: Job? = null
    private var batchAdvancing = false
    val packageBatchRunning = MutableStateFlow(false)
    val packageBatchOutcomes = MutableStateFlow<Map<String, String>>(emptyMap())
    private var installingId: String? = null
    val supported: Boolean get() = Build.SUPPORTED_ABIS.firstOrNull() == "arm64-v8a"

    init {
        scope.launch {
            try {
                mutableEnvironments.value = withContext(Dispatchers.IO) {
                    context.cacheDir.listFiles().orEmpty().filter { (it.name.startsWith("clone-") || it.name.startsWith("backup-bundle-")) && it.name.endsWith(".partial") }.forEach { java.nio.file.Files.deleteIfExists(it.toPath()) }
                    files.recoverRestores()
                    files.load().map { environment ->
                        if ((environment.state == "Installing" || environment.state.startsWith("Interrupted")) &&
                            File(files.root(environment.id), ".fandroid-ready").isFile) environment.copy(state = "Ready") else environment
                    }
                }
                loaded = true; isLoaded = true
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
        check(!packageBatchRunning.value || batchAdvancing) { "Wait for the current operation to finish" }
        check(loaded) { "Environments are still loading. Try again shortly." }
        check(job?.isActive != true && progress.value.isEmpty()) { "Wait for the current operation to finish" }
        mutableError.value = null
    }
    fun create(name: String, distribution: String = "debian") {
        admit()
        check(supported) { "Linux requires an ARM64 Android device" }
        check(environments.value.size < 20) { "Environment limit reached (20)" }
        val image = LinuxImages.get(distribution)
        val environment = LinuxEnvironment(UUID.randomUUID().toString(), EnvironmentFiles.validateName(name), shell = image.shell, distribution = distribution, release = image.version, packagePlan = defaults.profile(distribution), inheritShell = true, inheritDirectory = true)
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
                    LinuxInstaller(files, { worker.ensureActive() }, { message -> mutableProgress.value = mapOf(id to message) })
                        .install(environment, { configure(it, environment) }, { probe(it, environment) { worker.ensureActive() } })
                }
                update(environment.copy(state = "Ready", provisioning = if (environment.packagePlan.isBlank()) "" else "Pending"))
                if (environment.packagePlan.isNotBlank()) { val worker = currentCoroutineContext(); provision(environment.copy(state = "Ready"), { worker.ensureActive() }) }
                else applyDefaults(environment.copy(state = "Ready"))

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
        if (id in progress.value) { batchJob?.cancel(); job?.cancel() }
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
    fun operation(id: String, label: String, completed: (Exception?) -> Unit = {}, work: suspend (EnvironmentFiles, () -> Unit, (String) -> Unit) -> Unit) {
        admit()
        TerminalService.ensureStarted(context)
        mutableProgress.value = mapOf(id to label)
        job = scope.launch {
            var failure: Exception? = null
            try {
                withContext(Dispatchers.IO) {
                    val worker = currentCoroutineContext()
                    work(files, { worker.ensureActive() }, { mutableProgress.value = mapOf(id to it) })
                }
                mutableEnvironments.value = withContext(Dispatchers.IO) { files.load() }
            } catch (e: Exception) {
                failure = e
                mutableError.value = if (e is CancellationException) "Operation cancelled. Completed deletions cannot be undone; partial exports can be discarded." else "${e.message}. Check storage and retry."
            } finally { withContext(NonCancellable) { operationResults.value = operationResults.value + (id to (failure?.message ?: "Complete")); mutableProgress.value = emptyMap(); completed(failure) } }
        }
    }
    fun fileOperation(id: String, sessions: SessionManager, label: String, completed: (Exception?) -> Unit = {}, work: (GuestFiles) -> Unit) {
        check(sessions.sessions.value.none { it.environmentId == id }) { "Close this environment’s sessions before using Files to prevent concurrent path changes. Nothing will be terminated automatically." }
        operation(id, label, completed) { files, cancel, _ -> work(GuestFiles(files.root(id), cancel)) }
    }
    fun saveSettings(environment: LinuxEnvironment, sessions: SessionManager, completed: (Exception?) -> Unit = {}) {
        environment.validated()
        check(sessions.sessions.value.none { it.environmentId == environment.id }) { "Close this environment's sessions before changing guest settings. New sessions use these values." }
        operation(environment.id, "Saving settings…", completed) { files, _, _ ->
            val guest = GuestFiles(files.root(environment.id))
            if (!java.nio.file.Files.isDirectory(guest.resolve(environment.startDirectory))) throw GuestSettingsFailure(GuestSettingsFailure.Field.StartingFolder, "Starting directory does not exist")
            if (!java.nio.file.Files.isExecutable(guest.resolve(environment.shell))) throw GuestSettingsFailure(GuestSettingsFailure.Field.ShellProgram, "Guest shell is not executable")
            guest.write("/etc/resolv.conf", "nameserver ${environment.dns}\n".byteInputStream(), true)
            files.save(environment)
        }
    }
    fun snapshot(id: String, sessions: SessionManager, output: (() -> java.io.OutputStream)? = null) {
        val environment = environments.value.first { it.id == id }
        check(environment.ready)
        if (output == null) check(environments.value.size < 20) { "Environment limit reached (20)" }
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


    private suspend fun provision(environment: LinuxEnvironment, cancel: () -> Unit) {
        try {
            mutablePackageOutput.value = mutablePackageOutput.value + (environment.id to "")
            update(environment.copy(provisioning = "Installing packages"))
            withContext(Dispatchers.IO) {
                fun run(argv: List<String>) {
                    val result = GuestCommand.run(LinuxLaunch.specification(File(context.applicationInfo.nativeLibraryDir),
                        files.root(environment.id), File(files.instance(environment.id), "runtime-tmp"),
                        environment.copy(startDirectory = "/root"), argv), cancel) {
                        mutableProgress.value = mapOf(environment.id to "Provisioning packages…")
                        mutablePackageOutput.value = mutablePackageOutput.value + (environment.id to (mutablePackageOutput.value[environment.id].orEmpty() + it).takeLast(12000))
                    }
                    check(result.exitCode == 0) { "Package manager exited ${result.exitCode}; retry in Packages" }
                }
                val alpine = environment.distribution == "alpine"
                run(if (alpine) listOf("apk", "update") else listOf("apt-get", "update", "-o", "APT::Update::Error-Mode=any"))
                run((if (alpine) listOf("apk", "add") else listOf("/usr/bin/env", "DEBIAN_FRONTEND=noninteractive", "apt-get", "-y", "install", "--")) + PackageMetadata.names(environment.packagePlan))
            }
            applyDefaults(environment.copy(provisioning = "Complete"))
        } catch (failure: Exception) {
            withContext(NonCancellable) { applyDefaults(environment.copy(provisioning = if (failure is CancellationException) "Cancelled; rootfs ready. Retry or skip packages." else "Failed; rootfs ready. Retry or edit packages.")) }
        }
    }
    private suspend fun applyDefaults(environment: LinuxEnvironment) {
        val configured = withContext(Dispatchers.IO) {
            val guest = GuestFiles(files.root(environment.id))
            val preferred = defaults.shell(environment.distribution)
            val directory = defaults.directory(environment.distribution)
            environment.copy(shell = preferred?.takeIf { runCatching { java.nio.file.Files.isExecutable(guest.resolve(it)) }.getOrDefault(false) } ?: environment.shell,
                startDirectory = directory.takeIf { runCatching { java.nio.file.Files.isDirectory(guest.resolve(it)) }.getOrDefault(false) } ?: environment.startDirectory)
        }
        update(configured)
    }
    fun retryProvision(id: String, sessions: SessionManager, plan: String?) {
        check(sessions.sessions.value.none { it.environmentId == id }) { "Close this environment’s sessions first" }
        val environment = environments.value.first { it.id == id && it.ready }
        plan?.let { PackageMetadata.names(it) }
        operation(id, "Provisioning packages…") { _, cancel, _ ->
            withContext(Dispatchers.Main) {
                if (plan == null || plan.isBlank()) applyDefaults(environment.copy(packagePlan = plan ?: environment.packagePlan, provisioning = "Skipped"))
                else provision(environment.copy(packagePlan = plan), cancel)
            }
        }
    }
    data class PackageSnapshot(val packages: List<GuestPackage>, val readAt: Long, val refreshedAt: Long? = null)
    private val mutablePackages = MutableStateFlow<Map<String, PackageSnapshot>>(emptyMap())
    val packages = mutablePackages.asStateFlow()
    private val mutablePackageOutput = MutableStateFlow<Map<String, String>>(emptyMap())
    val packageOutput = mutablePackageOutput.asStateFlow()
    val packageReview = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    fun batchPackages(plans: Map<String, List<String>>, sessions: SessionManager, action: String) {
        admit()
        require(plans.isNotEmpty() && plans.size <= 20)
        require(action in listOf("Review", "Review upgrades", "Install", "Upgrade"))
        packageBatchRunning.value = true
        packageBatchOutcomes.value = plans.mapValues { "Pending" }
        batchJob = scope.launch {
            try {
                for ((id, names) in plans) {
                    ensureActive()
                    try {
                        batchAdvancing = true
                        try { packageAction(id, sessions, action, names) } finally { batchAdvancing = false }
                        job?.join()
                        packageBatchOutcomes.value = packageBatchOutcomes.value + (id to operationResults.value[id].orEmpty())
                    } catch (failure: CancellationException) { throw failure }
                    catch (failure: Exception) { packageBatchOutcomes.value = packageBatchOutcomes.value + (id to (failure.message ?: "Failed")) }
                }
            } finally {
                packageBatchOutcomes.value = packageBatchOutcomes.value.mapValues { if (it.value == "Pending") "Cancelled" else it.value }
                packageBatchRunning.value = false
            }
        }
    }
    fun packageAction(id: String, sessions: SessionManager, action: String, names: List<String> = emptyList()) {
        check(sessions.sessions.value.none { it.environmentId == id }) { "Close this environment’s sessions before package operations. No sessions are terminated automatically." }
        val environment = environments.value.first { it.id == id }
        check(environment.ready)
        require(action in listOf("Read", "Refresh", "Review", "Install", "Upgrade", "Repair", "Review upgrades", "Details"))
        val selected = PackageMetadata.names(names.joinToString(" "))
        require(action !in listOf("Review", "Install") || selected.isNotEmpty())
        if (action in listOf("Install", "Upgrade")) check(packageReview.value[id] == selected) { "Review this operation first" }
        packageReview.value = packageReview.value - id
        val alpine = environment.distribution == "alpine"
        operation(id, "Packages: $action") { _, cancel, report ->
            fun run(argv: List<String>, visible: Boolean = true): String {
                val spec = LinuxLaunch.specification(File(context.applicationInfo.nativeLibraryDir), files.root(id),
                    File(files.instance(id), "runtime-tmp"), environment.copy(startDirectory = "/root"), argv)
                val result = GuestCommand.run(spec, cancel) { output ->
                    if (visible) mutablePackageOutput.value = mutablePackageOutput.value + (id to ((mutablePackageOutput.value[id].orEmpty() + output).takeLast(12000)))
                }
                if (visible) mutablePackageOutput.value = mutablePackageOutput.value + (id to result.output.takeLast(12000))
                check(result.exitCode == 0) { "Package command exited ${result.exitCode}. Review package output. Locks are never removed; wait for other managers, then retry. Interrupted dpkg operations may need Repair." }
                return result.output
            }
            if (action == "Details") {
                require(selected.size == 1)
                val old = mutablePackages.value[id] ?: error("Read packages first")
                val name = selected.single().substringBefore(':'); val arch = selected.single().substringAfter(':', "")
                if (!alpine) {
                    val text = run(listOf("apt-cache", "show", "--no-all-versions", selected.single()), false)
                    mutablePackages.value = mutablePackages.value + (id to old.copy(packages = old.packages.map { row ->
                        if (row.name == name && (arch.isEmpty() || row.architecture == arch)) PackageMetadata.aptDetails(row, text) else row
                    }))
                }
                return@operation
            }
            when (action) {
                "Refresh" -> run(if (alpine) listOf("apk", "update") else listOf("apt-get", "update", "-o", "APT::Update::Error-Mode=any"))
                "Review" -> { run(if (alpine) listOf("apk", "add", "--simulate") + selected else listOf("apt-get", "--simulate", "install", "--") + selected); packageReview.value = packageReview.value + (id to selected); return@operation }
                "Review upgrades" -> { run(if (alpine) listOf("apk", "upgrade", "--simulate") else listOf("apt-get", "--simulate", "upgrade")); packageReview.value = packageReview.value + (id to emptyList()); return@operation }
                "Install" -> run(if (alpine) listOf("apk", "add") + selected else listOf("/usr/bin/env", "DEBIAN_FRONTEND=noninteractive", "apt-get", "-y", "install", "--") + selected)
                "Upgrade" -> run(if (alpine) listOf("apk", "upgrade") else listOf("/usr/bin/env", "DEBIAN_FRONTEND=noninteractive", "apt-get", "-y", "upgrade"))
                "Repair" -> run(if (alpine) listOf("apk", "fix") else listOf("/usr/bin/env", "DEBIAN_FRONTEND=noninteractive", "dpkg", "--configure", "-a"))
            }
            report("Reading package metadata…")
            val available = run(if (alpine) listOf("apk", "search", "-v") else listOf("apt", "list"), false)
            val installed = run(if (alpine) listOf("apk", "info", "-v") else listOf("apt", "list", "--installed"), false)
            val rows = if (alpine) PackageMetadata.apk(available, installed, run(listOf("apk", "--print-arch"), false), run(listOf("apk", "version", "-l", "<"), false), run(listOf("cat", "/lib/apk/db/installed"), false)) else PackageMetadata.apt(available, installed,
                run(listOf("apt-cache", "search", "."), false),
                run(listOf("dpkg-query", "-W", "-f=" + "\${Package}\t\${Architecture}\t\${Installed-Size}\n"), false))
            val old = mutablePackages.value[id]
            mutablePackages.value = mutablePackages.value + (id to PackageSnapshot(rows, System.currentTimeMillis(), if (action == "Refresh") System.currentTimeMillis() else old?.refreshedAt))
        }
    }
    fun installOpenCode(id: String, sessions: SessionManager, saveLauncher: suspend (CliLauncher) -> Unit) {
        val environment = environments.value.first { it.id == id && it.ready }
        check(environment.distribution in listOf("debian", "ubuntu")) { "This OpenCode build requires ARM64 glibc; choose Debian or Ubuntu." }
        check(sessions.sessions.value.none { it.environmentId == id }) { "Close this environment’s sessions first" }
        val prerequisites = listOf("ca-certificates", "curl", "tar")
        check(packageReview.value[id] == prerequisites) { "Review this operation first" }
        operation(id, "Installing OpenCode…") { _, cancel, _ ->
            fun run(argv: List<String>) {
                val result = GuestCommand.run(LinuxLaunch.specification(File(context.applicationInfo.nativeLibraryDir), files.root(id),
                    File(files.instance(id), "runtime-tmp"), environment.copy(startDirectory = "/root"), argv), cancel) { output ->
                    mutablePackageOutput.value = mutablePackageOutput.value + (id to (mutablePackageOutput.value[id].orEmpty() + output).takeLast(12000))
                }
                check(result.exitCode == 0) { "Package command exited ${result.exitCode}. Review package output and retry." }
            }
            packageReview.value = packageReview.value - id
            run(listOf("/usr/bin/env", "DEBIAN_FRONTEND=noninteractive", "apt-get", "-y", "install", "--") + prerequisites)
            val installer = context.assets.open("fandroid-tools").bufferedReader().use { it.readText() }
            run(listOf("/bin/sh", "-c", installer, "fandroid-tools", "opencode"))
            val launcher = CliLauncher(UUID.randomUUID().toString(), "OpenCode", id, environment.startDirectory, listOf("/usr/local/bin/opencode"))
            withContext(Dispatchers.Main) { saveLauncher(launcher) }
        }
    }
    suspend fun launcherSpecification(launcher: CliLauncher): io.github.aqsama.fandroidterminal.runtime.ShellSpec {
        launcher.validated()
        val environment = environments.value.firstOrNull { it.id == launcher.environmentId } ?: error("Launcher environment unavailable. Edit the shortcut.")
        check(environment.ready && environment.id !in progress.value && "workspace-backup" !in progress.value) { "Environment is busy or unavailable" }
        return withContext(Dispatchers.IO) {
            val guest = GuestFiles(files.root(environment.id))
            check(java.nio.file.Files.isDirectory(guest.resolve(launcher.directory))) { "Launcher directory unavailable. Edit the shortcut." }
            check(java.nio.file.Files.isExecutable(guest.resolve(launcher.arguments.first()))) { "Tool unavailable. Install a compatible package in Packages, or repair its executable path." }
            LinuxLaunch.specification(File(context.applicationInfo.nativeLibraryDir), files.root(environment.id),
                File(files.instance(environment.id), "runtime-tmp"), environment.copy(startDirectory = launcher.directory), launcher.arguments)
        }
    }
    suspend fun specification(id: String, directory: String? = null): io.github.aqsama.fandroidterminal.runtime.ShellSpec {
        val environment = environments.value.first { it.id == id }
        check(environment.ready && id !in progress.value && "workspace-backup" !in progress.value) { "Environment is not ready" }
        return withContext(Dispatchers.IO) {
            val guest = GuestFiles(files.root(id))
            val preferredShell = if (environment.inheritShell) defaults.shell(environment.distribution) else null
            val preferredDirectory = if (environment.inheritDirectory) defaults.directory(environment.distribution) else null
            val resolved = environment.copy(
                shell = preferredShell?.takeIf { runCatching { java.nio.file.Files.isExecutable(guest.resolve(it)) }.getOrDefault(false) } ?: environment.shell,
                startDirectory = directory ?: preferredDirectory?.takeIf { runCatching { java.nio.file.Files.isDirectory(guest.resolve(it)) }.getOrDefault(false) } ?: environment.startDirectory)
            LinuxLaunch.specification(File(context.applicationInfo.nativeLibraryDir), files.root(id),
                File(files.instance(id), "runtime-tmp"), resolved)
        }
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
        if (environment.distribution != "alpine") {
        // Rootless dpkg cannot chown to apt's sandbox user. Run downloads as guest root.
        write("etc/apt/apt.conf.d/99fandroid", "APT::Sandbox::User \"root\";\n")
        write("usr/sbin/policy-rc.d", "#!/bin/sh\nexit 101\n")
        File(root, "usr/sbin/policy-rc.d").setExecutable(true, false)
        }
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
            listOf("/bin/sh", "-ec", (if (environment.distribution == "alpine")
                "test \"\$(apk --print-arch)\" = aarch64; test -x /sbin/apk; " else
                "test \"\$(dpkg --print-architecture)\" = arm64; test -x /usr/bin/apt-get; ") +
                "test -s /etc/ssl/certs/ca-certificates.crt; test -s /etc/resolv.conf; touch /root/.fandroid-probe /tmp/.fandroid-probe; rm /root/.fandroid-probe /tmp/.fandroid-probe"))
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
