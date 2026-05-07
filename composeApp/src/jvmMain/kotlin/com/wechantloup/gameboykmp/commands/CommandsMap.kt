package com.wechantloup.gameboykmp.commands

import androidx.compose.ui.input.key.Key
import com.wechantloup.gameboykmp.joypad.JoypadButton
import com.wechantloup.gameboykmp.utils.KeySerializer
import kotlinx.serialization.Serializable

@Serializable
data class CommandsMap(
    val keyboardCommands: Map<@Serializable(with = KeySerializer::class) Key, JoypadButton> = hashMapOf(),
    val joypadCommands: Map<Int, JoypadButton> = hashMapOf()
)
