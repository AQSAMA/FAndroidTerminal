// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal.runtime.linux

import io.github.aqsama.fandroidterminal.runtime.ShellSpec
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

data class GuestPackage(val name: String, val installed: String?, val candidate: String?, val architecture: String, val repository: String, val updateAvailable: Boolean = false)

/** Parsers consume machine command output, never terminal cells. Versions remain distribution-local. */
object PackageMetadata {
    fun names(text: String): List<String> = text.split(Regex("[\\s,]+")).filter { it.isNotBlank() }.distinct().also {
        require(it.size <= 100) { "Select at most 100 packages" }
        require(it.all { name -> name.length <= 180 && name.matches(Regex("[a-z0-9][a-z0-9+._-]*(:[a-z0-9_-]+)?")) }) { "Use package names only, separated by spaces or commas" }
    }
    fun importNames(text: String): List<String> = names(text.lines().joinToString(" ") { line ->
        if ('\t' in line) line.substringBefore('\t') else line
    })
    fun apt(available: String, installed: String): List<GuestPackage> {
        fun rows(text: String) = text.lineSequence().mapNotNull { line ->
            val fields = line.trim().split(Regex("\\s+"))
            if (fields.size < 3 || '/' !in fields[0]) null else {
                val name = fields[0].substringBefore('/')
                if (!name.matches(Regex("[a-z0-9][a-z0-9+._-]*"))) null
                else GuestPackage(name, null, fields[1], fields[2], fields[0].substringAfter('/'), line.contains("[upgradable from:"))
            }
        }.toList()
        val local = rows(installed).associateBy { it.name to it.architecture }
        val repository = rows(available)
        val remoteKeys = repository.map { it.name to it.architecture }.toSet()
        return (repository.map { it.copy(installed = local[it.name to it.architecture]?.candidate) } +
            local.values.filter { item -> (item.name to item.architecture) !in remoteKeys }
                .map { it.copy(installed = it.candidate, candidate = null) }).distinctBy { it.name to it.architecture }
    }
    fun apk(available: String, installed: String, architecture: String, updates: String = ""): List<GuestPackage> {
        fun rows(text: String) = text.lineSequence().mapNotNull { line ->
            Regex("^([a-z0-9][a-z0-9+._-]*)-([0-9][^ ]*)").find(line)?.let { it.groupValues[1] to it.groupValues[2] }
        }.toMap()
        val local = rows(installed); val remote = rows(available)
        val upgradeNames = Regex("(?m)^([a-z0-9][a-z0-9+._-]*)-[0-9][^ ]*\\s+<").findAll(updates).map { it.groupValues[1] }.toSet()
        return (local.keys + remote.keys).map { GuestPackage(it, local[it], remote[it], architecture.trim(), "Configured apk repositories", it in upgradeNames) }
    }
}

/** Bounded structured capture. Call on IO; cancellation kills the owning PRoot and its traced children. */
object GuestCommand {
    data class Result(val exitCode: Int, val output: String)
    fun run(spec: ShellSpec, cancelled: () -> Unit, progress: (String) -> Unit = {}): Result {
        val builder = ProcessBuilder(spec.arguments).directory(java.io.File(spec.cwd)).redirectErrorStream(true)
        builder.environment().clear(); builder.environment().putAll(spec.environment)
        val process = builder.start()
        process.outputStream.close()
        val bytes = ByteArrayOutputStream()
        var readFailure: Throwable? = null
        val reader = thread(name = "guest-command-output", isDaemon = true) {
            try {
                process.inputStream.use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = input.read(buffer); if (n < 0) break
                        synchronized(bytes) {
                            check(bytes.size() + n <= 16 * 1024 * 1024) { "Package output exceeds 16 MiB; narrow the query" }
                            bytes.write(buffer, 0, n)
                            progress(String(buffer, 0, n, Charsets.UTF_8).takeLast(6000))
                        }
                    }
                }
            } catch (error: Throwable) { readFailure = error; process.destroy() }
        }
        try {
            val deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(30)
            while (!process.waitFor(150, TimeUnit.MILLISECONDS)) {
                cancelled(); check(System.nanoTime() < deadline) { "Package operation timed out" }

            }
            reader.join(2000)
            check(!reader.isAlive) { "Package output did not close" }
            readFailure?.let { throw IllegalStateException(it.message, it) }
            cancelled()
            return Result(process.exitValue(), bytes.toString("UTF-8"))
        } finally {
            process.destroy()
            if (!process.waitFor(500, TimeUnit.MILLISECONDS)) process.destroyForcibly()
            process.inputStream.close()
            reader.join(1000)
        }
    }
}
