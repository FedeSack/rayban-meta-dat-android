package com.fedesack.raybanmetadat

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.meta.wearable.dat.camera.types.PhotoData
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID

class BoardCaptureWriter(private val context: Context) {
    fun savePhoto(
        photo: PhotoData,
        takenAtMs: Long,
    ): BoardCapture {
        return when (photo) {
            is PhotoData.HEIC -> saveHeic(photo.data, takenAtMs)
            is PhotoData.Bitmap -> saveBitmap(photo.bitmap, takenAtMs, CaptureSource.PHOTO)
            else -> error("Formato PhotoData no soportado")
        }
    }

    fun saveYuvFrame(
        bytes: ByteArray,
        width: Int,
        height: Int,
        takenAtMs: Long,
    ): BoardCapture {
        val jpeg =
            YuvJpeg.encode(
                ByteBuffer.wrap(bytes),
                width,
                height,
                BoardCaptureMath.JPEG_QUALITY,
            )
        return write(
            bytes = jpeg,
            takenAtMs = takenAtMs,
            extension = "jpg",
            mime = BoardCapture.MIME_JPEG,
            width = width,
            height = height,
            source = CaptureSource.FRAME,
        )
    }

    private fun saveHeic(
        buffer: ByteBuffer,
        takenAtMs: Long,
    ): BoardCapture {
        val heic = YuvJpeg.copyBuffer(buffer)
        val jpeg = heicToJpeg(heic)
        return if (jpeg != null) {
            write(
                bytes = jpeg.bytes,
                takenAtMs = takenAtMs,
                extension = "jpg",
                mime = BoardCapture.MIME_JPEG,
                width = jpeg.width,
                height = jpeg.height,
                source = CaptureSource.PHOTO,
            )
        } else {
            write(
                bytes = heic,
                takenAtMs = takenAtMs,
                extension = "heic",
                mime = BoardCapture.MIME_HEIC,
                width = null,
                height = null,
                source = CaptureSource.PHOTO,
            )
        }
    }

    private fun saveBitmap(
        bitmap: Bitmap,
        takenAtMs: Long,
        source: CaptureSource,
    ): BoardCapture {
        val jpeg = jpegEncode(bitmap, BoardCaptureMath.JPEG_QUALITY)
        return write(
            bytes = jpeg,
            takenAtMs = takenAtMs,
            extension = "jpg",
            mime = BoardCapture.MIME_JPEG,
            width = bitmap.width,
            height = bitmap.height,
            source = source,
        )
    }

    private fun write(
        bytes: ByteArray,
        takenAtMs: Long,
        extension: String,
        mime: String,
        width: Int?,
        height: Int?,
        source: CaptureSource,
    ): BoardCapture {
        val fileName = BoardCaptureMath.fileName(takenAtMs, extension)
        val uri = writeMediaStore(fileName, mime, bytes) ?: writeAppFile(fileName, bytes)
        return BoardCapture(
            id = UUID.randomUUID().toString(),
            uri = uri.toString(),
            fileName = fileName,
            takenAtMs = takenAtMs,
            width = width,
            height = height,
            source = source,
            mime = mime,
        )
    }

    private fun writeMediaStore(
        fileName: String,
        mime: String,
        bytes: ByteArray,
    ): Uri? {
        val values =
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, RELATIVE_PATH)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return null
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (_: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }

    private fun writeAppFile(
        fileName: String,
        bytes: ByteArray,
    ): Uri {
        val dir = File(context.filesDir, DIR_NAME).apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeBytes(bytes)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun heicToJpeg(heic: ByteArray): DecodedJpeg? =
        runCatching {
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(heic)))
            DecodedJpeg(jpegEncode(bitmap, BoardCaptureMath.JPEG_QUALITY), bitmap.width, bitmap.height)
        }.getOrNull()

    private fun jpegEncode(
        bitmap: Bitmap,
        quality: Int,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), out)
        return out.toByteArray()
    }

    private data class DecodedJpeg(
        val bytes: ByteArray,
        val width: Int,
        val height: Int,
    )

    companion object {
        val RELATIVE_PATH = "${Environment.DIRECTORY_PICTURES}/RaybanDat"
        const val DIR_NAME = "captures"
    }
}
