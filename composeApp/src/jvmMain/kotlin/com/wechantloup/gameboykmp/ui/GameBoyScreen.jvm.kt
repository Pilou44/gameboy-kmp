package com.wechantloup.gameboykmp.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

actual fun intArrayToImageBitmap(pixels: IntArray, width: Int, height: Int): ImageBitmap {
    val byteArray = ByteArray(pixels.size * 4)
    pixels.forEachIndexed { index, pixel ->
        byteArray[index * 4] = ((pixel shr 16) and 0xFF).toByte()
        byteArray[index * 4 + 1] = ((pixel shr 8) and 0xFF).toByte()
        byteArray[index * 4 + 2] = (pixel and 0xFF).toByte()
        byteArray[index * 4 + 3] = ((pixel shr 24) and 0xFF).toByte()
    }

    val imageInfo = ImageInfo(
        width = width,
        height = height,
        colorType = ColorType.RGBA_8888,
        alphaType = ColorAlphaType.OPAQUE,
    )
    val bitmap = Bitmap()
    bitmap.installPixels(imageInfo, byteArray, width * 4)

    return bitmap.asComposeImageBitmap()
}
