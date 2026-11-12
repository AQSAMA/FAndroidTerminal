// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal.runtime.linux

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Only an unpublished staging tree is ever modified. Promotion follows a real guest probe. */
class DebianInstaller(
    private val files: EnvironmentFiles,
    private val cancelled: () -> Unit,
    private val progress: (String) -> Unit,
    private val acquire: ((File) -> Unit)? = null,
) {
    fun install(environment: LinuxEnvironment, configure: (File) -> Unit, probe: (File) -> Unit) {
        val instance = files.instance(environment.id)
        val staging = File(instance, "staging")
        val archive = File(instance, "download.part")
        val root = files.root(environment.id)
        check(!root.exists()) { "An installed rootfs already exists. It has been preserved." }
        try {
            cancelled()
            EnvironmentFiles.deleteTree(staging)
            check(instance.usableSpace > 800L * 1024 * 1024) { "Free at least 800 MiB of internal storage and retry" }
            check(staging.mkdirs())
            if (acquire == null) download(archive) else acquire.invoke(archive)
            progress("Extracting Debian…")
            SafeTarExtractor(checkCancelled = cancelled).extract(archive, staging, ArchiveFormat.TAR_XZ)
            cancelled()
            progress("Configuring DNS, certificates and storage…")
            configure(staging)
            progress("Checking the guest runtime…")
            probe(staging)
            cancelled()
            // Readiness is committed inside the promoted tree. A crash between rename and record
            // update is recoverable without reinstalling or touching the user's files.
            File(staging, ".fandroid-ready").writeText(DebianImage.id + "\n")
            check(staging.renameTo(root)) { "Could not promote installation; retry with more free storage" }
        } finally {
            archive.delete()
            EnvironmentFiles.deleteTree(staging)
        }
    }

    private fun download(target: File) {
        progress("Downloading Debian (17 MiB)…")
        val connection = URL(DebianImage.url).openConnection() as HttpURLConnection
        connection.connectTimeout = 20_000
        connection.readTimeout = 5_000
        connection.instanceFollowRedirects = true
        try {
            check(connection.responseCode == 200 && connection.url.protocol == "https") { "Image download failed; check your network and retry" }
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            connection.inputStream.use { input -> target.outputStream().use { output ->
                val buffer = ByteArray(65536)
                var reported = -1
                while (true) {
                    cancelled()
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    check(total <= DebianImage.bytes) { "Image exceeds its verified size" }
                    output.write(buffer, 0, count); digest.update(buffer, 0, count)
                    val percent = (total * 100 / DebianImage.bytes).toInt()
                    if (percent != reported) { reported = percent; progress("Downloading Debian: $percent%") }
                }
                output.fd.sync()
            } }
            check(total == DebianImage.bytes && digest.digest().joinToString("") { "%02x".format(it) } == DebianImage.sha256) {
                "Image verification failed. Nothing was installed. Retry the download."
            }
        } finally { connection.disconnect() }
    }
}
