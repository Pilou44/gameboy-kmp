package com.wechantloup.gameboykmp.commands

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import com.wechantloup.gameboykmp.joypad.JoypadButton
import com.wechantloup.gameboykmp.joypad.JoypadEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Could be an object to remove JoypadControllerHolder.
// Kept as class for testability (use of mock)
class JoypadController {

    private val _commandsMap = MutableStateFlow(CommandsMap())
    val commandsMapFlow: StateFlow<CommandsMap> = _commandsMap
    private val commandsMap: CommandsMap get() = _commandsMap.value

    val buttonChannel = Channel<JoypadEvent>(Channel.UNLIMITED)

    fun handleKeyEvent(key: Key, type: KeyEventType): Boolean {
        val button = commandsMap.keyboardCommands[key] ?: return false
        val event = when (type) {
            KeyEventType.KeyDown -> JoypadEvent.Pressed(button)
            KeyEventType.KeyUp   -> JoypadEvent.Released(button)
            else                 -> return false
        }
        buttonChannel.trySend(event)
        return true
    }

    fun handleGamepadEvent(buttonIndex: Int, pressed: Boolean) {
        val button = commandsMap.joypadCommands[buttonIndex] ?: return
        val event = if (pressed) JoypadEvent.Pressed(button) else JoypadEvent.Released(button)
        buttonChannel.trySend(event)
    }

    fun remapKeyboard(key: Key, button: JoypadButton) {
        val current = _commandsMap.value
        val commands = current.keyboardCommands.toMutableMap()
        commands.entries.removeIf { it.value == button }
        commands[key] = button
        _commandsMap.value = current.copy(keyboardCommands =  commands)
    }

    fun remapGamepad(buttonIndex: Int, button: JoypadButton) {
        val current = _commandsMap.value
        val commands = current.joypadCommands.toMutableMap()
        commands.entries.removeIf { it.value == button }
        commands[buttonIndex] = button
        _commandsMap.value = current.copy(joypadCommands =  commands)
    }

    fun putCommands(commandsMap: CommandsMap) {
        _commandsMap.value = commandsMap
    }
}
