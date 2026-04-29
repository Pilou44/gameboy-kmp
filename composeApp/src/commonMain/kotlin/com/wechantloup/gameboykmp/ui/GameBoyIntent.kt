package com.wechantloup.gameboykmp.ui

import com.wechantloup.gameboykmp.joypad.JoypadButton

sealed class GameBoyIntent {
    data class ButtonPressed(val button: JoypadButton) : GameBoyIntent()
    data class ButtonReleased(val button: JoypadButton) : GameBoyIntent()
}
