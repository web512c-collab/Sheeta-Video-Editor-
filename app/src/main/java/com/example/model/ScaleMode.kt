package com.example.model

enum class ScaleMode(val displayName: String, val description: String) {
    FIT("Fit (Preserve Ratio)", "Adds letterbox/pillarbox bars, keeps full image intact"),
    CROP("Crop (Fill Screen)", "Fills entire frame without distortion, trims edges"),
    ORIGINAL("Original Dimensions", "Adapts video canvas dimensions to the image's aspect ratio");

    companion object {
        val DEFAULT = FIT
    }
}
