package com.example.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.earn.EarnLogic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID

sealed interface UploadStatus {
    data class Progress(val percent: Int) : UploadStatus
    data class Completed(
        val downloadUrl: String,
        val thumbnailUrl: String = "",
        val contentHash: String = "",
        val fileSizeBytes: Long = 0L
    ) : UploadStatus
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
        val ownerUid = resolveOwnerUid(userId)
        val fileId = UUID.randomUUID().toString()
        emit(UploadStatus.Progress(10))

        val (contentHash, measuredSize) = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(videoUri)?.use { stream ->
                    EarnLogic.computeStreamSha256AndSize(stream)
                }
            }.getOrNull() ?: ("" to -1L)
        }
        if (measuredSize <= 0L) {
            emit(UploadStatus.Failed("Unable to read selected video file"))
            return@flow
        }

        emit(UploadStatus.Progress(35))
        val videoUrl = try {
            supabase.uploadStorageStream(
                objectPath = "videos/$ownerUid/$fileId.mp4",
                contentLength = measuredSize,
                contentType = context.contentResolver.getType(videoUri) ?: "video/mp4",
                openStream = { context.contentResolver.openInputStream(videoUri) }
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emit(
                UploadStatus.Failed(
                    e.localizedMessage ?: "Video upload failed (verify Supabase Storage bucket ${supabase.storageBucket} & RLS policies)"
                )
            )
            return@flow
        }

        emit(UploadStatus.Progress(80))
        val thumbBytes = withContext(Dispatchers.IO) {
            if (customCoverUri != null) {
                readUriBytes(context, customCoverUri)?.let { encodeCompressedImageBytes(it, maxEdgePx = 720) ?: it }
            } else {
                extractVideoFrameBytes(context, videoUri)
            }
        }

        val thumbUrl = if (thumbBytes != null && thumbBytes.isNotEmpty()) {
            runCatching {
                supabase.uploadStorageObject(
                    objectPath = "thumbnails/$ownerUid/$fileId.jpg",
                    bytes = thumbBytes,
                    contentType = "image/jpeg"
                )
            }.getOrDefault("")
        } else {
            ""
        }

        emit(UploadStatus.Progress(100))
        emit(
            UploadStatus.Completed(
                downloadUrl = videoUrl,
                thumbnailUrl = thumbUrl,
                contentHash = contentHash,
                fileSizeBytes = measuredSize.coerceAtLeast(0L)
            )
        )
    }

    suspend fun uploadImage(userId: String, folder: String, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val ownerUid = resolveOwnerUid(userId)
            val cleanFolder = folder.trim().trim('/')
            require(cleanFolder.isNotEmpty()) { "Invalid upload folder" }
            val rawBytes = readUriBytes(appContext, uri)
                ?.takeIf { it.isNotEmpty() }
                ?: throw IllegalStateException("تعذر قراءة ملف الصورة المختار")
            val compressedBytes = encodeCompressedImageBytes(rawBytes)
            val payload = compressedBytes?.takeIf { it.isNotEmpty() } ?: rawBytes
            val isJpeg = compressedBytes != null && compressedBytes.isNotEmpty()
            val ext = if (isJpeg) "jpg" else guessExtensionFromUri(appContext, uri)
            val mime = if (isJpeg) "image/jpeg" else (appContext.contentResolver.getType(uri) ?: "image/jpeg")
            supabase.uploadStorageObject(
                objectPath = "$cleanFolder/$ownerUid/${UUID.randomUUID()}.$ext",
                bytes = payload,
                contentType = mime
            )
        }
    }

    suspend fun uploadMediaFile(
        userId: String,
        folder: String,
        uri: Uri,
        extension: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val ownerUid = resolveOwnerUid(userId)
            val cleanFolder = folder.trim().trim('/')
            require(cleanFolder.isNotEmpty()) { "Invalid upload folder" }
            val bytes = readUriBytes(appContext, uri)
                ?.takeIf { it.isNotEmpty() }
                ?: throw IllegalStateException("Unable to read media file")
            val mimeType = when (extension.lowercase()) {
                "mp4" -> "video/mp4"
                "m4a" -> "audio/mp4"
                "mp3" -> "audio/mpeg"
                "png" -> "image/png"
                "webp" -> "image/webp"
                else -> "image/jpeg"
            }
            supabase.uploadStorageObject(
                objectPath = "$cleanFolder/$ownerUid/${UUID.randomUUID()}.$extension",
                bytes = bytes,
                contentType = mimeType
            )
        }
    }

    private fun resolveOwnerUid(fallbackUserId: String): String =
        supabase.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: fallbackUserId.trim().takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in.")

    private fun readUriBytes(context: Context, uri: Uri): ByteArray? = try {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (_: Exception) {
        null
    }

    private fun guessExtensionFromUri(context: Context, uri: Uri): String {
        val mime = context.contentResolver.getType(uri).orEmpty().lowercase()
        return when {
            mime.contains("png") -> "png"
            mime.contains("webp") -> "webp"
            else -> "jpg"
        }
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
                if (scaled !== bitmap) scaled.recycle()
                bitmap.recycle()
                baos.toByteArray()
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        fun encodeCompressedImageBytes(rawBytes: ByteArray, maxEdgePx: Int = 720): ByteArray? {
            if (rawBytes.isEmpty()) return null
            return try {
                val exifOrientation = runCatching {
                    ExifInterface(ByteArrayInputStream(rawBytes)).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

                var sampleSize = 1
                while ((bounds.outWidth / sampleSize) > maxEdgePx * 2 || (bounds.outHeight / sampleSize) > maxEdgePx * 2) {
                    sampleSize *= 2
                }
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
                    ?: return null

                val oriented = applyExifOrientation(decoded, exifOrientation)
                if (oriented !== decoded) decoded.recycle()

                val maxDim = maxOf(oriented.width, oriented.height)
                val scaled = if (maxDim > maxEdgePx) {
                    val ratio = maxEdgePx.toFloat() / maxDim.toFloat()
                    Bitmap.createScaledBitmap(
                        oriented,
                        (oriented.width * ratio).toInt().coerceAtLeast(1),
                        (oriented.height * ratio).toInt().coerceAtLeast(1),
                        true
                    )
                } else {
                    oriented
                }
                val baos = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 86, baos)
                if (scaled !== oriented) scaled.recycle()
                oriented.recycle()
                baos.toByteArray().takeIf { it.isNotEmpty() }
            } catch (_: Exception) {
                null
            }
        }

        private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    matrix.postRotate(90f)
                    matrix.postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    matrix.postRotate(270f)
                    matrix.postScale(-1f, 1f)
                }
                else -> return bitmap
            }
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
    }
}
