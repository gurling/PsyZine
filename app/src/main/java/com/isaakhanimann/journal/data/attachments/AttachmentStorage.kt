// PsyZine, 2026-10-11. GPL-3.0-or-later.
package com.isaakhanimann.journal.data.attachments

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AttachmentStorage @Inject constructor(@ApplicationContext private val context: Context) {
    val root get() = File(context.filesDir, "attachments").apply { mkdirs() }
    val staging get() = File(context.filesDir, "staging").apply { mkdirs() }
    val camera get() = File(staging, "camera").apply { mkdirs() }
    val thumbnails get() = File(context.cacheDir, "attachment-thumbnails").apply { mkdirs() }
    fun draftDir(id: String): File {
        require(runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false))
        return File(staging, id).apply { mkdirs() }
    }
    fun file(relative: String): File {
        require(relative.matches(Regex("attachments/[a-f0-9-]{36}\\.(jpg|png)"))) { "Invalid attachment path" }
        return File(context.filesDir, relative).also {
            require(it.canonicalFile.parentFile == root.canonicalFile) { "Invalid attachment path" }
        }
    }
    fun relative(id: String, mime: String) = "attachments/$id.${if (mime == "image/png") "png" else "jpg"}"
    fun hash(file: File): String = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(65536)
        while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
}
