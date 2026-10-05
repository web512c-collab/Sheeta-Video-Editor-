package com.example.model

enum class VideoQuality(
    val id: String,
    val displayName: String,
    val targetWidth: Int,
    val targetHeight: Int,
    val defaultVideoBitrate: Int, // bps
    val badge: String
) {
    P144(
        id = "144p",
        displayName = "144p",
        targetWidth = 256,
        targetHeight = 144,
        defaultVideoBitrate = 180_000,
        badge = "Ultra Light"
    ),
    P240(
        id = "240p",
        displayName = "240p",
        targetWidth = 426,
        targetHeight = 240,
        defaultVideoBitrate = 350_000,
        badge = "Low"
    ),
    P360(
        id = "360p",
        displayName = "360p",
        targetWidth = 640,
        targetHeight = 360,
        defaultVideoBitrate = 700_000,
        badge = "Medium"
    ),
    P480(
        id = "480p",
        displayName = "480p",
        targetWidth = 854,
        targetHeight = 480,
        defaultVideoBitrate = 1_200_000,
        badge = "SD"
    ),
    P720(
        id = "720p",
        displayName = "720p HD",
        targetWidth = 1280,
        targetHeight = 720,
        defaultVideoBitrate = 2_500_000,
        badge = "HD"
    ),
    P1080(
        id = "1080p",
        displayName = "1080p Full HD",
        targetWidth = 1920,
        targetHeight = 1080,
        defaultVideoBitrate = 4_500_000,
        badge = "FHD"
    );

    companion object {
        val DEFAULT = P144 // Default is 144p as requested
    }
}
