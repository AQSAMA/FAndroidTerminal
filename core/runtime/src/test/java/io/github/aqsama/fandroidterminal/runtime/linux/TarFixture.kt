// Adapted from Horus 02d6fdd (MIT), Copyright (c) 2026 0x1379.
package io.github.aqsama.fandroidterminal.runtime.linux

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZOutputStream

/**
 * Minimal ustar fixture builder for extractor tests. It deliberately emits
 * attack entries (absolute names, traversal, symlink redirection) alongside
 * benign ones so every rejection path is exercised against real tar bytes.
 */
object TarFixture {
  sealed interface Entry {
    data class Dir(val name: String) : Entry
    data class File(
      val name: String,
      val content: ByteArray,
      val mode: Int = 0b110_100_100,
      val prefix: String = "",
    ) : Entry
    data class Symlink(val name: String, val target: String) : Entry
    data class Hardlink(val name: String, val target: String) : Entry
    data class Raw(val header: ByteArray) : Entry
    data class PaxPath(val overridePath: String, val wrapped: Entry) : Entry
    data class GnuLongName(val longName: String, val wrapped: Entry) : Entry
  }

  fun header(
    name: String,
    mode: Int = 0b110_100_100,
    size: Long = 0,
    typeFlag: Char = '0',
    linkname: String = "",
    prefix: String = "",
  ): ByteArray {
    val block = ByteArray(512)
    fun putOctal(offset: Int, length: Int, value: Long) {
      val text = value.toString(8).padStart(length - 1, '0') + "\u0000"
      for (i in 0 until length) block[offset + i] = if (i < text.length) text[i].code.toByte() else 0
    }
    fun putString(offset: Int, length: Int, value: String) {
      val bytes = value.toByteArray(Charsets.UTF_8)
      for (i in 0 until length) block[offset + i] = if (i < bytes.size) bytes[i] else 0
    }
    putString(0, 100, name)
    putOctal(100, 8, mode.toLong())
    putOctal(108, 8, 0)
    putOctal(116, 8, 0)
    putOctal(124, 12, size)
    putOctal(136, 12, 0)
    putString(148, 8, "        ")
    block[156] = typeFlag.code.toByte()
    putString(157, 100, linkname)
    putString(257, 6, "ustar\u0000")
    putString(263, 2, "00")
    putString(265, 32, "fixture")
    putString(297, 32, "fixture")
    putString(345, 155, prefix)
    // Compute the checksum the way tar does: treat the field as spaces.
    var checksum = 0L
    for (i in 0 until 512) checksum += if (i in 148 until 156) ' '.code.toLong() else block[i].toLong() and 0xff
    val checksumText = checksum.toString(8).padStart(6, '0') + "\u0000 "
    for (i in 0 until 8) block[148 + i] = checksumText[i].code.toByte()
    return block
  }

  private fun contentBlocks(content: ByteArray): ByteArray {
    val padded = ((content.size + 511) / 512) * 512
    val block = ByteArray(padded)
    content.copyInto(block)
    return block
  }

  fun build(entries: List<Entry>): ByteArray {
    val output = ByteArrayOutputStream()
    fun emit(entry: Entry) {
      when (entry) {
        is Entry.Dir -> output.write(header(entry.name, 0b111_101_101, 0, '5'))
        is Entry.File -> {
          output.write(header(entry.name, entry.mode, entry.content.size.toLong(), '0', prefix = entry.prefix))
          output.write(contentBlocks(entry.content))
        }
        is Entry.Symlink -> output.write(header(entry.name, 0b111_101_101, 0, '2', entry.target))
        is Entry.Hardlink -> output.write(header(entry.name, 0b110_100_100, 0, '1', entry.target))
        is Entry.Raw -> output.write(entry.header)
        is Entry.PaxPath -> {
          val record = paxPathRecord(entry.overridePath)
          output.write(header("PaxHeader", 0b110_100_100, record.size.toLong(), 'x'))
          output.write(contentBlocks(record))
          emit(entry.wrapped)
        }
        is Entry.GnuLongName -> {
          val longName = entry.longName.toByteArray(Charsets.UTF_8)
          output.write(header("././@LongLink", 0b110_100_100, longName.size.toLong(), 'L'))
          output.write(contentBlocks(longName))
          emit(entry.wrapped)
        }
      }
    }
    for (entry in entries) emit(entry)
    output.write(ByteArray(1024))
    return output.toByteArray()
  }

  fun buildGzip(entries: List<Entry>): ByteArray {
    val compressed = ByteArrayOutputStream()
    GZIPOutputStream(compressed).use { it.write(build(entries)) }
    return compressed.toByteArray()
  }

  fun buildXz(entries: List<Entry>): ByteArray {
    val compressed = ByteArrayOutputStream()
    XZOutputStream(compressed, LZMA2Options()).use { it.write(build(entries)) }
    return compressed.toByteArray()
  }

  fun build(entries: List<Entry>, format: ArchiveFormat): ByteArray = when (format) {
    ArchiveFormat.TAR_GZ -> buildGzip(entries)
    ArchiveFormat.TAR_XZ -> buildXz(entries)
  }

  private fun paxPathRecord(path: String): ByteArray {
    val pathBytes = path.toByteArray(Charsets.UTF_8)
    var length = pathBytes.size + 7 // " path=\n"
    while (true) {
      val candidate = "${length} path=${path}\n".toByteArray(Charsets.UTF_8)
      if (candidate.size == length) return candidate
      length = candidate.size
    }
  }
}
