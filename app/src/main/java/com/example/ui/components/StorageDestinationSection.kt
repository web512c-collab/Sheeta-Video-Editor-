package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.StorageDestination

@Composable
fun StorageDestinationSection(
    selectedDestination: StorageDestination,
    availableSdCard: StorageDestination.SdCard?,
    onDestinationSelected: (StorageDestination) -> Unit,
    onCustomFolderSelected: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val treeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {}
            onCustomFolderSelected(it)
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("storage_destination_section"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = stringResource(R.string.section_storage_location),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // 1. Internal Storage Option
            val isInternal = selectedDestination is StorageDestination.Internal
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("storage_option_internal")
                    .clickable { onDestinationSelected(StorageDestination.Internal) },
                shape = RoundedCornerShape(12.dp),
                color = if (isInternal) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    if (isInternal) 2.dp else 1.dp,
                    if (isInternal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = if (isInternal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.storage_internal),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isInternal) FontWeight.Bold else FontWeight.Medium,
                            color = if (isInternal) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.storage_internal_desc),
                            fontSize = 11.sp,
                            color = if (isInternal) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 2. SD Card Option
            val isSdSelected = selectedDestination is StorageDestination.SdCard
            val sdTarget = availableSdCard ?: StorageDestination.SdCard(null, "SD Card")
            val isSdDetected = availableSdCard != null

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("storage_option_sd")
                    .clickable {
                        if (isSdDetected) {
                            onDestinationSelected(sdTarget)
                        } else {
                            // Prompt to pick SD card folder directly
                            treeLauncher.launch(null)
                        }
                    },
                shape = RoundedCornerShape(12.dp),
                color = if (isSdSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    if (isSdSelected) 2.dp else 1.dp,
                    if (isSdSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.SdCard,
                        contentDescription = null,
                        tint = if (isSdSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isSdDetected) stringResource(R.string.storage_sd_card) else stringResource(R.string.storage_sd_not_detected),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSdSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSdSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isSdDetected) stringResource(R.string.storage_sd_card_desc) else stringResource(R.string.storage_custom_desc),
                            fontSize = 11.sp,
                            color = if (isSdSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 3. Custom Folder Picker Option
            val isCustom = selectedDestination is StorageDestination.CustomFolder
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("storage_option_custom")
                    .clickable { treeLauncher.launch(null) },
                shape = RoundedCornerShape(12.dp),
                color = if (isCustom) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    if (isCustom) 2.dp else 1.dp,
                    if (isCustom) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CreateNewFolder,
                        contentDescription = null,
                        tint = if (isCustom) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isCustom) {
                                (selectedDestination as StorageDestination.CustomFolder).folderDisplayName
                            } else {
                                stringResource(R.string.storage_custom)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isCustom) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (isCustom) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.storage_custom_desc),
                            fontSize = 11.sp,
                            color = if (isCustom) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
