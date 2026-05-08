package com.wechantloup.gameboykmp.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.wechantloup.gameboykmp.commands.CommandsMap
import com.wechantloup.gameboykmp.commands.JoypadController
import com.wechantloup.gameboykmp.commands.JoypadControllerHolder
import com.wechantloup.gameboykmp.joypad.JoypadButton
import com.wechantloup.gameboykmp.repositories.JvmSettingsRepository
import com.wechantloup.gameboykmp.ui.dialog.ClosedDialogState
import com.wechantloup.gameboykmp.ui.dialog.OpenedDialogState
import gameboykmp.composeapp.generated.resources.Res
import gameboykmp.composeapp.generated.resources.ok_btn_label
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.reflect.KClass

class MainViewModel(
    private val joypadController: JoypadController,
    private val settingsRepository: JvmSettingsRepository,
): ViewModel() {
    private val _stateFlow = MutableStateFlow(MainState())
    val stateFlow: StateFlow<MainState> = _stateFlow

    private var waitForKeyboardEvent: JoypadButton? = null
    private var waitForGamepadEvent: JoypadButton? = null

    init {
        val commandsMap = settingsRepository.commandsMap ?: getDefaultCommands()
        joypadController.putCommands(commandsMap)
    }

    fun onIntent(intent: MainIntent) {
        when (intent) {
            MainIntent.ShowCommandsTable -> showCommandsDialog()
        }
    }

    fun onKeyEvent(event: KeyEvent): Boolean {
        val waitKeyboardEvent = waitForKeyboardEvent ?: return false

        when (event.type) {
            KeyEventType.KeyDown -> {
                joypadController.remapKeyboard(event.key, waitKeyboardEvent)
                waitForKeyboardEvent = null
            }
            else -> {}
        }
        return true
    }

    fun onGamepadEvent(buttonIndex: Int, pressed: Boolean) {
        if (!pressed) return
        val waitGamepadEvent = waitForGamepadEvent ?: return

        joypadController.remapGamepad(buttonIndex, waitGamepadEvent)
        waitForGamepadEvent = null
    }

    fun catchAllInputs(): Boolean {
        return stateFlow.value.dialog is OpenedDialogState
    }

    private fun showCommandsDialog() {
        val newState = OpenedDialogState(
            onDismiss = ::closeDialog,
            body = {
                SetCommandsTable(
                    commandsState = joypadController.commandsMapFlow,
                    registerKeyboard = ::registerKeyboard,
                    registerGamepad = ::registerGamepad,
                )
            },
            confirmButtonTextRes = Res.string.ok_btn_label,
            onConfirmButtonClicked = {
                settingsRepository.commandsMap = joypadController.commandsMapFlow.value
                closeDialog()
            }
        )
        _stateFlow.value = stateFlow.value.copy(dialog = newState)
    }

    private fun registerKeyboard(joypadButton: JoypadButton) {
        waitForKeyboardEvent = joypadButton
    }

    private fun registerGamepad(joypadButton: JoypadButton) {
        waitForGamepadEvent = joypadButton
    }

    private fun closeDialog() {
        _stateFlow.value = stateFlow.value.copy(dialog = ClosedDialogState)
    }

    private fun getDefaultCommands(): CommandsMap {
        val defaultKeyboardCommands = JoypadButton.entries.associateBy { button ->
            when (button) {
                JoypadButton.A -> Key.W
                JoypadButton.B -> Key.X
                JoypadButton.START -> Key.Enter
                JoypadButton.SELECT -> Key.Spacebar
                JoypadButton.UP -> Key.DirectionUp
                JoypadButton.DOWN -> Key.DirectionDown
                JoypadButton.LEFT -> Key.DirectionLeft
                JoypadButton.RIGHT -> Key.DirectionRight
            }
        }
        return CommandsMap(keyboardCommands = defaultKeyboardCommands)
    }

    class Factory : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T {
            val settings = JvmSettingsRepository.getSettings()
            val settingsRepository = JvmSettingsRepository(settings)
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(JoypadControllerHolder.instance, settingsRepository) as T
        }
    }
}
