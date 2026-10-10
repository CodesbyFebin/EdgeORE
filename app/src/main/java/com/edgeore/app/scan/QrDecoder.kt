package com.edgeore.app.scan

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader

/**
 * QR-only decoding with zxing-core (pure Java, no Play Services, no network, no model download).
 * The same code path decodes camera frames (luminance plane) and picked images (ARGB pixels).
 */
object QrDecoder {
    private val hints = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.CHARACTER_SET to "UTF-8",
    )

    fun decodeArgb(pixels: IntArray, width: Int, height: Int): String? =
        decode(RGBLuminanceSource(width, height, pixels))

    /** [data] is a Y (luminance) plane with [rowStride] bytes per row, as delivered by an ImageReader YUV_420_888 frame. */
    fun decodeLuminance(data: ByteArray, rowStride: Int, width: Int, height: Int): String? =
        decode(PlanarYUVLuminanceSource(data, rowStride, height, 0, 0, width, height, false))

    private fun decode(source: LuminanceSource): String? {
        val reader = QRCodeReader()
        return try {
            reader.decode(BinaryBitmap(HybridBinarizer(source)), hints).text
        } catch (_: NotFoundException) {
            null
        } catch (_: com.google.zxing.ReaderException) {
            null
        } finally {
            reader.reset()
        }
    }
}
