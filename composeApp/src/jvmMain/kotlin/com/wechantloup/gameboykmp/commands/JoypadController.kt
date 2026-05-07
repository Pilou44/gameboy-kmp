package com.wechantloup.gameboykmp.commands

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import com.wechantloup.gameboykmp.joypad.JoypadButton
import com.wechantloup.gameboykmp.joypad.JoypadEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

// Could be an object to remove JoypadControllerHolder.
// Kept as class for testability (use of mock)
class JoypadController {

    val commandsMap = CommandsMap()

    private val _buttonEvents = MutableSharedFlow<JoypadEvent>(extraBufferCapacity = 16)
    val buttonEvents: SharedFlow<JoypadEvent> = _buttonEvents // Why flow and not channel

    fun handleKeyEvent(key: Key, type: KeyEventType): Boolean {
        val button = commandsMap.keyboardCommands[key] ?: return false
        val event = when (type) {
            KeyEventType.KeyDown -> JoypadEvent.Pressed(button)
            KeyEventType.KeyUp   -> JoypadEvent.Released(button)
            else                 -> return false
        }
        _buttonEvents.tryEmit(event)
        return true
    }

    fun handleGamepadEvent(buttonIndex: Int, pressed: Boolean) {
        val button = commandsMap.joypadCommands[buttonIndex] ?: return
        val event = if (pressed) JoypadEvent.Pressed(button) else JoypadEvent.Released(button)
        _buttonEvents.tryEmit(event)
    }

    fun remapKeyboard(key: Key, button: JoypadButton) {
        commandsMap.keyboardCommands.entries.removeIf { it.value == button }
        commandsMap.keyboardCommands[key] = button
    }
}
