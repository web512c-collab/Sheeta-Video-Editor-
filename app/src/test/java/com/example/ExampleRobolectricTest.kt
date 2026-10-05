package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.encoder.MediaHelper
import com.example.model.AudioBitrate
import com.example.model.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Image Audio Video Maker", appName)
    }

    @Test
    fun `test formatDuration`() {
        assertEquals("01:05", MediaHelper.formatDuration(65_000L))
        assertEquals("00:00", MediaHelper.formatDuration(0L))
        assertEquals("05:30", MediaHelper.formatDuration(330_000L))
    }

    @Test
    fun `test estimateOutputSizeBytes`() {
        val estimate = MediaHelper.estimateOutputSizeBytes(
            durationMs = 60_000L,
            videoBitrate = VideoQuality.P144.defaultVideoBitrate,
            audioBitrate = AudioBitrate.KBPS_128.bps
        )
        assertTrue(estimate > 0)
        // 144p bitrate 180kbps + 128kbps audio = 308 kbps * 60s / 8 = ~2.3 MB
        val mb = estimate / (1024.0 * 1024.0)
        assertTrue(mb in 1.5..3.5)
    }
}
