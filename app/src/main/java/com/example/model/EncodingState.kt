package com.example.model

import android.net.Uri
import java.io.File

enum class EncodingStep {
    IDLE,
    PREPARING,
    PROCESSING_IMAGE,
    PROCESSING_AUDIO,
    ENCODING_VIDEO,
    COMPRESSING,
    FINALIZING,
    COMPLETED,
    ERROR,
    CANCELLED
}

data class EncodingProgress(
    val step: EncodingStep = EncodingStep.IDLE,
    val progress: Float = 0f, // 0.0 to 1.0
    val statusText: String = "",
    val outputUri: Uri? = null,
    val outputFile: File? = null,
    val fileSizeBytes: Long = 0L,
    val durationSeconds: Long = 0L,
    val savedLocationText: String = "",
    val errorMessage: String? = null
)
