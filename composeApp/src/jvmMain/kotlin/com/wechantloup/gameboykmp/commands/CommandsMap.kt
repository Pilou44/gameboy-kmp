package com.wechantloup.gameboykmp.commands

import androidx.compose.ui.input.key.Key
import com.wechantloup.gameboykmp.joypad.JoypadButton

data class CommandsMap(
    val keyboardCommands: MutableMap<Key, JoypadButton> = mutableMapOf(),
    val joypadCommands: MutableMap<Int, JoypadButton> = mutableMapOf()
)
