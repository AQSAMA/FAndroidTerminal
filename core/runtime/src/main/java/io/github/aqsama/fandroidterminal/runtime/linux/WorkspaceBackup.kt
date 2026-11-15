// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal.runtime.linux

import java.io.*
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BackupPasswordRequired : IllegalArgumentException("This backup requires its password")

/** Selected portable settings plus complete environments. Restore always duplicates, never replaces. */
class WorkspaceBackup(private val files: EnvironmentFiles, private val cache: File, private val cancelled: () -> Unit = {}, private val progress: (String) -> Unit = {}) {
    data class Restored(val settings: ByteArray, val identities: Map<String, String>)
    private val maximum = 32L * 1024 * 1024 * 1024
    private fun temp() = File.createTempFile("backup-bundle-", ".partial", cache)
    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        require(password.size in 8..1024) { "Use a backup password of 8–1024 characters" }
        val spec = PBEKeySpec(password, salt, 210_000, 256)
        val bytes = try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
        return try { SecretKeySpec(bytes, "AES") } finally { bytes.fill(0) }
    }
    private fun crypt(mode: Int, key: SecretKeySpec, nonce: ByteArray, sequence: Long, size: Int, bytes: ByteArray): ByteArray {
        val iv = nonce.copyOf()
        val counter = java.nio.ByteBuffer.allocate(8).putLong(sequence).array()
        for (i in 0..7) iv[4 + i] = (iv[4 + i].toInt() xor counter[i].toInt()).toByte()
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(mode, key, GCMParameterSpec(128, iv))
            updateAAD(java.nio.ByteBuffer.allocate(12).putLong(sequence).putInt(size).array())
            doFinal(bytes)
        }
    }
    /** Independent 64 KiB authenticated records bound provider buffering; final empty record prevents truncation. */
    private inner class EncryptedOutput(output: OutputStream, val key: SecretKeySpec, val nonce: ByteArray) : OutputStream() {
        val out = DataOutputStream(output)
        val buffer = ByteArray(65536); var used = 0; var sequence = 0L
        fun record(final: Boolean = false) {
            if (used == 0 && !final) return
            cancelled()
            val encrypted = crypt(Cipher.ENCRYPT_MODE, key, nonce, sequence++, used, buffer.copyOf(used))
            out.writeInt(used); out.write(encrypted); used = 0
        }
        override fun write(value: Int) { buffer[used++] = value.toByte(); if (used == buffer.size) record() }
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            var position = offset; var remaining = length
            while (remaining > 0) {
                val count = minOf(remaining, buffer.size - used)
                bytes.copyInto(buffer, used, position, position + count); used += count; position += count; remaining -= count
                if (used == buffer.size) record()
            }
        }
        override fun flush() { record(); out.flush() }
        override fun close() { record(); record(true); buffer.fill(0); out.close() }
    }
    private inner class EncryptedInput(input: InputStream, val key: SecretKeySpec, val nonce: ByteArray) : InputStream() {
        val source = DataInputStream(input)
        var current = ByteArrayInputStream(byteArrayOf()); var finished = false; var sequence = 0L
        fun next(): Boolean {
            if (finished) return false
            cancelled()
            val size = source.readInt(); require(size in 0..65536) { "Invalid encrypted record size" }
            val encrypted = ByteArray(size + 16).also { source.readFully(it) }
            val plain = crypt(Cipher.DECRYPT_MODE, key, nonce, sequence++, size, encrypted)
            if (size == 0) {
                require(source.read() == -1) { "Trailing encrypted backup data" }; finished = true; return false
            }
            current = ByteArrayInputStream(plain); return true
        }
        override fun read(): Int { while (true) { val value = current.read(); if (value >= 0) return value; if (!next()) return -1 } }
        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            while (true) { val count = current.read(bytes, offset, length); if (count >= 0) return count; if (!next()) return -1 }
        }
        override fun close() { source.close() }
    }
    private fun copy(input: InputStream, output: OutputStream, length: Long): ByteArray {
        require(length in 0..maximum)
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(65536); var remaining = length
        while (remaining > 0) {
            cancelled(); val n = input.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
            if (n < 0) throw EOFException("Truncated workspace backup")
            output.write(buffer, 0, n); digest.update(buffer, 0, n); remaining -= n
        }
        return digest.digest()
    }
    fun export(settings: ByteArray, environments: List<LinuxEnvironment>, output: OutputStream, password: CharArray? = null) {
        require(settings.size <= 1024 * 1024 && environments.size <= 20)
        val header = DataOutputStream(output)
        header.writeUTF("FANDROID-WORKSPACE-1"); header.writeBoolean(password != null)
        val payload: OutputStream = if (password == null) output else {
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
            header.write(salt); header.write(nonce); header.flush()
            EncryptedOutput(output, key(password, salt), nonce)
        }
        DataOutputStream(BufferedOutputStream(payload)).use { out ->
            out.writeInt(settings.size); out.write(settings); out.write(MessageDigest.getInstance("SHA-256").digest(settings))
            out.writeInt(environments.size)
            var total = 0L
            environments.forEach { environment ->
                cancelled(); progress("Backing up ${environment.name}")
                val archive = temp()
                try {
                    archive.outputStream().use { EnvironmentBackup(files, cancelled, progress).export(environment, it) }
                    total += archive.length(); require(total <= maximum) { "Workspace backup exceeds 32 GiB" }
                    out.writeUTF(environment.id); out.writeLong(archive.length())
                    val hash = archive.inputStream().use { copy(it, out, archive.length()) }; out.write(hash)
                } finally { archive.delete() }
            }
        }
    }
    data class Entry(val originalId: String, val name: String, val distribution: String, val release: String)
    inner class PreparedRestore internal constructor(val settings: ByteArray, val environments: List<Entry>, private val archives: List<Pair<String, File>>) : Closeable {
        fun restore(selected: Set<String>, checkCancelled: () -> Unit = {}, report: (String) -> Unit = {}): Restored {
            require(selected.all { id -> environments.any { it.originalId == id } })
            require(files.load().size + selected.size <= 20) { "Environment limit reached (20)" }
            val identities = mutableMapOf<String, String>()
            try {
                archives.filter { it.first in selected }.forEach { (old, archive) ->
                    checkCancelled()
                    val environment = archive.inputStream().use { EnvironmentBackup(files, checkCancelled, report).restore(it) }
                    identities[old] = environment.id
                }
                return Restored(settings, identities)
            } catch (failure: Exception) {
                identities.values.forEach { id -> runCatching { files.deleteInstance(id) } }
                throw failure
            }
        }
        override fun close() { archives.forEach { it.second.delete() } }
    }
    fun restore(input: InputStream, password: CharArray? = null, restoreEnvironments: Boolean = true, validateSettings: (ByteArray) -> Unit = {}): Restored =
        prepare(input, password, validateSettings).use { plan -> plan.restore(if (restoreEnvironments) plan.environments.map { it.originalId }.toSet() else emptySet(), cancelled, progress) }

    fun prepare(input: InputStream, password: CharArray? = null, validateSettings: (ByteArray) -> Unit = {}): PreparedRestore {
        val header = DataInputStream(input)
        require(header.readUTF() == "FANDROID-WORKSPACE-1") { "Select a workspace backup; legacy environment backups use Environments → Restore" }
        val encrypted = header.readBoolean()
        require(encrypted || password == null) { "This backup is unencrypted. Disable password encryption to import it explicitly." }
        val payload: InputStream = if (!encrypted) input else {
            if (password == null) throw BackupPasswordRequired()
            val salt = ByteArray(16).also { header.readFully(it) }; val nonce = ByteArray(12).also { header.readFully(it) }
            EncryptedInput(input, key(password, salt), nonce)
        }
        // Authenticate the entire encrypted payload BEFORE promoting any environment or settings.
        val stage = temp()
        val archives = mutableListOf<Pair<String, File>>()
        var prepared = false
        try {
            payload.use { source -> stage.outputStream().use { out ->
                val buffer = ByteArray(65536); var total = 0L
                while (true) {
                    cancelled(); val n = source.read(buffer); if (n < 0) break
                    total += n; require(total <= maximum + 2 * 1024 * 1024) { "Workspace backup exceeds limit" }; out.write(buffer, 0, n)
                }
            } }
            DataInputStream(BufferedInputStream(stage.inputStream())).use { data ->
                val size = data.readInt(); require(size in 0..1024 * 1024)
                val settings = ByteArray(size).also { data.readFully(it) }
                val hash = ByteArray(32).also { data.readFully(it) }
                require(MessageDigest.isEqual(hash, MessageDigest.getInstance("SHA-256").digest(settings))) { "Settings integrity check failed" }
                validateSettings(settings)
                val count = data.readInt(); require(count in 0..20)
                repeat(count) {
                    val original = data.readUTF(); require(java.util.UUID.fromString(original).toString() == original && archives.none { it.first == original })
                    val length = data.readLong()
                    val archive = temp(); archives += original to archive
                    val actual = archive.outputStream().use { copy(data, it, length) }
                    val expected = ByteArray(32).also { data.readFully(it) }
                    require(MessageDigest.isEqual(actual, expected)) { "Environment integrity check failed" }
                }
                require(data.read() == -1) { "Unexpected workspace backup data" }
                val entries = archives.map { (id, archive) ->
                    DataInputStream(archive.inputStream()).use { metadata ->
                        val version = metadata.readUTF()
                        require(version in listOf("FANDROID-BACKUP-1", "FANDROID-BACKUP-2", "FANDROID-BACKUP-3"))
                        val distribution = if (version.endsWith("-1")) "debian" else metadata.readUTF()
                        val release = if (version.endsWith("-1")) "13.7" else metadata.readUTF()
                        Entry(id, EnvironmentFiles.validateName(metadata.readUTF()), LinuxImages.get(distribution).distribution, release)
                    }
                }
                prepared = true
                return PreparedRestore(settings, entries, archives)
            }
        } finally {
            stage.delete()
            if (!prepared) archives.forEach { it.second.delete() }
        }
    }
}
