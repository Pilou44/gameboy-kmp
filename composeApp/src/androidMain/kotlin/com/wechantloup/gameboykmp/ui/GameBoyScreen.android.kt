package com.wechantloup.gameboykmp.ui

import android.graphics.Bitmap
import android.graphics.Bitmap.createBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

actual fun intArrayToImageBitmap(pixels: IntArray, width: Int, height: Int): ImageBitmap {
    val bitmap = createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    return bitmap.asImageBitmap()
}
