package com.wechantloup.gameboykmp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.wechantloup.gameboykmp.commands.CommandsMap
import com.wechantloup.gameboykmp.joypad.JoypadButton
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun SetCommandsTable(
    commandsState: StateFlow<CommandsMap>,
    registerKeyboard: (JoypadButton) -> Unit, // ToDo
    modifier: Modifier = Modifier,
) {
    val commands by commandsState.collectAsState()

    Row(
        modifier = modifier.height(IntrinsicSize.Max),
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
        ) {
            Text(text = "Game Boy buttton")
            JoypadButton.entries.forEach {
                Text(text = it.name)
            }
        }
        Column(
            modifier = Modifier.fillMaxHeight(),
        ) {
            Text(text = "Keyboard key")
            JoypadButton.entries.forEach { padEntry ->
                val key = commands.keyboardCommands.filterValues { it == padEntry }.toList().firstOrNull()
                Text(
                    text = "$key",
                    modifier = Modifier.clickable { registerKeyboard(padEntry) }
                )
            }
        }
        Column(
            modifier = Modifier.fillMaxHeight(),
        ) {
            Text(text = "Joypad button")
            JoypadButton.entries.forEach { padEntry ->
                val key = commands.joypadCommands.filterValues { it == padEntry }.toList().firstOrNull()
                Text(text = "$key")
            }
        }
    }
}
