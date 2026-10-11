// PsyZine, 2026-10-11. GPL-3.0-or-later.
package com.isaakhanimann.journal.data.attachments

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

const val MAX_IMAGE_BYTES = 50L * 1024 * 1024
const val MAX_BATCH_IMAGES = 20

data class ProcessedImage(val id: String, val mimeType: String, val width: Int, val height: Int,
    val byteSize: Long, val sha256: String, val fileName: String)

@Singleton
class ImageProcessor @Inject constructor(@ApplicationContext private val context: Context, private val storage: AttachmentStorage) {
    suspend fun process(uri: Uri, directory: File): ProcessedImage = withContext(Dispatchers.IO) {
        val source = File(directory, "source-${UUID.randomUUID()}")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(source).use { output ->
                    var total = 0L; val buffer = ByteArray(65536)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer); if (n < 0) break
                        total += n; require(total <= MAX_IMAGE_BYTES) { "Image exceeds 50 MiB" }; output.write(buffer, 0, n)
                    }
                }
            } ?: error("Image is unavailable. Choose the file again.")
            normalize(source, directory)
        } finally { source.delete() }
    }
    suspend fun normalize(source: File, directory: File): ProcessedImage = withContext(Dispatchers.IO) {
        require(source.length() in 1..MAX_IMAGE_BYTES) { "Empty or oversized image" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.path, bounds)
        require(bounds.outWidth in 1..32768 && bounds.outHeight in 1..32768 &&
            bounds.outWidth.toLong() * bounds.outHeight <= 100_000_000) { "Unsupported image or unreasonable dimensions" }
        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 5120 ||
                bounds.outWidth.toLong() * bounds.outHeight / inSampleSize / inSampleSize > 8_000_000) inSampleSize *= 2
        }
        currentCoroutineContext().ensureActive()
        val decoded = try { BitmapFactory.decodeFile(source.path, opts) }
            catch (_: OutOfMemoryError) { throw java.io.IOException("Image is too large for the available memory. Choose a smaller image.") }
            ?: error("Image cannot be decoded on this Android version")
        var pixels = decoded
        val id = UUID.randomUUID().toString()
        var destination: File? = null
        try {
            val orientation = runCatching { ExifInterface(source).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
            val matrix = Matrix().apply {
                when (orientation) {
                    2 -> setScale(-1f, 1f)
                    3 -> setRotate(180f)
                    4 -> setScale(1f, -1f)
                    5 -> { setRotate(90f); postScale(-1f, 1f) }
                    6 -> setRotate(90f)
                    7 -> { setRotate(-90f); postScale(-1f, 1f) }
                    8 -> setRotate(-90f)
                }
            }
            pixels = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            if (pixels !== decoded) decoded.recycle()
            val factor = minOf(1.0, 2560.0 / maxOf(pixels.width, pixels.height))
            if (factor < 1) {
                val resized = Bitmap.createScaledBitmap(pixels, maxOf(1, (pixels.width * factor).toInt()), maxOf(1, (pixels.height * factor).toInt()), true)
                if (resized !== pixels) pixels.recycle()
                pixels = resized
            }
            currentCoroutineContext().ensureActive()
            val transparent = hasTransparency(pixels)
            val mime = if (transparent) "image/png" else "image/jpeg"
            val name = "$id.${if (transparent) "png" else "jpg"}"
            destination = File(directory, name)
            // Encoding only pixels drops EXIF, XMP and location metadata.
            FileOutputStream(destination).use {
                check(pixels.compress(if (transparent) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 85, it)) { "Image encoding failed" }
                it.fd.sync()
            }
            currentCoroutineContext().ensureActive()
            ProcessedImage(id, mime, pixels.width, pixels.height, destination.length(), storage.hash(destination), name)
        } catch (_: OutOfMemoryError) {
            destination?.delete()
            throw java.io.IOException("Image is too large for the available memory. Choose a smaller image.")
        } catch (e: Throwable) { destination?.delete(); throw e }
        finally { pixels.recycle(); if (!decoded.isRecycled) decoded.recycle() }
    }
    private suspend fun hasTransparency(bitmap: Bitmap): Boolean {
        if (!bitmap.hasAlpha()) return false
        val row = IntArray(bitmap.width)
        for (y in 0 until bitmap.height) {
            currentCoroutineContext().ensureActive()
            bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            if (row.any { (it ushr 24) != 255 }) return true
        }
        return false
    }
    suspend fun thumbnail(id: String, source: File): File = withContext(Dispatchers.IO) {
        require(id.matches(Regex("[a-f0-9-]{36}")))
        val target = File(storage.thumbnails, "$id.png")
        if (!target.exists()) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.path, bounds)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 768) inSampleSize = maxOf(1, inSampleSize) * 2
            }
            val decoded = BitmapFactory.decodeFile(source.path, opts) ?: error("Photo is missing")
            try {
                val factor = minOf(1.0, 384.0 / maxOf(decoded.width, decoded.height))
                val thumb = Bitmap.createScaledBitmap(decoded, maxOf(1, (decoded.width * factor).toInt()), maxOf(1, (decoded.height * factor).toInt()), true)
                try {
                    val temp = File(storage.thumbnails, "$id-${UUID.randomUUID()}.tmp")
                    try {
                        FileOutputStream(temp).use { check(thumb.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                        check(temp.renameTo(target)) { "Thumbnail write failed" }
                    } finally { temp.delete() }
                } finally { if (thumb !== decoded) thumb.recycle() }
            } finally { decoded.recycle() }
        }
        target
    }
}
