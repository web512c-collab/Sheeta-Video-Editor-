package com.example.encoder

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.example.model.AudioBitrate
import com.example.model.EncodingProgress
import com.example.model.EncodingStep
import com.example.model.ScaleMode
import com.example.model.SlideDurationMode
import com.example.model.StorageDestination
import com.example.model.VideoQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VideoAudioEncoder(private val context: Context) {

    companion object {
        private const val TIMEOUT_US = 10_000L
        private const val FPS = 15 // 15 frames per second: optimal for size, speed and smooth playback
    }

    suspend fun encode(
        imageUris: List<Uri>,
        audioUri: Uri,
        videoQuality: VideoQuality,
        audioBitrate: AudioBitrate,
        scaleMode: ScaleMode,
        slideDurationMode: SlideDurationMode,
        storageDestination: StorageDestination = StorageDestination.Internal,
        onProgress: (EncodingProgress) -> Unit,
        isCancelled: () -> Boolean
    ): EncodingProgress = withContext(Dispatchers.IO) {

        val cacheDir = File(context.cacheDir, "videos").apply { mkdirs() }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val tempAudioFile = File(cacheDir, "temp_audio_${timeStamp}.m4a")
        val tempVideoFile = File(cacheDir, "temp_video_${timeStamp}.mp4")
        val finalVideoFile = File(cacheDir, "Video_${timeStamp}.mp4")

        try {
            // Check storage
            val freeBytes = MediaHelper.getAvailableStorageBytes()
            if (freeBytes < 20L * 1024L * 1024L) {
                return@withContext EncodingProgress(
                    step = EncodingStep.ERROR,
                    errorMessage = "Not enough storage space available on device (at least 20MB required)."
                )
            }

            // Step 1: PREPARING
            onProgress(
                EncodingProgress(
                    step = EncodingStep.PREPARING,
                    progress = 0.05f,
                    statusText = "Preparing encoder and settings…"
                )
            )

            if (isCancelled()) throw InterruptedException("Cancelled")

            // Determine dimensions
            var targetWidth = (videoQuality.targetWidth / 2) * 2
            var targetHeight = (videoQuality.targetHeight / 2) * 2

            if (scaleMode == ScaleMode.ORIGINAL && imageUris.isNotEmpty()) {
                val firstMeta = MediaHelper.queryImageItem(context, imageUris[0])
                if (firstMeta.width > 0 && firstMeta.height > 0) {
                    val aspect = firstMeta.width.toFloat() / firstMeta.height.toFloat()
                    var calcW = (videoQuality.targetHeight * aspect).toInt()
                    calcW = (calcW / 2) * 2
                    calcW = calcW.coerceIn(144, 1920)
                    targetWidth = calcW
                }
            }

            // Step 2: PROCESSING AUDIO
            onProgress(
                EncodingProgress(
                    step = EncodingStep.PROCESSING_AUDIO,
                    progress = 0.10f,
                    statusText = "Transcoding audio to AAC ${audioBitrate.kbpsText}…"
                )
            )

            val audioResult = AudioTranscoder.transcodeToAac(
                context = context,
                audioUri = audioUri,
                targetBitrate = audioBitrate.bps,
                outputFile = tempAudioFile,
                onProgress = { audioPct ->
                    val totalPct = 0.10f + (audioPct * 0.25f)
                    onProgress(
                        EncodingProgress(
                            step = EncodingStep.PROCESSING_AUDIO,
                            progress = totalPct,
                            statusText = "Converting audio (${(audioPct * 100).toInt()}%)…"
                        )
                    )
                },
                isCancelled = isCancelled
            )

            if (isCancelled()) throw InterruptedException("Cancelled")

            val totalDurationUs = audioResult.durationUs
            if (totalDurationUs <= 0) {
                throw IllegalStateException("Audio duration could not be determined")
            }

            // Step 3: PROCESSING IMAGES
            onProgress(
                EncodingProgress(
                    step = EncodingStep.PROCESSING_IMAGE,
                    progress = 0.38f,
                    statusText = "Preparing image frames…"
                )
            )

            // Calculate duration for each image
            val imageDurationsUs = mutableListOf<Long>()
            val numImages = imageUris.size

            when (slideDurationMode) {
                is SlideDurationMode.FitToAudio -> {
                    val perImage = totalDurationUs / numImages
                    for (i in 0 until numImages) {
                        if (i == numImages - 1) {
                            // last image takes remainder to reach exact end
                            val elapsed = perImage * (numImages - 1)
                            imageDurationsUs.add(totalDurationUs - elapsed)
                        } else {
                            imageDurationsUs.add(perImage)
                        }
                    }
                }
                is SlideDurationMode.FixedSeconds -> {
                    val fixedUs = slideDurationMode.seconds * 1_000_000L
                    for (i in 0 until numImages) {
                        imageDurationsUs.add(fixedUs)
                    }
                }
            }

            // Pre-render scaled bitmaps and YUV
            val colorFormat = selectColorFormat(MediaFormat.MIMETYPE_VIDEO_AVC)
            val yuvFrames = mutableListOf<ByteArray>()

            for (i in imageUris.indices) {
                if (isCancelled()) throw InterruptedException("Cancelled")
                val bitmap = MediaHelper.loadAndScaleBitmap(
                    context,
                    imageUris[i],
                    targetWidth,
                    targetHeight,
                    scaleMode
                ) ?: throw IllegalStateException("Failed to decode image #${i + 1}")

                val yuv = YuvConverter.convertBitmapToYuv(bitmap, targetWidth, targetHeight, colorFormat)
                bitmap.recycle()
                yuvFrames.add(yuv)
            }

            // Step 4: ENCODING VIDEO
            onProgress(
                EncodingProgress(
                    step = EncodingStep.ENCODING_VIDEO,
                    progress = 0.45f,
                    statusText = "Encoding MP4 video stream…"
                )
            )

            encodeVideoStream(
                outputFile = tempVideoFile,
                width = targetWidth,
                height = targetHeight,
                colorFormat = colorFormat,
                bitrate = videoQuality.defaultVideoBitrate,
                totalDurationUs = totalDurationUs,
                yuvFrames = yuvFrames,
                imageDurationsUs = imageDurationsUs,
                onProgress = { videoPct ->
                    val totalPct = 0.45f + (videoPct * 0.40f)
                    onProgress(
                        EncodingProgress(
                            step = EncodingStep.ENCODING_VIDEO,
                            progress = totalPct,
                            statusText = "Encoding video frames (${(videoPct * 100).toInt()}%)…"
                        )
                    )
                },
                isCancelled = isCancelled
            )

            if (isCancelled()) throw InterruptedException("Cancelled")

            // Step 5: FINALIZING & COMPRESSING (Mux video + audio together)
            onProgress(
                EncodingProgress(
                    step = EncodingStep.FINALIZING,
                    progress = 0.88f,
                    statusText = "Muxing video and audio tracks…"
                )
            )

            muxVideoAndAudio(
                videoFile = tempVideoFile,
                audioFile = tempAudioFile,
                outputFile = finalVideoFile,
                isCancelled = isCancelled
            )

            // Step 6: Save to selected destination
            onProgress(
                EncodingProgress(
                    step = EncodingStep.FINALIZING,
                    progress = 0.95f,
                    statusText = "Saving to destination…"
                )
            )

            val displayName = "Video_${timeStamp}.mp4"
            val (savedUri, locationText) = saveVideoToDestination(
                finalVideoFile,
                displayName,
                storageDestination
            )

            val finalSizeBytes = finalVideoFile.length()
            val finalDurationSec = totalDurationUs / 1_000_000L

            EncodingProgress(
                step = EncodingStep.COMPLETED,
                progress = 1.0f,
                statusText = "Video created successfully!",
                outputUri = savedUri,
                outputFile = finalVideoFile,
                fileSizeBytes = finalSizeBytes,
                durationSeconds = finalDurationSec,
                savedLocationText = locationText
            )
        } catch (e: InterruptedException) {
            EncodingProgress(
                step = EncodingStep.CANCELLED,
                errorMessage = "Operation cancelled by user."
            )
        } catch (e: Exception) {
            EncodingProgress(
                step = EncodingStep.ERROR,
                errorMessage = e.localizedMessage ?: "Failed to generate video. Please try again with lower resolution."
            )
        } finally {
            tempAudioFile.delete()
            tempVideoFile.delete()
        }
    }

    private fun encodeVideoStream(
        outputFile: File,
        width: Int,
        height: Int,
        colorFormat: Int,
        bitrate: Int,
        totalDurationUs: Long,
        yuvFrames: List<ByteArray>,
        imageDurationsUs: List<Long>,
        onProgress: (Float) -> Unit,
        isCancelled: () -> Boolean
    ) {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var muxerStarted = false

        val bufferInfo = MediaCodec.BufferInfo()
        val frameIntervalUs = 1_000_000L / FPS
        var currentPtsUs = 0L
        var inputDone = false
        var outputDone = false

        var currentImageIndex = 0
        var currentImageElapsedUs = 0L

        try {
            while (!outputDone) {
                if (isCancelled()) throw InterruptedException("Cancelled")

                // Feed input frames
                if (!inputDone) {
                    val inputBufIndex = encoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inputBufIndex >= 0) {
                        if (currentPtsUs >= totalDurationUs) {
                            encoder.queueInputBuffer(
                                inputBufIndex,
                                0,
                                0,
                                currentPtsUs,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            val inputBuf = encoder.getInputBuffer(inputBufIndex)
                            if (inputBuf != null) {
                                inputBuf.clear()

                                // Pick current image frame based on duration
                                val yuv = yuvFrames[currentImageIndex % yuvFrames.size]
                                inputBuf.put(yuv)

                                encoder.queueInputBuffer(
                                    inputBufIndex,
                                    0,
                                    yuv.size,
                                    currentPtsUs,
                                    0
                                )

                                currentPtsUs += frameIntervalUs
                                currentImageElapsedUs += frameIntervalUs

                                val targetDurationForThisImage = if (currentImageIndex < imageDurationsUs.size) {
                                    imageDurationsUs[currentImageIndex]
                                } else {
                                    imageDurationsUs.lastOrNull() ?: totalDurationUs
                                }

                                if (currentImageElapsedUs >= targetDurationForThisImage && currentImageIndex < yuvFrames.size - 1) {
                                    currentImageIndex++
                                    currentImageElapsedUs = 0L
                                }

                                val pct = (currentPtsUs.toFloat() / totalDurationUs.toFloat()).coerceIn(0f, 1f)
                                onProgress(pct)
                            }
                        }
                    }
                }

                // Drain output frames
                val outStatus = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                if (outStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (muxerStarted) throw IllegalStateException("Format changed twice")
                    trackIndex = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    muxerStarted = true
                } else if (outStatus >= 0) {
                    val encodedData = encoder.getOutputBuffer(outStatus)
                    if (encodedData != null) {
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            bufferInfo.size = 0
                        }

                        if (bufferInfo.size > 0 && muxerStarted) {
                            encodedData.position(bufferInfo.offset)
                            encodedData.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                        }
                    }

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true
                    }

                    encoder.releaseOutputBuffer(outStatus, false)
                }
            }
        } finally {
            try { encoder.stop() } catch (_: Exception) {}
            try { encoder.release() } catch (_: Exception) {}
            try {
                if (muxerStarted) muxer.stop()
                muxer.release()
            } catch (_: Exception) {}
        }
    }

    private fun muxVideoAndAudio(
        videoFile: File,
        audioFile: File,
        outputFile: File,
        isCancelled: () -> Boolean
    ) {
        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()
        var muxer: MediaMuxer? = null

        try {
            videoExtractor.setDataSource(videoFile.absolutePath)
            audioExtractor.setDataSource(audioFile.absolutePath)

            var videoTrackInExtractor = -1
            var audioTrackInExtractor = -1

            for (i in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoTrackInExtractor = i
                    break
                }
            }

            for (i in 0 until audioExtractor.trackCount) {
                val format = audioExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackInExtractor = i
                    break
                }
            }

            if (videoTrackInExtractor == -1) throw IllegalStateException("Video track missing")
            if (audioTrackInExtractor == -1) throw IllegalStateException("Audio track missing")

            videoExtractor.selectTrack(videoTrackInExtractor)
            audioExtractor.selectTrack(audioTrackInExtractor)

            val videoFormat = videoExtractor.getTrackFormat(videoTrackInExtractor)
            val audioFormat = audioExtractor.getTrackFormat(audioTrackInExtractor)

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerVideoTrack = muxer.addTrack(videoFormat)
            val muxerAudioTrack = muxer.addTrack(audioFormat)
            muxer.start()

            val bufferSize = 256 * 1024
            val buffer = ByteBuffer.allocateDirect(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            // Copy Video Track
            while (!isCancelled()) {
                bufferInfo.offset = 0
                bufferInfo.size = videoExtractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) break

                bufferInfo.presentationTimeUs = videoExtractor.sampleTime
                bufferInfo.flags = videoExtractor.sampleFlags
                muxer.writeSampleData(muxerVideoTrack, buffer, bufferInfo)
                videoExtractor.advance()
            }

            // Copy Audio Track
            while (!isCancelled()) {
                bufferInfo.offset = 0
                bufferInfo.size = audioExtractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) break

                bufferInfo.presentationTimeUs = audioExtractor.sampleTime
                bufferInfo.flags = audioExtractor.sampleFlags
                muxer.writeSampleData(muxerAudioTrack, buffer, bufferInfo)
                audioExtractor.advance()
            }

        } finally {
            try { videoExtractor.release() } catch (_: Exception) {}
            try { audioExtractor.release() } catch (_: Exception) {}
            try {
                muxer?.stop()
                muxer?.release()
            } catch (_: Exception) {}
        }
    }

    private fun saveVideoToDestination(
        file: File,
        displayName: String,
        destination: StorageDestination
    ): Pair<Uri?, String> {
        val resolver = context.contentResolver

        when (destination) {
            is StorageDestination.CustomFolder -> {
                try {
                    val treeDocId = DocumentsContract.getTreeDocumentId(destination.treeUri)
                    val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(destination.treeUri, treeDocId)
                    val newDocUri = DocumentsContract.createDocument(
                        resolver,
                        parentDocUri,
                        "video/mp4",
                        displayName
                    )
                    if (newDocUri != null) {
                        resolver.openOutputStream(newDocUri)?.use { out ->
                            FileInputStream(file).use { input -> input.copyTo(out) }
                        }
                        return Pair(newDocUri, destination.folderDisplayName)
                    }
                } catch (_: Exception) {}
            }
            is StorageDestination.SdCard -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && destination.volumeName != null) {
                    try {
                        val volumeUri = MediaStore.Video.Media.getContentUri(destination.volumeName)
                        val values = ContentValues().apply {
                            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ImageAudioVideo")
                            put(MediaStore.Video.Media.IS_PENDING, 1)
                        }
                        val uri = resolver.insert(volumeUri, values)
                        if (uri != null) {
                            resolver.openOutputStream(uri)?.use { out ->
                                FileInputStream(file).use { input -> input.copyTo(out) }
                            }
                            values.clear()
                            values.put(MediaStore.Video.Media.IS_PENDING, 0)
                            resolver.update(uri, values, null, null)
                            return Pair(uri, "SD Card: Movies/ImageAudioVideo")
                        }
                    } catch (_: Exception) {}
                }

                // Fallback to secondary external files dir
                val extDirs = context.getExternalFilesDirs(android.os.Environment.DIRECTORY_MOVIES)
                if (extDirs.size > 1 && extDirs[1] != null) {
                    try {
                        val sdMovieDir = File(extDirs[1], "ImageAudioVideo").apply { mkdirs() }
                        val targetFile = File(sdMovieDir, displayName)
                        FileInputStream(file).use { input ->
                            targetFile.outputStream().use { out -> input.copyTo(out) }
                        }
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            targetFile
                        )
                        return Pair(uri, "SD Card: Movies/ImageAudioVideo")
                    } catch (_: Exception) {}
                }
            }
            is StorageDestination.Internal -> {
                // Handled below
            }
        }

        // Standard Internal MediaStore save
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ImageAudioVideo")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(file).use { input -> input.copyTo(out) }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
                return Pair(uri, "Movies/ImageAudioVideo")
            } catch (_: Exception) {}
        }

        return Pair(null, "Movies/ImageAudioVideo")
    }

    private fun selectColorFormat(mimeType: String): Int {
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        for (info in codecList.codecInfos) {
            if (!info.isEncoder) continue
            val types = info.supportedTypes
            for (type in types) {
                if (type.equals(mimeType, ignoreCase = true)) {
                    val caps = info.getCapabilitiesForType(type)
                    // SemiPlanar (NV12) is most widely supported
                    if (caps.colorFormats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)) {
                        return MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
                    }
                    if (caps.colorFormats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar)) {
                        return MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
                    }
                    if (caps.colorFormats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)) {
                        return MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
                    }
                }
            }
        }
        return MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
    }
}
