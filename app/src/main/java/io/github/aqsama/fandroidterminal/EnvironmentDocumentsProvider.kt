// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import android.content.*
import android.os.IBinder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import io.github.aqsama.fandroidterminal.runtime.linux.GuestFiles
import io.github.aqsama.fandroidterminal.sessions.TerminalService
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files

/** Read-only SAF bridge. All guest access uses the same session admission lock as Files.
 * Open files are bounded, unlinked snapshots so later sessions cannot race a granted descriptor.
 * Contract study: Android DocumentsProvider documentation and TermuxDocumentsProvider; original code.
 */
class EnvironmentDocumentsProvider : DocumentsProvider() {
    private val requests = Mutex()
    override fun onCreate() = true
    private val columns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_FLAGS, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED)
    private fun parts(id: String): Pair<String, String> {
        val environment = id.substringBefore(':')
        val path = id.substringAfter(':', "")
        require(environment.matches(Regex("[a-zA-Z0-9-]+")) && path.startsWith('/') && path.split('/').none { it == "." || it == ".." })
        return environment to path
    }
    private fun <T> access(id: String, signal: CancellationSignal? = null, block: (GuestFiles, String) -> T): T = try {
        val (environment, path) = parts(id)
        runBlocking {
            requests.withLock { bound { service ->
                val result = CompletableDeferred<T>()
                withContext(Dispatchers.Main) {
                    check(service.environments.environments.value.any { it.id == environment && it.ready })
                    var value: T? = null
                    service.environments.fileOperation(environment, service.sessions, "Reading external document…", { error ->
                        if (error != null) { (value as? java.io.Closeable)?.close(); result.completeExceptionally(error) }
                        else { @Suppress("UNCHECKED_CAST") result.complete(value as T) }
                    }) { guest ->
                        signal?.throwIfCanceled()
                        var prefix = ""
                        path.split('/').filter { it.isNotEmpty() }.forEach {
                            prefix += "/$it"
                            check(!Files.isSymbolicLink(guest.resolve(prefix, false))) { "Browse links inside FAndroidTerminal" }
                        }
                        value = block(guest, path)
                    }
                }
                result.await()
            } }
        }
    } catch (error: Exception) { throw FileNotFoundException(error.message).apply { initCause(error) } }

    private suspend fun <T> bound(block: suspend (TerminalService) -> T): T {
        val connected = CompletableDeferred<TerminalService>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) { connected.complete((binder as TerminalService.LocalBinder).service) }
            override fun onServiceDisconnected(name: ComponentName) { connected.completeExceptionally(FileNotFoundException("Service disconnected")) }
        }
        check(context!!.bindService(Intent(context, TerminalService::class.java), connection, Context.BIND_AUTO_CREATE))
        try {
            val service = withTimeout(15000) { connected.await() }
            withTimeout(15000) { withContext(Dispatchers.Main) { while (!service.environments.isLoaded) delay(25) } }
            return block(service)
        } finally { context!!.unbindService(connection) }
    }
    override fun queryRoots(projection: Array<out String>?): MatrixCursor = runBlocking {
        val columns = projection ?: arrayOf(Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE, Root.COLUMN_FLAGS, Root.COLUMN_ICON)
        val result = MatrixCursor(columns)
        bound { service -> service.environments.environments.value.filter { it.ready }.forEach { env ->
            val values = mapOf<String, Any?>(Root.COLUMN_ROOT_ID to env.id, Root.COLUMN_DOCUMENT_ID to "${env.id}:/", Root.COLUMN_TITLE to env.name, Root.COLUMN_FLAGS to Root.FLAG_SUPPORTS_IS_CHILD, Root.COLUMN_ICON to R.drawable.ic_files)
            result.addRow(columns.map { values[it] }.toTypedArray())
        } }
        result
    }
    private fun include(cursor: MatrixCursor, id: String, name: String, directory: Boolean, size: Long, modified: Long) {
        val mime = if (directory) Document.MIME_TYPE_DIR else MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "application/octet-stream"
        val values = mapOf<String, Any?>(Document.COLUMN_DOCUMENT_ID to id, Document.COLUMN_DISPLAY_NAME to name, Document.COLUMN_MIME_TYPE to mime, Document.COLUMN_FLAGS to 0, Document.COLUMN_SIZE to size, Document.COLUMN_LAST_MODIFIED to modified)
        cursor.addRow(cursor.columnNames.map { values[it] }.toTypedArray())
    }
    override fun queryDocument(documentId: String, projection: Array<out String>?) = access(documentId) { guest, path ->
        MatrixCursor(projection ?: columns).also { cursor ->
            val file = guest.resolve(path).toFile()
            check(file.isDirectory || file.isFile)
            include(cursor, documentId, if (path == "/") "/" else file.name, file.isDirectory, file.length(), file.lastModified())
        }
    }
    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?) = access(parentDocumentId) { guest, path ->
        MatrixCursor(projection ?: columns).also { cursor ->
            guest.list(path).filter { !it.link }.forEach { entry ->
                include(cursor, parentDocumentId.trimEnd('/') + "/" + entry.name, entry.name, entry.directory, entry.bytes, entry.modified)
            }
        }
    }
    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        val (parent, path) = parts(parentDocumentId); val (child, target) = parts(documentId)
        return parent == child && target.startsWith(path.trimEnd('/') + "/") && target != path
    }
    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("External browsing is read-only")
        return access(documentId, signal) { guest, path ->
            check(Files.size(guest.resolve(path)) <= 64L * 1024 * 1024) { "Export files larger than 64 MiB from FAndroidTerminal" }
            val snapshot = File.createTempFile("external-", ".snapshot", context!!.cacheDir)
            try {
                snapshot.outputStream().use { output -> guest.export(path, object : java.io.FilterOutputStream(output) {
                    override fun write(b: ByteArray, off: Int, len: Int) { signal?.throwIfCanceled(); out.write(b, off, len) }
                }) }
                ParcelFileDescriptor.open(snapshot, ParcelFileDescriptor.MODE_READ_ONLY)
            } finally { snapshot.delete() }
        }
    }
}
