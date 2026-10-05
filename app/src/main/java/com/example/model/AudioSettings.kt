package com.example.model

enum class AudioPreset(val displayName: String, val recommendedBitrate: AudioBitrate) {
    HIGH_QUALITY("High Quality", AudioBitrate.KBPS_320),
    STANDARD("Standard", AudioBitrate.KBPS_128),
    COMPRESSED("Compressed", AudioBitrate.KBPS_64)
}

enum class AudioBitrate(val bps: Int, val kbpsText: String, val description: String) {
    KBPS_64(64_000, "64 kbps", "Maximum compression, smallest file"),
    KBPS_96(96_000, "96 kbps", "Good balance for voice / podcasts"),
    KBPS_128(128_000, "128 kbps", "Standard music streaming quality"),
    KBPS_192(192_000, "192 kbps", "High fidelity stereo"),
    KBPS_256(256_000, "256 kbps", "Very high quality"),
    KBPS_320(320_000, "320 kbps", "Studio master quality");

    companion object {
        val DEFAULT = KBPS_128
    }
}
