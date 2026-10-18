// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal.runtime

/** A byte transport, independent of VT emulation and Compose. */
interface SessionTransport : AutoCloseable {
    suspend fun read(buffer: ByteArray): Int
    suspend fun write(bytes: ByteArray)
    fun resize(rows: Int, columns: Int, widthPixels: Int = 0, heightPixels: Int = 0)
    suspend fun awaitExit(): Int
    override fun close()
}

data class ShellSpec(val executable: String, val arguments: List<String>, val cwd: String, val environment: Map<String, String>)
