package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.EncodingStep
import com.example.ui.components.AspectRatioSection
import com.example.ui.components.AudioCompressionSection
import com.example.ui.components.EncodingProgressDialog
import com.example.ui.components.EstimatedSizeCard
import com.example.ui.components.MediaPickerSection
import com.example.ui.components.QualitySelectorSection
import com.example.ui.components.SlideshowSettingsSection
import com.example.ui.components.StorageDestinationSection
import com.example.ui.components.SuccessResultCard
import com.example.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissUserMessage()
        }
    }

    val hasRequiredMedia = uiState.images.isNotEmpty() && uiState.audio != null

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Movie,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("main_scroll_column"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Intro Subtitle
            item {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.app_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }

            // Error Banner (if error occurred during encoding)
            if (uiState.encodingProgress.step == EncodingStep.ERROR) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("error_card"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = uiState.encodingProgress.errorMessage
                                    ?: stringResource(R.string.error_encoding_generic),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // Success Result Card (when completed)
            if (uiState.encodingProgress.step == EncodingStep.COMPLETED) {
                item {
                    SuccessResultCard(
                        progress = uiState.encodingProgress,
                        onReset = { viewModel.resetForNewVideo() }
                    )
                }
            }

            // 1. Media Picker Section (Images & Audio)
            item {
                MediaPickerSection(
                    images = uiState.images,
                    audio = uiState.audio,
                    onSingleImageSelected = { viewModel.onSingleImageSelected(it) },
                    onMultipleImagesSelected = { viewModel.onMultipleImagesSelected(it) },
                    onRemoveImage = { viewModel.onRemoveImage(it) },
                    onAudioSelected = { viewModel.onAudioSelected(it) }
                )
            }

            // 2. Video Quality (Resolution) Section
            item {
                QualitySelectorSection(
                    selectedQuality = uiState.videoQuality,
                    onQualitySelected = { viewModel.onVideoQualitySelected(it) }
                )
            }

            // 3. Audio Quality & Compression Section
            item {
                AudioCompressionSection(
                    selectedPreset = uiState.audioPreset,
                    selectedBitrate = uiState.audioBitrate,
                    onPresetSelected = { viewModel.onAudioPresetSelected(it) },
                    onBitrateSelected = { viewModel.onAudioBitrateSelected(it) }
                )
            }

            // 4. Aspect Ratio & Fit Section
            item {
                AspectRatioSection(
                    selectedMode = uiState.scaleMode,
                    onModeSelected = { viewModel.onScaleModeSelected(it) }
                )
            }

            // 5. Slideshow Duration Section (Visible if > 1 image)
            if (uiState.images.size > 1) {
                item {
                    SlideshowSettingsSection(
                        imageCount = uiState.images.size,
                        selectedMode = uiState.slideDurationMode,
                        onModeSelected = { viewModel.onSlideDurationModeSelected(it) }
                    )
                }
            }

            // 6. Save Location (Storage) Section
            item {
                StorageDestinationSection(
                    selectedDestination = uiState.storageDestination,
                    availableSdCard = uiState.availableSdCard,
                    onDestinationSelected = { viewModel.onStorageDestinationSelected(it) },
                    onCustomFolderSelected = { viewModel.onCustomFolderSelected(it) }
                )
            }

            // 7. Estimated Size & Create Video Action Card
            item {
                EstimatedSizeCard(
                    hasMedia = hasRequiredMedia,
                    estimatedSizeBytes = uiState.estimatedSizeBytes,
                    quality = uiState.videoQuality,
                    audioBitrate = uiState.audioBitrate,
                    imageCount = uiState.images.size,
                    audioDurationMs = uiState.audio?.durationMs ?: 0L,
                    onStartEncoding = { viewModel.startEncoding() }
                )
            }

            // Bottom Spacing for insets
            item {
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // Modal Progress Dialog during Encoding
    EncodingProgressDialog(
        progress = uiState.encodingProgress,
        onCancel = { viewModel.cancelEncoding() }
    )
}
