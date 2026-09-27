package app.gamenative.wrapper

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicLong

/**
 * Copies an already-extracted game folder, chosen with the system folder picker (no storage permission),
 * into the app's own storage. Tuned for folders with thousands of small files:
 * - lists each directory with one DocumentsContract query (DocumentFile would issue one query per property),
 * - copies several files at once to hide per-file overhead,
 * - lets the kernel move the bytes (FileChannel.transferTo).
 * The source folder is only read, never modified.
 */
object WrapperFolderImporter {
    private const val COPY_THREADS = 6

    private class Entry(val relativePath: String, val documentId: String, val isDir: Boolean, val size: Long)

    /** Imports the folder containing [exeName] (the picked folder or anything below it) into [targetDir]. */
    fun import(context: Context, treeUri: Uri, exeName: String, targetDir: File, onProgress: (Float) -> Unit): File {
        val resolver = context.contentResolver
        val entries = listTree(context, treeUri)

        val exe = entries.firstOrNull { !it.isDir && it.relativePath.substringAfterLast('/').equals(exeName, ignoreCase = true) }
            ?: throw IOException("$exeName not found in the chosen folder")
        val rootPrefix = exe.relativePath.substringBeforeLast('/', "")
        val gameName = rootPrefix.substringAfterLast('/').ifEmpty { treeName(treeUri) }
        val underRoot = entries.filter { rootPrefix.isEmpty() || it.relativePath.startsWith("$rootPrefix/") }
        fun rel(entry: Entry) = if (rootPrefix.isEmpty()) entry.relativePath else entry.relativePath.removePrefix("$rootPrefix/")

        val staging = File(targetDir, "staging").apply { deleteRecursively(); mkdirs() }
        underRoot.filter { it.isDir }.forEach { File(staging, rel(it)).mkdirs() }

        val files = underRoot.filter { !it.isDir }
        val totalBytes = files.sumOf { it.size }.coerceAtLeast(1L)
        val copied = AtomicLong()
        var lastReport = 0L
        val pool = Executors.newFixedThreadPool(COPY_THREADS)
        try {
            val jobs: List<Future<*>> = files.map { entry ->
                pool.submit(Runnable {
                    val dest = File(staging, rel(entry))
                    dest.parentFile?.mkdirs()
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, entry.documentId)
                    val pfd = resolver.openFileDescriptor(docUri, "r") ?: throw IOException("Cannot read ${entry.relativePath}")
                    pfd.use {
                        FileInputStream(it.fileDescriptor).channel.use { src ->
                            FileOutputStream(dest).channel.use { dst ->
                                var position = 0L
                                val size = src.size()
                                while (position < size) position += src.transferTo(position, size - position, dst)
                            }
                        }
                    }
                    copied.addAndGet(entry.size)
                })
            }
            for (job in jobs) {
                job.get() // rethrows the first copy failure
                val now = SystemClock.elapsedRealtime()
                if (now - lastReport >= 500) {
                    lastReport = now
                    onProgress((copied.get().toFloat() / totalBytes).coerceIn(0f, 1f))
                }
            }
        } finally {
            pool.shutdownNow()
        }

        val finalDir = File(targetDir, gameName)
        finalDir.deleteRecursively()
        if (!staging.renameTo(finalDir)) throw IOException("Could not move the game folder into place")
        onProgress(1f)
        return finalDir
    }

    /** Lists the whole tree with one child query per directory. */
    private fun listTree(context: Context, treeUri: Uri): List<Entry> {
        val resolver = context.contentResolver
        val result = mutableListOf<Entry>()
        val pending = ArrayDeque<Pair<String, String>>() // documentId to relative path
        pending.add(DocumentsContract.getTreeDocumentId(treeUri) to "")
        val projection = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE)

        while (pending.isNotEmpty()) {
            val (parentId, parentPath) = pending.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    val name = cursor.getString(1)
                    if (name.isNullOrEmpty() || name == "." || name == "..") continue
                    val isDir = cursor.getString(2) == Document.MIME_TYPE_DIR
                    val size = if (cursor.isNull(3)) 0L else cursor.getLong(3)
                    val path = if (parentPath.isEmpty()) name else "$parentPath/$name"
                    result.add(Entry(path, id, isDir, size))
                    if (isDir) pending.add(id to path)
                }
            }
        }
        return result
    }

    private fun treeName(treeUri: Uri): String =
        DocumentsContract.getTreeDocumentId(treeUri).substringAfterLast(':').substringAfterLast('/').ifEmpty { "game" }
}
