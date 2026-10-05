package com.example.encoder

import android.graphics.Bitmap
import android.media.MediaCodecInfo

object YuvConverter {

    /**
     * Converts an ARGB Bitmap into YUV420 format (NV12 or I420)
     */
    fun convertBitmapToYuv(
        bitmap: Bitmap,
        width: Int,
        height: Int,
        colorFormat: Int
    ): ByteArray {
        val yuv = ByteArray(width * height * 3 / 2)
        val argb = IntArray(width * height)
        bitmap.getPixels(argb, 0, width, 0, 0, width, height)

        val frameSize = width * height
        val isNv12 = colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar ||
                colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible

        var yIndex = 0
        var uIndex = frameSize
        var vIndex = frameSize + (frameSize / 4)

        for (j in 0 until height) {
            for (i in 0 until width) {
                val pixel = argb[j * width + i]
                val r = (pixel shr 16) and 0xff
                val g = (pixel shr 8) and 0xff
                val b = pixel and 0xff

                // ITU-R BT.601 conversion
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yuv[yIndex++] = y.coerceIn(16, 235).toByte()

                if (j % 2 == 0 && i % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128

                    if (isNv12) {
                        // NV12: interleaved UV
                        yuv[uIndex++] = u.coerceIn(16, 240).toByte()
                        yuv[uIndex++] = v.coerceIn(16, 240).toByte()
                    } else {
                        // I420: Planar Y, then U plane, then V plane
                        yuv[uIndex++] = u.coerceIn(16, 240).toByte()
                        yuv[vIndex++] = v.coerceIn(16, 240).toByte()
                    }
                }
            }
        }

        return yuv
    }
}
