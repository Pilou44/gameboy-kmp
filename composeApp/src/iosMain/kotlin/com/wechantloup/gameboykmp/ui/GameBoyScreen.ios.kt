package com.wechantloup.gameboykmp.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
actual fun intArrayToImageBitmap(pixels: IntArray, width: Int, height: Int): ImageBitmap {
    val byteArray = ByteArray(pixels.size * 4)

    pixels.usePinned { src ->
        byteArray.usePinned { dst ->
            memcpy(dst.addressOf(0), src.addressOf(0), (pixels.size * 4).toULong())
        }
    }

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
