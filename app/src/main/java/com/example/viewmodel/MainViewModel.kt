package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.encoder.MediaHelper
import com.example.encoder.VideoAudioEncoder
import com.example.model.AudioBitrate
import com.example.model.AudioItem
import com.example.model.AudioPreset
import com.example.model.EncodingProgress
import com.example.model.EncodingStep
import com.example.model.ImageItem
import com.example.model.ScaleMode
import com.example.model.SlideDurationMode
import com.example.model.StorageDestination
import com.example.model.VideoQuality
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val images: List<ImageItem> = emptyList(),
    val audio: AudioItem? = null,
    val videoQuality: VideoQuality = VideoQuality.P144, // Default 144p as requested
    val audioPreset: AudioPreset = AudioPreset.STANDARD,
    val audioBitrate: AudioBitrate = AudioBitrate.KBPS_128,
    val scaleMode: ScaleMode = ScaleMode.FIT,
    val slideDurationMode: SlideDurationMode = SlideDurationMode.FitToAudio,
    val storageDestination: StorageDestination = StorageDestination.Internal,
    val availableSdCard: StorageDestination.SdCard? = null,
    val encodingProgress: EncodingProgress = EncodingProgress(),
    val estimatedSizeBytes: Long = 0L,
    val userMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val encoder = VideoAudioEncoder(context)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var encodingJob: Job? = null
    @Volatile
    private var isCancelledFlag = false

    init {
        // Detect SD Card availability
        viewModelScope.launch {
            val sdCard = MediaHelper.getSdCardDestination(context)
            _uiState.update { it.copy(availableSdCard = sdCard) }
        }
    }

    fun onSingleImageSelected(uri: Uri) {
        viewModelScope.launch {
            val item = MediaHelper.queryImageItem(context, uri)
            _uiState.update { it.copy(images = listOf(item)) }
            recalculateEstimatedSize()
        }
    }

    fun onMultipleImagesSelected(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val items = uris.map { MediaHelper.queryImageItem(context, it) }
            _uiState.update { it.copy(images = items) }
            recalculateEstimatedSize()
        }
    }

    fun onRemoveImage(index: Int) {
        _uiState.update { state ->
            val updated = state.images.toMutableList()
            if (index in updated.indices) {
                updated.removeAt(index)
            }
            state.copy(images = updated)
        }
        recalculateEstimatedSize()
    }

    fun onAudioSelected(uri: Uri) {
        viewModelScope.launch {
            val item = MediaHelper.queryAudioItem(context, uri)
            _uiState.update { it.copy(audio = item) }
            recalculateEstimatedSize()
        }
    }

    fun onVideoQualitySelected(quality: VideoQuality) {
        _uiState.update { it.copy(videoQuality = quality) }
        recalculateEstimatedSize()
    }

    fun onAudioPresetSelected(preset: AudioPreset) {
        _uiState.update {
            it.copy(
                audioPreset = preset,
                audioBitrate = preset.recommendedBitrate
            )
        }
        recalculateEstimatedSize()
    }

    fun onAudioBitrateSelected(bitrate: AudioBitrate) {
        _uiState.update { it.copy(audioBitrate = bitrate) }
        recalculateEstimatedSize()
    }

    fun onScaleModeSelected(mode: ScaleMode) {
        _uiState.update { it.copy(scaleMode = mode) }
    }

    fun onSlideDurationModeSelected(mode: SlideDurationMode) {
        _uiState.update { it.copy(slideDurationMode = mode) }
    }

    fun onStorageDestinationSelected(destination: StorageDestination) {
        _uiState.update { it.copy(storageDestination = destination) }
    }

    fun onCustomFolderSelected(treeUri: Uri) {
        val folderName = MediaHelper.getFolderDisplayName(context, treeUri)
        val destination = StorageDestination.CustomFolder(
            treeUri = treeUri,
            folderDisplayName = folderName
        )
        _uiState.update { it.copy(storageDestination = destination) }
    }

    private fun recalculateEstimatedSize() {
        val audio = _uiState.value.audio ?: return
        val durationMs = if (audio.durationMs > 0) audio.durationMs else 30_000L
        val videoBitrate = _uiState.value.videoQuality.defaultVideoBitrate
        val audioBitrate = _uiState.value.audioBitrate.bps
        val estimate = MediaHelper.estimateOutputSizeBytes(durationMs, videoBitrate, audioBitrate)
        _uiState.update { it.copy(estimatedSizeBytes = estimate) }
    }

    fun startEncoding() {
        val state = _uiState.value
        if (state.images.isEmpty() || state.audio == null) {
            _uiState.update {
                it.copy(userMessage = "Please select at least one image and an audio file")
            }
            return
        }

        isCancelledFlag = false
        encodingJob?.cancel()
        encodingJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    encodingProgress = EncodingProgress(
                        step = EncodingStep.PREPARING,
                        progress = 0.02f,
                        statusText = "Starting video engine…"
                    )
                )
            }

            val result = encoder.encode(
                imageUris = state.images.map { it.uri },
                audioUri = state.audio.uri,
                videoQuality = state.videoQuality,
                audioBitrate = state.audioBitrate,
                scaleMode = state.scaleMode,
                slideDurationMode = state.slideDurationMode,
                storageDestination = state.storageDestination,
                onProgress = { progress ->
                    _uiState.update { it.copy(encodingProgress = progress) }
                },
                isCancelled = { isCancelledFlag }
            )

            _uiState.update { it.copy(encodingProgress = result) }
        }
    }

    fun cancelEncoding() {
        isCancelledFlag = true
        encodingJob?.cancel()
        _uiState.update {
            it.copy(
                encodingProgress = EncodingProgress(
                    step = EncodingStep.CANCELLED,
                    statusText = "Cancelled"
                )
            )
        }
    }

    fun dismissUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    fun resetForNewVideo() {
        _uiState.update {
            it.copy(
                encodingProgress = EncodingProgress(step = EncodingStep.IDLE)
            )
        }
    }
}
