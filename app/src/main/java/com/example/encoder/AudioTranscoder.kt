package com.example.encoder

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer

object AudioTranscoder {

    const val TIMEOUT_US = 10_000L

    data class TranscodeResult(
        val audioFile: File,
        val durationUs: Long,
        val sampleRate: Int,
        val channelCount: Int
    )

    /**
     * Transcodes any supported audio file to an AAC M4A file with the specified bitrate.
     * Reports progress via the onProgress callback.
     */
    fun transcodeToAac(
        context: Context,
        audioUri: Uri,
        targetBitrate: Int,
        outputFile: File,
        onProgress: (Float) -> Unit,
        isCancelled: () -> Boolean
    ): TranscodeResult {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null

        try {
            extractor.setDataSource(context, audioUri, null)
            var audioTrackIndex = -1
            var inputFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    inputFormat = format
                    break
                }
            }

            if (audioTrackIndex == -1 || inputFormat == null) {
                throw IllegalStateException("No valid audio track found in selected file")
            }

            extractor.selectTrack(audioTrackIndex)

            val inputMime = inputFormat.getString(MediaFormat.KEY_MIME) ?: ""
            val sampleRate = if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else {
                44100
            }
            val channelCount = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else {
                2
            }
            val totalDurationUs = if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) {
                inputFormat.getLong(MediaFormat.KEY_DURATION)
            } else {
                0L
            }

            // 1. Setup Decoder
            decoder = MediaCodec.createDecoderByType(inputMime)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            // 2. Setup Encoder (AAC LC)
            val encoderFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                sampleRate,
                channelCount
            ).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, targetBitrate)
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            encoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            // 3. Setup Muxer
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var muxerTrackIndex = -1
            var muxerStarted = false

            val decoderBufferInfo = MediaCodec.BufferInfo()
            val encoderBufferInfo = MediaCodec.BufferInfo()

            var extractorDone = false
            var decoderDone = false
            var encoderDone = false

            var lastPresentationTimeUs = 0L

            while (!encoderDone) {
                if (isCancelled()) {
                    throw InterruptedException("Audio transcoding cancelled")
                }

                // A. Feed Extractor into Decoder
                if (!extractorDone) {
                    val inputBufIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inputBufIndex >= 0) {
                        val inputBuf = decoder.getInputBuffer(inputBufIndex) ?: ByteBuffer.allocate(0)
                        val sampleSize = extractor.readSampleData(inputBuf, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(
                                inputBufIndex,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            extractorDone = true
                        } else {
                            val presentationTimeUs = extractor.sampleTime
                            decoder.queueInputBuffer(
                                inputBufIndex,
                                0,
                                sampleSize,
                                presentationTimeUs,
                                0
                            )
                            extractor.advance()
                        }
                    }
                }

                // B. Drain Decoder -> Feed into Encoder
                if (!decoderDone) {
                    val decStatus = decoder.dequeueOutputBuffer(decoderBufferInfo, TIMEOUT_US)
                    if (decStatus >= 0) {
                        val pcmBuffer = decoder.getOutputBuffer(decStatus)
                        val isEos = (decoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0

                        if (pcmBuffer != null && decoderBufferInfo.size > 0) {
                            // Feed into encoder
                            var fed = false
                            while (!fed && !isCancelled()) {
                                val encInputIndex = encoder.dequeueInputBuffer(TIMEOUT_US)
                                if (encInputIndex >= 0) {
                                    val encInputBuf = encoder.getInputBuffer(encInputIndex)
                                    if (encInputBuf != null) {
                                        encInputBuf.clear()
                                        pcmBuffer.position(decoderBufferInfo.offset)
                                        pcmBuffer.limit(decoderBufferInfo.offset + decoderBufferInfo.size)
                                        encInputBuf.put(pcmBuffer)
                                    }
                                    encoder.queueInputBuffer(
                                        encInputIndex,
                                        0,
                                        decoderBufferInfo.size,
                                        decoderBufferInfo.presentationTimeUs,
                                        if (isEos) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                                    )
                                    fed = true
                                }
                            }
                        } else if (isEos) {
                            // Queue EOS to encoder
                            val encInputIndex = encoder.dequeueInputBuffer(TIMEOUT_US)
                            if (encInputIndex >= 0) {
                                encoder.queueInputBuffer(
                                    encInputIndex,
                                    0,
                                    0,
                                    decoderBufferInfo.presentationTimeUs,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                            }
                        }

                        decoder.releaseOutputBuffer(decStatus, false)
                        if (isEos) {
                            decoderDone = true
                        }
                    }
                }

                // C. Drain Encoder -> Write to Muxer
                val encStatus = encoder.dequeueOutputBuffer(encoderBufferInfo, TIMEOUT_US)
                if (encStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (muxerStarted) {
                        throw IllegalStateException("Audio format changed twice")
                    }
                    val newFormat = encoder.outputFormat
                    muxerTrackIndex = muxer.addTrack(newFormat)
                    muxer.start()
                    muxerStarted = true
                } else if (encStatus >= 0) {
                    val encodedBuffer = encoder.getOutputBuffer(encStatus)
                    if (encodedBuffer != null) {
                        if ((encoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            encoderBufferInfo.size = 0
                        }

                        if (encoderBufferInfo.size > 0 && muxerStarted) {
                            encodedBuffer.position(encoderBufferInfo.offset)
                            encodedBuffer.limit(encoderBufferInfo.offset + encoderBufferInfo.size)
                            muxer.writeSampleData(muxerTrackIndex, encodedBuffer, encoderBufferInfo)
                            lastPresentationTimeUs = encoderBufferInfo.presentationTimeUs

                            if (totalDurationUs > 0) {
                                val progress = (lastPresentationTimeUs.toFloat() / totalDurationUs.toFloat())
                                    .coerceIn(0f, 1f)
                                onProgress(progress)
                            }
                        }
                    }

                    if ((encoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        encoderDone = true
                    }
                    encoder.releaseOutputBuffer(encStatus, false)
                }
            }

            val finalDurationUs = if (lastPresentationTimeUs > 0) lastPresentationTimeUs else totalDurationUs

            return TranscodeResult(
                audioFile = outputFile,
                durationUs = finalDurationUs,
                sampleRate = sampleRate,
                channelCount = channelCount
            )
        } finally {
            try { decoder?.stop() } catch (_: Exception) {}
            try { decoder?.release() } catch (_: Exception) {}
            try { encoder?.stop() } catch (_: Exception) {}
            try { encoder?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
            try {
                muxer?.stop()
                muxer?.release()
            } catch (_: Exception) {}
        }
    }
}
