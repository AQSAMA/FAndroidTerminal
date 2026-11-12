// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal.runtime.linux

import io.github.aqsama.fandroidterminal.runtime.ShellSpec
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.Properties
import java.util.UUID

/** Instances own their entire mutable rootfs, including /root. Sessions only borrow it. */
data class LinuxEnvironment(
    val id: String,
    val name: String,
    val state: String = "Not installed",
    val dns: String = "1.1.1.1",
    val compatibility: Boolean = true,
) {
    val ready: Boolean get() = state == "Ready"
}

class EnvironmentFiles(val directory: File) {
    init { check(directory.isDirectory || directory.mkdirs()) }
    fun instance(id: String): File {
        require(UUID.fromString(id).toString() == id)
        return File(directory, id)
    }
    fun root(id: String) = File(instance(id), "rootfs")
    fun save(environment: LinuxEnvironment) {
        val dir = instance(environment.id).apply { check(isDirectory || mkdirs()) }
        val properties = Properties().apply {
            setProperty("name", environment.name); setProperty("state", environment.state)
            setProperty("dns", environment.dns); setProperty("compatibility", environment.compatibility.toString())
        }
        val temporary = File(dir, "record.tmp")
        temporary.outputStream().use { properties.store(it, "FAndroidTerminal environment"); it.fd.sync() }
        check(temporary.renameTo(File(dir, "record.properties"))) { "Cannot save environment" }
    }
    fun load(): List<LinuxEnvironment> = directory.listFiles().orEmpty().mapNotNull { dir ->
        if (runCatching { instance(dir.name) }.isFailure) return@mapNotNull null
        val record = File(dir, "record.properties")
        if (!record.isFile) return@mapNotNull null
        val p = Properties().apply { record.inputStream().use { load(it) } }
        val state = p.getProperty("state", "Interrupted")
        LinuxEnvironment(dir.name, p.getProperty("name", "Debian"),
            if (state == "Installing") "Interrupted — retry installation" else state,
            p.getProperty("dns", "1.1.1.1"), p.getProperty("compatibility", "true").toBoolean())
    }.sortedBy { it.name }

    /** Keep the durable tombstone until every potentially large payload is gone. */
    fun deleteInstance(id: String, deletePayload: (File) -> Unit = ::deleteTree) {
        val dir = instance(id)
        dir.listFiles()?.filter { it.name != "record.properties" }?.forEach(deletePayload)
            ?: error("Cannot read environment for deletion")
        Files.deleteIfExists(File(dir, "record.properties").toPath())
        Files.deleteIfExists(dir.toPath())
    }

    companion object {
        fun validateName(name: String): String = name.trim().also {
            require(it.isNotEmpty() && it.length <= 60 && it.none { c -> c.isISOControl() }) { "Use a name of 1–60 characters" }
        }
        fun validateDns(dns: String): String = dns.trim().also {
            require(it.split('.').size == 4 && it.split('.').all { part -> part.toIntOrNull() in 0..255 }) { "Enter an IPv4 DNS address" }
        }
        /** Never traverse guest-created symlinks while deleting app-owned trees. */
        fun deleteTree(file: File) {
            if (Files.isDirectory(file.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                val children = file.listFiles() ?: error("Cannot read directory for deletion")
                children.forEach(::deleteTree)
            }
            Files.deleteIfExists(file.toPath())
        }
    }
}

object DebianImage {
    const val id = "debian-13.7-20261005-arm64"
    const val url = "https://github.com/scarif-labs/horus/releases/download/rootfs-debian-13.7-20261005/debian-trixie-slim-13.7-arm64.tar.xz"
    const val sha256 = "c4a40504d8eddd7d47125d129296352d3758b1d0ce958bd517b44631aee74eb5"
    const val bytes = 17_386_584L
}

/** Original launcher informed by Horus/Rikkahub/Kelivo. No Termux prefix or shell wrapper. */
object LinuxLaunch {
    fun specification(nativeDir: File, root: File, temporary: File, environment: LinuxEnvironment,
                      command: List<String> = listOf("/bin/bash", "--login")): ShellSpec {
        val proot = File(nativeDir, "libproot.so")
        val loader = File(nativeDir, "libproot_loader.so")
        check(proot.canExecute() && loader.canExecute()) { "Packaged ARM64 runtime is unavailable. Install the ARM64 APK." }
        check(temporary.isDirectory || temporary.mkdirs())
        val argv = mutableListOf(proot.path, "--root-id", "--link2symlink", "--kill-on-exit",
            "-r", root.path, "-w", "/root", "-b", "/dev", "-b", "/proc", "-b", "/sys",
            "-b", "/dev/urandom:/dev/random", "/usr/bin/env", "-i",
            "HOME=/root", "USER=root", "LOGNAME=root", "SHELL=/bin/bash",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "LANG=C.UTF-8", "LC_ALL=C.UTF-8", "TERM=xterm-256color", "COLORTERM=truecolor", "TMPDIR=/tmp")
        argv += command
        val host = mutableMapOf("PROOT_LOADER" to loader.path, "PROOT_TMP_DIR" to temporary.path,
            "TMPDIR" to temporary.path, "PATH" to "/system/bin", "ANDROID_ROOT" to "/system",
            "ANDROID_DATA" to "/data")
        if (environment.compatibility) host["PROOT_NO_SECCOMP"] = "1"
        return ShellSpec(proot.path, argv, root.parentFile!!.path, host)
    }
}
