package com.wechantloup.gameboykmp

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "GameBoyKMP",
    ) {
        App()
    }
}