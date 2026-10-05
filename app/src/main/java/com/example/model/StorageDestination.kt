package com.example.model

import android.net.Uri

sealed class StorageDestination {
    object Internal : StorageDestination() {
        override fun toString(): String = "Internal Storage"
    }

    data class SdCard(
        val volumeName: String? = null,
        val displayName: String = "SD Card"
    ) : StorageDestination() {
        override fun toString(): String = displayName
    }

    data class CustomFolder(
        val treeUri: Uri,
        val folderDisplayName: String
    ) : StorageDestination() {
        override fun toString(): String = folderDisplayName
    }
}
