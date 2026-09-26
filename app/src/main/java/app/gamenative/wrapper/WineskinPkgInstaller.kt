package app.gamenative.wrapper

import android.os.SystemClock
import android.util.Xml
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import org.xmlpull.v1.XmlPullParser

/**
 * Extracts a Windows game out of a macOS Wineskin `.pkg`, reading the file strictly sequentially.
 *
 * Layout (verified on MapleLegends-MAC19SEP2026v2.pkg):
 *   xar header (28 bytes, big-endian): "xar!", u16 headerSize, u16 version,
 *                                     u64 tocCompressedLength, u64 tocUncompressedLength, u32 checksumAlgo
 *   TOC:  zlib-compressed XML right after the header; the heap starts right after the TOC.
 *   `<something>.pkg/Payload` in the heap is gzip-compressed cpio ("odc" ASCII) holding the .app bundle,
 *   whose Wine prefix contains the Windows game under `.../drive_c/...`.
 */
object WineskinPkgInstaller {
    private const val BUF_SIZE = 64 * 1024
    private const val XAR_HEADER_SIZE = 28
    private const val DRIVE_C = "/drive_c/"

    /**
     * Extracts the folder that directly contains [exeName] into [targetDir] and returns it.
     * Only `drive_c` content is written (to a staging folder), everything else is skipped.
     * [totalBytes] is the .pkg size for progress (-1 if unknown).
     */
    fun install(
        input: InputStream,
        totalBytes: Long,
        exeName: String,
        targetDir: File,
        onProgress: (Float) -> Unit,
    ): File {
        val counted = CountingInputStream(input)
        val progress = Progress(totalBytes, onProgress)

        // --- xar header
        val header = ByteArray(XAR_HEADER_SIZE)
        readFully(counted, header, 0, header.size)
        if (String(header, 0, 4, Charsets.US_ASCII) != "xar!") {
            throw IOException("Not a macOS .pkg (xar) file")
        }
        val headerSize = readU16(header, 4)
        if (headerSize < XAR_HEADER_SIZE) throw IOException("Corrupt .pkg header")
        val tocCompressedLength = readU64(header, 8)
        skipFully(counted, (headerSize - XAR_HEADER_SIZE).toLong())
        val heapStart = headerSize + tocCompressedLength

        // --- TOC -> Payload location
        val payload = readToc(counted, tocCompressedLength)
            .firstOrNull { it.path.endsWith("/Payload") && it.path.substringBeforeLast('/').endsWith(".pkg") }
            ?: throw IOException("No Payload in this .pkg - is it the Mac version of the game?")

        // --- Payload: gzip -> cpio
        skipFully(counted, heapStart + payload.offset - counted.count)
        val staging = File(targetDir, "staging").apply { deleteRecursively() }
        GZIPInputStream(BoundedInputStream(counted, payload.length), BUF_SIZE).use { cpio ->
            extractCpio(cpio, staging) { progress.report(counted.count) }
        }

        // --- move the game folder out of staging
        val exe = findFile(staging, exeName)
            ?: throw IOException("$exeName not found in the package")
        val gameDir = exe.parentFile ?: throw IOException("$exeName not found in the package")
        val finalDir = File(targetDir, gameDir.name)
        finalDir.deleteRecursively()
        if (!gameDir.renameTo(finalDir)) throw IOException("Could not move the game folder into place")
        staging.deleteRecursively()
        onProgress(1f)
        return finalDir
    }

    // ------------------------------------------------------------------ TOC

    private class TocEntry(val path: String, val offset: Long, val length: Long)

    /** A `<file>` element being parsed; its own `<name>`/`<data>` are its direct children. */
    private class Frame(val depth: Int, val parentPath: String) {
        var name: String? = null
        var offset = -1L
        var length = -1L
        val path get() = if (parentPath.isEmpty()) name.orEmpty() else "$parentPath/${name.orEmpty()}"
    }

    private fun readToc(input: InputStream, compressedLength: Long): List<TocEntry> {
        val entries = mutableListOf<TocEntry>()
        val parser = Xml.newPullParser()
        parser.setInput(InflaterInputStream(BoundedInputStream(input, compressedLength), Inflater(), BUF_SIZE), null)
        val stack = ArrayDeque<Frame>()
        var inDataOf: Frame? = null

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            val frame = stack.lastOrNull()
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when {
                    parser.name == "file" -> {
                        // Children are nested inside their directory's <file>; the directory's <name>
                        // precedes its children, so its path is known by now.
                        stack.addLast(Frame(parser.depth, frame?.path.orEmpty()))
                    }
                    frame == null -> Unit
                    parser.name == "name" && parser.depth == frame.depth + 1 -> frame.name = parser.nextText()
                    parser.name == "data" && parser.depth == frame.depth + 1 -> inDataOf = frame
                    inDataOf === frame && parser.depth == frame.depth + 2 -> when (parser.name) {
                        "offset" -> frame.offset = parser.nextText().trim().toLongOrNull() ?: -1L
                        "length" -> frame.length = parser.nextText().trim().toLongOrNull() ?: -1L
                    }
                }
                XmlPullParser.END_TAG -> when {
                    parser.name == "data" && frame != null && parser.depth == frame.depth + 1 -> inDataOf = null
                    parser.name == "file" -> {
                        val done = stack.removeLast()
                        if (done.name != null && done.offset >= 0 && done.length >= 0) {
                            entries.add(TocEntry(done.path, done.offset, done.length))
                        }
                    }
                }
            }
        }
        return entries
    }

    // ------------------------------------------------------------------ cpio (odc)

    private fun extractCpio(cpio: InputStream, staging: File, onEntry: () -> Unit) {
        val header = ByteArray(76)
        while (true) {
            readFully(cpio, header, 0, header.size)
            if (String(header, 0, 6, Charsets.US_ASCII) != "070707") throw IOException("Corrupt game archive")
            val mode = parseOctal(header, 18, 6).toInt()
            val nameSize = parseOctal(header, 59, 6).toInt()
            val fileSize = parseOctal(header, 65, 11)
            if (nameSize < 1) throw IOException("Corrupt game archive")
            val nameBytes = ByteArray(nameSize)
            readFully(cpio, nameBytes, 0, nameSize)
            val name = String(nameBytes, 0, nameSize - 1, Charsets.UTF_8) // drop trailing NUL
            if (name == "TRAILER!!!") return

            val type = mode and 0o170000
            val driveC = name.indexOf(DRIVE_C)
            val relative = if (driveC >= 0) name.substring(driveC + DRIVE_C.length) else null
            if (relative != null && relative.isNotEmpty() && type == 0o100000) {
                checkSafe(relative)
                val dest = File(staging, relative)
                dest.parentFile?.mkdirs()
                FileOutputStream(dest).use { copy(cpio, it, fileSize) }
            } else {
                if (relative != null && relative.isNotEmpty() && type == 0o040000) {
                    checkSafe(relative)
                    File(staging, relative).mkdirs()
                }
                skipFully(cpio, fileSize) // everything outside drive_c, symlinks, specials
            }
            onEntry()
        }
    }

    private fun checkSafe(path: String) {
        if (path.startsWith('/') || path.split('/').any { it == ".." }) {
            throw IOException("Unsafe path in archive: $path")
        }
    }

    private fun findFile(dir: File, name: String): File? {
        for (f in dir.listFiles() ?: return null) {
            if (f.isFile && f.name.equals(name, ignoreCase = true)) return f
        }
        for (f in dir.listFiles() ?: return null) {
            if (f.isDirectory) findFile(f, name)?.let { return it }
        }
        return null
    }

    // ------------------------------------------------------------------ helpers

    /** Counts bytes consumed from the original .pkg stream (for progress and absolute offsets). */
    private class CountingInputStream(private val input: InputStream) : InputStream() {
        var count = 0L
            private set

        override fun read(): Int = input.read().also { if (it >= 0) count++ }
        override fun read(b: ByteArray, off: Int, len: Int): Int = input.read(b, off, len).also { if (it > 0) count += it }
        override fun skip(n: Long): Long = input.skip(n).also { if (it > 0) count += it }
        override fun available(): Int = input.available()
    }

    /** Exposes at most [limit] bytes of [input]; closing it does not close [input]. */
    private class BoundedInputStream(private val input: InputStream, limit: Long) : InputStream() {
        private var remaining = limit

        override fun read(): Int {
            if (remaining <= 0) return -1
            return input.read().also { if (it >= 0) remaining-- }
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (remaining <= 0) return -1
            return input.read(b, off, minOf(len.toLong(), remaining).toInt()).also { if (it > 0) remaining -= it }
        }
    }

    private class Progress(private val totalBytes: Long, private val onProgress: (Float) -> Unit) {
        private var last = 0L

        fun report(count: Long) {
            if (totalBytes <= 0) return
            val now = SystemClock.elapsedRealtime()
            if (now - last < 500) return
            last = now
            onProgress((count.toFloat() / totalBytes).coerceIn(0f, 1f))
        }
    }

    private fun readFully(input: InputStream, buf: ByteArray, off: Int, len: Int) {
        var pos = off
        var left = len
        while (left > 0) {
            val r = input.read(buf, pos, left)
            if (r < 0) throw IOException("The file ended early - is the download complete?")
            pos += r
            left -= r
        }
    }

    private fun skipFully(input: InputStream, n: Long) {
        var left = n
        val buf = ByteArray(BUF_SIZE)
        while (left > 0) {
            val s = input.skip(left)
            if (s > 0) {
                left -= s
                continue
            }
            val r = input.read(buf, 0, minOf(left, buf.size.toLong()).toInt())
            if (r < 0) throw IOException("The file ended early - is the download complete?")
            left -= r
        }
    }

    private fun copy(input: InputStream, out: FileOutputStream, n: Long) {
        var left = n
        val buf = ByteArray(BUF_SIZE)
        while (left > 0) {
            val r = input.read(buf, 0, minOf(left, buf.size.toLong()).toInt())
            if (r < 0) throw IOException("The file ended early - is the download complete?")
            out.write(buf, 0, r)
            left -= r
        }
    }

    private fun readU16(b: ByteArray, off: Int): Int = ((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF)

    private fun readU64(b: ByteArray, off: Int): Long {
        var v = 0L
        for (i in 0 until 8) v = (v shl 8) or (b[off + i].toLong() and 0xFF)
        return v
    }

    private fun parseOctal(b: ByteArray, off: Int, len: Int): Long {
        var v = 0L
        for (i in off until off + len) {
            val c = b[i].toInt()
            if (c == ' '.code || c == 0) continue
            if (c < '0'.code || c > '7'.code) throw IOException("Corrupt game archive")
            v = (v shl 3) or (c - '0'.code).toLong()
        }
        return v
    }
}
