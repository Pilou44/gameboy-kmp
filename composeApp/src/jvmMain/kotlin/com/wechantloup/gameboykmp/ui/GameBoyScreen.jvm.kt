package com.wechantloup.gameboykmp.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import java.nio.ByteBuffer
import java.nio.ByteOrder

actual fun intArrayToImageBitmap(pixels: IntArray, width: Int, height: Int): ImageBitmap {
    val byteArray = ByteArray(pixels.size * 4)

    ByteBuffer.wrap(byteArray)
        .order(ByteOrder.LITTLE_ENDIAN) // ARGB int → BGRA bytes
        .asIntBuffer()
        .put(pixels)

    val imageInfo = ImageInfo(
        width = width,
        height = height,
        colorType = ColorType.BGRA_8888,
        alphaType = ColorAlphaType.OPAQUE,
    )
    val bitmap = Bitmap()
    bitmap.installPixels(imageInfo, byteArray, width * 4)

    return bitmap.asComposeImageBitmap()
}
