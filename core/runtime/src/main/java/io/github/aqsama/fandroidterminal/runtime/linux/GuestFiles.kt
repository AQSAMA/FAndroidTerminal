// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal.runtime.linux

import java.io.*
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes

/** Guest absolute links are rooted in rootfs, never resolved against Android /. */
class GuestFiles(private val root: File, private val cancelled: () -> Unit = {}) {
    data class Entry(val name: String, val directory: Boolean, val link: Boolean, val bytes: Long, val modified: Long)
    fun resolve(path: String, followFinal: Boolean = true): Path {
        require(path.startsWith('/') && path.length <= 4096 && '\u0000' !in path) { "Use an absolute guest path" }
        val base = root.toPath().toAbsolutePath().normalize()
        check(!Files.isSymbolicLink(base)) { "Environment root cannot be a link" }
        val pending = java.util.ArrayDeque(path.split('/').filter { it.isNotEmpty() })
        var current = base
        var links = 0
        while (pending.isNotEmpty()) {
            cancelled()
            val part = pending.removeFirst()
            if (part == ".") continue
            if (part == "..") { require(current != base) { "Path escapes guest root" }; current = current.parent; continue }
            current = current.resolve(part)
            if (Files.isSymbolicLink(current) && (followFinal || pending.isNotEmpty())) {
                check(++links <= 40) { "Symlink loop" }
                val link = Files.readSymbolicLink(current)
                current = if (link.isAbsolute) base else current.parent
                link.toString().split('/').filter { it.isNotEmpty() }.asReversed().forEach { pending.addFirst(it) }
            }
        }
        require(current.startsWith(base)) { "Path escapes guest root" }
        return current
    }
    fun list(path: String): List<Entry> {
        val entries = mutableListOf<Entry>()
        Files.newDirectoryStream(resolve(path)).use { stream ->
            for (p in stream) {
                cancelled(); require(entries.size < 5000) { "Directory exceeds 5,000 entries. Use the terminal to narrow it." }
                val a = Files.readAttributes(p, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
                entries += Entry(p.fileName.toString(), a.isDirectory, a.isSymbolicLink, a.size(), a.lastModifiedTime().toMillis())
            }
        }
        return entries.sortedWith(compareBy<Entry> { !it.directory }.thenBy { it.name.lowercase() })
    }
    fun read(path: String): String {
        val p = resolve(path)
        require(Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) { "Select a regular text file" }
        val bytes = Files.newInputStream(p, LinkOption.NOFOLLOW_LINKS).use { it.readBytesBounded(1024 * 1024) }
        require(0.toByte() !in bytes) { "Binary file; export it instead" }
        return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    }
    fun write(path: String, input: InputStream, overwrite: Boolean = false, limit: Long = 4L * 1024 * 1024 * 1024) {
        val target = resolve(path)
        require(target != root.toPath().toAbsolutePath().normalize())
        require(overwrite || !Files.exists(target, LinkOption.NOFOLLOW_LINKS)) { "Destination exists; confirm overwrite" }
        val temp = Files.createTempFile(target.parent, ".fandroid-", ".partial")
        try {
            Files.newOutputStream(temp).use { output -> copy(input, output, limit) }
            if (Files.exists(target)) Files.setPosixFilePermissions(temp, Files.getPosixFilePermissions(target))
            cancelled()
            if (overwrite) Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            else Files.move(temp, target)
        } finally { Files.deleteIfExists(temp) }
    }
    fun mkdir(path: String) { Files.createDirectory(resolve(path, false)) }
    fun delete(path: String) {
        val p = resolve(path, false); require(p != root.toPath().toAbsolutePath().normalize()) { "Cannot delete guest root" }
        walkDelete(p)
    }
    private fun walkDelete(p: Path) {
        cancelled()
        if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) Files.newDirectoryStream(p).use { it.forEach(::walkDelete) }
        Files.delete(p)
    }
    fun transfer(source: String, destination: String, move: Boolean) {
        val src = resolve(source, false); val dst = resolve(destination, false)
        require(src != root.toPath().toAbsolutePath().normalize() && !dst.startsWith(src)) { "Choose a destination outside the source" }
        require(!Files.exists(dst, LinkOption.NOFOLLOW_LINKS)) { "Destination exists. Choose another name." }
        if (move) { cancelled(); Files.move(src, dst); return }
        val temp = Files.createTempDirectory(dst.parent, ".fandroid-copy-")
        try { copyTree(src, temp.resolve("item")); cancelled(); Files.move(temp.resolve("item"), dst) }
        finally { EnvironmentFiles.deleteTree(temp.toFile()) }
    }
    private fun copyTree(src: Path, dst: Path, depth: Int = 0) {
        cancelled(); require(depth < 128) { "Directory nesting exceeds 128" }
        when {
            Files.isSymbolicLink(src) -> Files.createSymbolicLink(dst, Files.readSymbolicLink(src))
            Files.isDirectory(src, LinkOption.NOFOLLOW_LINKS) -> {
                Files.createDirectory(dst)
                Files.newDirectoryStream(src).use { stream -> stream.forEach { copyTree(it, dst.resolve(it.fileName), depth + 1) } }
                Files.setPosixFilePermissions(dst, Files.getPosixFilePermissions(src))
            }
            Files.isRegularFile(src, LinkOption.NOFOLLOW_LINKS) -> {
                Files.newInputStream(src, LinkOption.NOFOLLOW_LINKS).use { input -> Files.newOutputStream(dst).use { copy(input, it) } }
                Files.setPosixFilePermissions(dst, Files.getPosixFilePermissions(src))
            }
            else -> error("Special files cannot be copied")
        }
    }
    fun usage(): Long {
        var bytes = 0L
        Files.walkFileTree(root.toPath(), object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult { cancelled(); bytes += attrs.size(); return FileVisitResult.CONTINUE }
        })
        return bytes
    }
    fun export(path: String, output: OutputStream) {
        val p = resolve(path); require(Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) { "Export selects a regular file" }
        Files.newInputStream(p, LinkOption.NOFOLLOW_LINKS).use { copy(it, output) }
    }
    private fun copy(input: InputStream, output: OutputStream, limit: Long = Long.MAX_VALUE) {
        val buffer = ByteArray(65536); var total = 0L
        while (true) { cancelled(); val n = input.read(buffer); if (n < 0) break; total += n; require(total <= limit) { "File exceeds size limit" }; output.write(buffer, 0, n) }
    }
}
internal fun InputStream.readBytesBounded(limit: Int): ByteArray {
    val result = ByteArrayOutputStream(); val buffer = ByteArray(8192)
    while (true) { val n = read(buffer); if (n < 0) break; require(result.size() + n <= limit) { "File exceeds ${limit / 1024} KiB limit" }; result.write(buffer, 0, n) }
    return result.toByteArray()
}
