package com.example.model

import android.net.Uri

data class ImageItem(
    val uri: Uri,
    val name: String,
    val width: Int = 0,
    val height: Int = 0,
    val sizeBytes: Long = 0L,
    val mimeType: String = "image/*"
)

data class AudioItem(
    val uri: Uri,
    val name: String,
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val mimeType: String = "audio/*",
    val bitrate: Int = 0,
    val sampleRate: Int = 44100,
    val channelCount: Int = 2
)
