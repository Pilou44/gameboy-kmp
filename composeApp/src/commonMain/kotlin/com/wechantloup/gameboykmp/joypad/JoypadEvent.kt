package com.wechantloup.gameboykmp.joypad

sealed interface JoypadEvent {
    data class Pressed(val button: JoypadButton) : JoypadEvent
    data class Released(val button: JoypadButton) : JoypadEvent
}
