package com.example.encoder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.OpenableColumns
import com.example.model.AudioItem
import com.example.model.ImageItem
import com.example.model.ScaleMode
import java.io.InputStream
import java.util.Locale

object MediaHelper {

    fun queryImageItem(context: Context, uri: Uri): ImageItem {
        var name = "image"
        var sizeBytes = 0L
        val contentResolver = context.contentResolver

        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        name = cursor.getString(nameIndex) ?: name
                    }
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1) {
                        sizeBytes = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (_: Exception) {}

        val mimeType = contentResolver.getType(uri) ?: "image/jpeg"

        // Measure bounds without loading pixels
        var width = 0
        var height = 0
        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(stream, null, options)
                width = options.outWidth
                height = options.outHeight
            }
        } catch (_: Exception) {}

        return ImageItem(
            uri = uri,
            name = name,
            width = width,
            height = height,
            sizeBytes = sizeBytes,
            mimeType = mimeType
        )
    }

    fun queryAudioItem(context: Context, uri: Uri): AudioItem {
        var name = "audio"
        var sizeBytes = 0L
        val contentResolver = context.contentResolver

        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        name = cursor.getString(nameIndex) ?: name
                    }
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1) {
                        sizeBytes = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (_: Exception) {}

        var durationMs = 0L
        var bitrate = 0
        var sampleRate = 44100
        var channelCount = 2
        var mimeType = contentResolver.getType(uri) ?: "audio/mpeg"

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            if (!durationStr.isNullOrEmpty()) {
                durationMs = durationStr.toLongOrNull() ?: 0L
            }
            val titleStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            if (!titleStr.isNullOrEmpty()) {
                val artistStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                name = if (!artistStr.isNullOrEmpty()) "$artistStr - $titleStr" else titleStr
            }
            val bitrateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            if (!bitrateStr.isNullOrEmpty()) {
                bitrate = bitrateStr.toIntOrNull() ?: 0
            }
            val mimeStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            if (!mimeStr.isNullOrEmpty()) {
                mimeType = mimeStr
            }
        } catch (_: Exception) {
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }

        return AudioItem(
            uri = uri,
            name = name,
            durationMs = durationMs,
            sizeBytes = sizeBytes,
            mimeType = mimeType,
            bitrate = bitrate,
            sampleRate = sampleRate,
            channelCount = channelCount
        )
    }

    fun loadAndScaleBitmap(
        context: Context,
        uri: Uri,
        targetWidth: Int,
        targetHeight: Int,
        scaleMode: ScaleMode
    ): Bitmap? {
        val resolver = context.contentResolver
        var srcWidth = 0
        var srcHeight = 0

        // 1. Measure dimensions
        try {
            resolver.openInputStream(uri)?.use { stream ->
                val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(stream, null, boundsOptions)
                srcWidth = boundsOptions.outWidth
                srcHeight = boundsOptions.outHeight
            }
        } catch (e: Exception) {
            return null
        }

        if (srcWidth <= 0 || srcHeight <= 0) return null

        // 2. Compute sample size to avoid OOM
        var sampleSize = 1
        while (srcWidth / (sampleSize * 2) >= targetWidth && srcHeight / (sampleSize * 2) >= targetHeight) {
            sampleSize *= 2
        }

        // 3. Decode sub-sampled bitmap
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val decodedBitmap: Bitmap = (try {
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }
        } catch (e: Exception) {
            null
        }) ?: return null

        // 4. Render into exact target dimensions based on ScaleMode
        val outputBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)
        canvas.drawColor(Color.BLACK)

        val srcRect = Rect(0, 0, decodedBitmap.width, decodedBitmap.height)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        when (scaleMode) {
            ScaleMode.FIT -> {
                val scale = minOf(
                    targetWidth.toFloat() / decodedBitmap.width.toFloat(),
                    targetHeight.toFloat() / decodedBitmap.height.toFloat()
                )
                val dstW = decodedBitmap.width * scale
                val dstH = decodedBitmap.height * scale
                val left = (targetWidth - dstW) / 2f
                val top = (targetHeight - dstH) / 2f
                val dstRect = RectF(left, top, left + dstW, top + dstH)
                canvas.drawBitmap(decodedBitmap, srcRect, dstRect, paint)
            }
            ScaleMode.CROP -> {
                val scale = maxOf(
                    targetWidth.toFloat() / decodedBitmap.width.toFloat(),
                    targetHeight.toFloat() / decodedBitmap.height.toFloat()
                )
                val dstW = decodedBitmap.width * scale
                val dstH = decodedBitmap.height * scale
                val left = (targetWidth - dstW) / 2f
                val top = (targetHeight - dstH) / 2f
                val dstRect = RectF(left, top, left + dstW, top + dstH)
                canvas.drawBitmap(decodedBitmap, srcRect, dstRect, paint)
            }
            ScaleMode.ORIGINAL -> {
                // Already centered fit
                val scale = minOf(
                    targetWidth.toFloat() / decodedBitmap.width.toFloat(),
                    targetHeight.toFloat() / decodedBitmap.height.toFloat()
                )
                val dstW = decodedBitmap.width * scale
                val dstH = decodedBitmap.height * scale
                val left = (targetWidth - dstW) / 2f
                val top = (targetHeight - dstH) / 2f
                val dstRect = RectF(left, top, left + dstW, top + dstH)
                canvas.drawBitmap(decodedBitmap, srcRect, dstRect, paint)
            }
        }

        decodedBitmap.recycle()
        return outputBitmap
    }

    fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format(Locale.getDefault(), "%02d:%02d", min, sec)
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 KB"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1.0) {
            String.format(Locale.getDefault(), "%.1f MB", mb)
        } else {
            String.format(Locale.getDefault(), "%.0f KB", kb)
        }
    }

    fun getAvailableStorageBytes(): Long {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (_: Exception) {
            500L * 1024L * 1024L // fallback 500MB
        }
    }

    fun estimateOutputSizeBytes(durationMs: Long, videoBitrate: Int, audioBitrate: Int): Long {
        val durationSeconds = maxOf(1L, durationMs / 1000L)
        val totalBitsPerSec = (videoBitrate + audioBitrate).toLong()
        val totalBytes = (totalBitsPerSec * durationSeconds) / 8L
        // add 5% container overhead
        return (totalBytes * 1.05).toLong()
    }

    fun getSdCardDestination(context: Context): com.example.model.StorageDestination.SdCard? {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            try {
                val volumes = android.provider.MediaStore.getExternalVolumeNames(context)
                for (vol in volumes) {
                    if (vol != android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY && vol != "internal") {
                        return com.example.model.StorageDestination.SdCard(
                            volumeName = vol,
                            displayName = "SD Card ($vol)"
                        )
                    }
                }
            } catch (_: Exception) {}
        }

        val externalDirs = context.getExternalFilesDirs(null)
        if (externalDirs.size > 1 && externalDirs[1] != null) {
            val secondDir = externalDirs[1]
            val isRemovable = try {
                Environment.isExternalStorageRemovable(secondDir)
            } catch (_: Exception) { true }
            if (isRemovable) {
                return com.example.model.StorageDestination.SdCard(
                    volumeName = null,
                    displayName = "SD Card"
                )
            }
        }
        return null
    }

    fun getFolderDisplayName(context: Context, treeUri: Uri): String {
        return try {
            val docId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
            val parts = docId.split(":")
            if (parts.size > 1 && parts[1].isNotEmpty()) {
                parts[1]
            } else {
                docId
            }
        } catch (_: Exception) {
            "Custom Folder"
        }
    }
}
