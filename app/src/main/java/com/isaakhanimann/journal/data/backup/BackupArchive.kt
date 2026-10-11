// PsyZine, 2026-10-11. GPL-3.0-or-later.
package com.isaakhanimann.journal.data.backup

import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

object BackupArchive {
    const val MAX_MANIFEST_BYTES = 64L * 1024 * 1024
    const val MAX_ARCHIVE_BYTES = 2L * 1024 * 1024 * 1024
    const val MAX_ENTRIES = 10000
    fun validImagePath(path: String): Boolean = path.matches(Regex("images/[a-f0-9-]{36}\\.(jpg|png)")) &&
        runCatching { UUID.fromString(path.substringAfter('/').substringBefore('.')).toString() == path.substringAfter('/').substringBefore('.') }.getOrDefault(false)
    fun validateEntry(path: String) { require(path == "manifest.json" || validImagePath(path)) { "Unsafe or unexpected archive path" } }
    fun copyLimited(input: InputStream, output: OutputStream, limit: Long, checkpoint: () -> Unit = {}): Long {
        val buffer = ByteArray(65536); var total = 0L
        while (true) {
            checkpoint()
            val n = input.read(buffer); if (n < 0) break
            total += n; require(total <= limit) { "Backup exceeds extraction limit" }; output.write(buffer, 0, n)
        }
        return total
    }
}
