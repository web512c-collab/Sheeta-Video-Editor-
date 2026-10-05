package com.example.model

sealed class SlideDurationMode {
    object FitToAudio : SlideDurationMode() {
        override fun toString(): String = "Fit to Audio"
    }

    data class FixedSeconds(val seconds: Int) : SlideDurationMode() {
        override fun toString(): String = "$seconds sec"
    }

    companion object {
        val AVAILABLE_FIXED = listOf(1, 2, 3, 5, 10)
    }
}
