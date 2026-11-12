// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal.runtime.linux

import java.io.*
import java.nio.file.*
import java.nio.file.attribute.PosixFilePermission
import java.util.UUID

/** Versioned streaming backup. Links are entries, never followed. No archive path is resolved through links. */
class EnvironmentBackup(private val files: EnvironmentFiles, private val cancelled: () -> Unit = {}, private val progress: (String) -> Unit = {}) {
    private val maxBytes = 16L * 1024 * 1024 * 1024
    fun export(environment: LinuxEnvironment, output: OutputStream) {
        val out = DataOutputStream(BufferedOutputStream(output))
        out.writeUTF("FANDROID-BACKUP-1")
        out.writeUTF(environment.name); out.writeUTF(environment.dns); out.writeBoolean(environment.compatibility)
        out.writeUTF(environment.startDirectory); out.writeUTF(environment.shell); out.writeUTF(environment.variables)
        val root = files.root(environment.id).toPath()
        var count = 0; var bytes = 0L
        fun entry(path: Path, depth: Int) {
            cancelled(); require(depth <= 128 && ++count <= 500_000) { "Backup exceeds entry or nesting limit" }
            val relative = root.relativize(path).toString()
            progress("Backing up $relative")
            val link = Files.isSymbolicLink(path)
            val directory = Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
            require(link || directory || Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) { "Remove special file $relative before backup" }
            out.writeByte(if (link) 3 else if (directory) 2 else 1); out.writeUTF(relative)
            out.writeInt(if (link) 0 else Files.getPosixFilePermissions(path).fold(0) { bits, p -> bits or (1 shl p.ordinal) })
            when {
                link -> out.writeUTF(Files.readSymbolicLink(path).toString())
                directory -> { Files.newDirectoryStream(path).use { it.forEach { child -> entry(child, depth + 1) } }; out.writeByte(4) }
                else -> {
                    val size = Files.size(path); bytes += size; require(bytes <= maxBytes) { "Backup exceeds 16 GiB" }; out.writeLong(size)
                    Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS).use { input -> transfer(input, out, size) }
                }
            }
        }
        Files.newDirectoryStream(root).use { it.forEach { p -> entry(p, 0) } }
        out.writeByte(0); out.flush()
    }
    fun restore(input: InputStream, name: String? = null): LinuxEnvironment {
        val stream = DataInputStream(BufferedInputStream(input))
        require(stream.readUTF() == "FANDROID-BACKUP-1") { "Select an FAndroidTerminal .fatbackup file (version 1)" }
        val originalName = stream.readUTF(); val dns = stream.readUTF(); val compatibility = stream.readBoolean()
        val environment = LinuxEnvironment(UUID.randomUUID().toString(), EnvironmentFiles.validateName(name ?: "$originalName restored"), "Ready", dns, compatibility,
            stream.readUTF(), stream.readUTF(), stream.readUTF()).validated()
        val instance = files.instance(environment.id)
        check(instance.mkdir())
        val stage = File(instance, "restore-staging").apply { check(mkdir()) }.toPath()
        var promoted = false
        try {
            var bytes = 0L; var count = 0
            val directoryModes = java.util.ArrayDeque<Pair<Path, Set<PosixFilePermission>>>()
            while (true) {
                cancelled(); val kind = stream.readUnsignedByte()
                if (kind == 0) { require(directoryModes.isEmpty()) { "Unclosed backup directory" }; break }
                if (kind == 4) {
                    require(directoryModes.isNotEmpty()) { "Unexpected directory end" }
                    val (p, mode) = directoryModes.removeLast(); Files.setPosixFilePermissions(p, mode); continue
                }
                require(kind in 1..3 && ++count <= 500_000) { "Malformed or oversized backup" }
                val relative = stream.readUTF()
                val parts = relative.split('/')
                require(relative.isNotEmpty() && !relative.startsWith('/') && parts.size <= 128 && parts.none { it.isEmpty() || it == "." || it == ".." || '\u0000' in it }) { "Unsafe backup path" }
                var parent = stage
                for (part in parts.dropLast(1)) { parent = parent.resolve(part); require(Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) { "Backup parent is missing or a symlink" } }
                require(parent == (directoryModes.lastOrNull()?.first ?: stage)) { "Backup directory order is invalid" }
                val target = parent.resolve(parts.last()); require(!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) { "Duplicate backup entry" }
                val bits = stream.readInt(); require(bits in 0..511) { "Invalid permissions" }
                val permissions = PosixFilePermission.entries.filter { bits and (1 shl it.ordinal) != 0 }.toSet()
                progress("Restoring $relative")
                when (kind) {
                    3 -> { val link = stream.readUTF(); require(link.isNotEmpty() && '\u0000' !in link); Files.createSymbolicLink(target, Paths.get(link)) }
                    2 -> { Files.createDirectory(target); directoryModes.addLast(target to permissions) }
                    1 -> { val size = stream.readLong(); require(size >= 0 && size <= maxBytes - bytes) { "Backup exceeds 16 GiB" }; bytes += size
                        Files.newOutputStream(target, StandardOpenOption.CREATE_NEW).use { transfer(stream, it, size) }; Files.setPosixFilePermissions(target, permissions) }
                }
            }
            require(stream.read() == -1) { "Unexpected trailing backup data" }
            require(Files.isRegularFile(stage.resolve(".fandroid-ready"), LinkOption.NOFOLLOW_LINKS)) { "Backup is not a complete installed environment" }
            cancelled(); Files.move(stage, files.root(environment.id).toPath(), StandardCopyOption.ATOMIC_MOVE)
            files.save(environment); promoted = true
            return environment
        } finally { if (!promoted) EnvironmentFiles.deleteTree(instance) }
    }
    private fun transfer(input: InputStream, output: OutputStream, size: Long) {
        var remaining = size; val buffer = ByteArray(65536)
        while (remaining > 0) { cancelled(); val n = input.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt()); if (n < 0) throw EOFException("Backup file is truncated"); output.write(buffer, 0, n); remaining -= n }
    }
}
