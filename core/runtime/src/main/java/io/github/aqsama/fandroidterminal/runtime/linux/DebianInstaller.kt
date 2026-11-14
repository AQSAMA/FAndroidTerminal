// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal.runtime.linux

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Only an unpublished staging tree is ever modified. Promotion follows a real guest probe. */
class LinuxInstaller(
    private val files: EnvironmentFiles,
    private val cancelled: () -> Unit,
    private val progress: (String) -> Unit,
    private val acquire: ((File) -> Unit)? = null,
) {
    fun install(environment: LinuxEnvironment, configure: (File) -> Unit, probe: (File) -> Unit) {
        val image = LinuxImages.get(environment.distribution)
        require(environment.release == image.version) { "This installer requires ${image.name} ${image.version}" }
        val instance = files.instance(environment.id)
        val staging = File(instance, "staging")
        val archive = File(instance, "download.part")
        val root = files.root(environment.id)
        check(!root.exists()) { "An installed rootfs already exists. It has been preserved." }
        try {
            cancelled()
            EnvironmentFiles.deleteTree(staging)
            check(instance.usableSpace > image.minimumFreeMiB.toLong() * 1024 * 1024) { "Free at least ${image.minimumFreeMiB} MiB of internal storage and retry" }
            check(staging.mkdirs())
            if (acquire == null) download(archive, image) else acquire.invoke(archive)
            progress("Extracting ${image.name}…")
            SafeTarExtractor(checkCancelled = cancelled).extract(archive, staging, image.format)
            cancelled()
            progress("Configuring DNS, certificates and storage…")
            configure(staging)
            progress("Checking the guest runtime…")
            probe(staging)
            cancelled()
            // Readiness is committed inside the promoted tree. A crash between rename and record
            // update is recoverable without reinstalling or touching the user's files.
            File(staging, ".fandroid-ready").writeText(image.imageId + "\n")
            check(staging.renameTo(root)) { "Could not promote installation; retry with more free storage" }
        } finally {
            archive.delete()
            EnvironmentFiles.deleteTree(staging)
        }
    }

    private fun download(target: File, image: LinuxImage) {
        progress("Downloading ${image.name} (${(image.bytes + 1048575) / 1048576} MiB)…")
        val connection = URL(image.url).openConnection() as HttpURLConnection
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
                    check(total <= image.bytes) { "Image exceeds its verified size" }
                    output.write(buffer, 0, count); digest.update(buffer, 0, count)
                    val percent = (total * 100 / image.bytes).toInt()
                    if (percent != reported) { reported = percent; progress("Downloading ${image.name}: $percent%") }
                }
                output.fd.sync()
            } }
            check(total == image.bytes && digest.digest().joinToString("") { "%02x".format(it) } == image.sha256) {
                "Image verification failed. Nothing was installed. Retry the download."
            }
        } finally { connection.disconnect() }
    }
}

/** Source compatibility for existing callers/tests. New installations use the distribution catalog. */
typealias DebianInstaller = LinuxInstaller
