package com.wechantloup.gameboykmp.commands

import androidx.compose.ui.input.key.Key
import com.wechantloup.gameboykmp.joypad.JoypadButton

data class CommandsMap(
    val keyboardCommands: Map<Key, JoypadButton> = hashMapOf(),
    val joypadCommands: Map<Int, JoypadButton> = hashMapOf()
)
