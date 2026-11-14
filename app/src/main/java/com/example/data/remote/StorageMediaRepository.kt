package com.example.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

sealed interface UploadStatus {
    data class Progress(val percent: Int) : UploadStatus
    data class Completed(val downloadUrl: String, val thumbnailUrl: String = "") : UploadStatus
    data class Failed(val message: String) : UploadStatus
}

class StorageMediaRepository(
    private val appContext: Context,
    private val supabase: SupabaseClient = SupabaseClient.getInstance(appContext)
) {
    fun uploadVideoWithThumbnail(
        context: Context,
        userId: String,
        videoUri: Uri,
        customCoverUri: Uri? = null
    ): Flow<UploadStatus> = flow {
        val fileId = UUID.randomUUID().toString()
        emit(UploadStatus.Progress(10))

        val contentLength = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openAssetFileDescriptor(videoUri, "r")?.use { it.length } ?: -1L
            }.getOrDefault(-1L)
        }
        val canRead = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(videoUri)?.use { it.available() >= 0 } ?: false
            }.getOrDefault(false)
        }
        if (!canRead) {
            emit(UploadStatus.Failed("Unable to read selected video file"))
            return@flow
        }

        emit(UploadStatus.Progress(35))
        val videoUrl = try {
            supabase.uploadStorageStream(
                objectPath = "videos/$userId/$fileId.mp4",
                contentLength = contentLength,
                contentType = context.contentResolver.getType(videoUri) ?: "video/mp4",
                openStream = { context.contentResolver.openInputStream(videoUri) }
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emit(
                UploadStatus.Failed(
                    e.localizedMessage ?: "Video upload failed (verify Supabase Storage bucket vyora-media & RLS policies)"
                )
            )
            return@flow
        }

        emit(UploadStatus.Progress(80))
        val thumbBytes = withContext(Dispatchers.IO) {
            if (customCoverUri != null) {
                readUriBytes(context, customCoverUri)
            } else {
                extractVideoFrameBytes(context, videoUri)
            }
        }

        val thumbUrl = if (thumbBytes != null && thumbBytes.isNotEmpty()) {
            runCatching {
                supabase.uploadStorageObject(
                    objectPath = "thumbnails/$userId/$fileId.jpg",
                    bytes = thumbBytes,
                    contentType = "image/jpeg"
                )
            }.getOrDefault("")
        } else {
            ""
        }

        emit(UploadStatus.Progress(100))
        emit(UploadStatus.Completed(downloadUrl = videoUrl, thumbnailUrl = thumbUrl))
    }

    suspend fun uploadImage(userId: String, folder: String, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = encodeCompressedImageBytes(appContext, uri)
                ?: readUriBytes(appContext, uri)
                ?: throw IllegalStateException("Unable to read image file")
            supabase.uploadStorageObject(
                objectPath = "$folder/$userId/${UUID.randomUUID()}.jpg",
                bytes = bytes,
                contentType = "image/jpeg"
            )
        }
    }

    private fun encodeCompressedImageBytes(context: Context, uri: Uri, maxEdgePx: Int = 720): ByteArray? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sampleSize = 1
            while ((bounds.outWidth / sampleSize) > maxEdgePx * 2 || (bounds.outHeight / sampleSize) > maxEdgePx * 2) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: return null

            val maxDim = maxOf(decoded.width, decoded.height)
            val scaled = if (maxDim > maxEdgePx) {
                val ratio = maxEdgePx.toFloat() / maxDim.toFloat()
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * ratio).toInt().coerceAtLeast(1),
                    (decoded.height * ratio).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                decoded
            }
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 86, baos)
            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()
            baos.toByteArray()
        } catch (_: Exception) {
            null
        }
    }

    suspend fun uploadMediaFile(
        userId: String,
        folder: String,
        uri: Uri,
        extension: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = readUriBytes(appContext, uri)
                ?: throw IllegalStateException("Unable to read media file")
            val mimeType = when (extension.lowercase()) {
                "mp4" -> "video/mp4"
                "m4a" -> "audio/mp4"
                "mp3" -> "audio/mpeg"
                else -> "image/jpeg"
            }
            supabase.uploadStorageObject(
                objectPath = "$folder/$userId/${UUID.randomUUID()}.$extension",
                bytes = bytes,
                contentType = mimeType
            )
        }
    }

    private fun readUriBytes(context: Context, uri: Uri): ByteArray? = try {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (_: Exception) {
        null
    }

    private fun extractVideoFrameBytes(context: Context, videoUri: Uri): ByteArray? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, videoUri)
            val bitmap = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            retriever.release()
            if (bitmap != null) {
                val targetHeight = (480 * bitmap.height / bitmap.width.coerceAtLeast(1)).coerceAtLeast(480)
                val scaled = Bitmap.createScaledBitmap(bitmap, 480, targetHeight, true)
                val baos = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 78, baos)
                baos.toByteArray()
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
