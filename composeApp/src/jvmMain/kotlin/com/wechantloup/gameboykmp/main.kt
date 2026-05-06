package com.wechantloup.gameboykmp

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wechantloup.gameboykmp.joypad.JoypadButton
import com.wechantloup.gameboykmp.ui.GameBoyIntent
import com.wechantloup.gameboykmp.ui.GameBoyViewModel
import kotlinx.coroutines.delay

fun main() = application {
    var viewModel: GameBoyViewModel? = null
    val manager = remember { GamepadManager() }
    val state = remember { mutableStateOf<GamepadState?>(null) }
    Window(
        onCloseRequest = ::exitApplication,
        onKeyEvent = { keyEvent ->
            val button = keyEvent.key.toJoypadButton() ?: return@Window false
            val intent = when (keyEvent.type) {
                KeyEventType.KeyUp -> GameBoyIntent.ButtonReleased(button)
                KeyEventType.KeyDown -> GameBoyIntent.ButtonPressed(button)
                else -> return@Window false
            }
            viewModel?.onIntent(intent)
            true
        },
        title = "GameBoyKMP",
    ) {
        LaunchedEffect(Unit) {
            manager.connect()
            while (true) {
                val newState = manager.poll()
                handleState(viewModel, newState, state)
                delay(16) // ToDO ~60fps
            }
        }

        val owner = checkNotNull(LocalViewModelStoreOwner.current)
        viewModel = viewModel<GameBoyViewModel>(
            viewModelStoreOwner = owner,
            factory = GameBoyViewModel.Factory()
        )
        MaterialTheme {
            MainScreen()
        }
    }
}

private fun handleState(
    viewModel: GameBoyViewModel?,
    newState: GamepadState?,
    state: MutableState<GamepadState?>,
) {
    if (newState == null) return

    if (newState.up != state.value?.up) {
        val intent = if (newState.up) {
            GameBoyIntent.ButtonPressed(JoypadButton.UP)
        } else {
            GameBoyIntent.ButtonReleased(JoypadButton.UP)
        }
        viewModel?.onIntent(intent)
    }

    if (newState.down != state.value?.down) {
        val intent = if (newState.down) {
            GameBoyIntent.ButtonPressed(JoypadButton.DOWN)
        } else {
            GameBoyIntent.ButtonReleased(JoypadButton.DOWN)
        }
        viewModel?.onIntent(intent)
    }

    if (newState.left != state.value?.left) {
        val intent = if (newState.left) {
            GameBoyIntent.ButtonPressed(JoypadButton.LEFT)
        } else {
            GameBoyIntent.ButtonReleased(JoypadButton.LEFT)
        }
        viewModel?.onIntent(intent)
    }

    if (newState.right != state.value?.right) {
        val intent = if (newState.right) {
            GameBoyIntent.ButtonPressed(JoypadButton.RIGHT)
        } else {
            GameBoyIntent.ButtonReleased(JoypadButton.RIGHT)
        }
        viewModel?.onIntent(intent)
    }

    state.value = newState
}

private fun Key.toJoypadButton(): JoypadButton? = when (this) {
    Key.DirectionRight -> JoypadButton.RIGHT
    Key.DirectionLeft -> JoypadButton.LEFT
    Key.DirectionUp -> JoypadButton.UP
    Key.DirectionDown -> JoypadButton.DOWN
    Key.W -> JoypadButton.A
    Key.X -> JoypadButton.B
    Key.Enter -> JoypadButton.START
    Key.Backspace -> JoypadButton.SELECT
    else -> null
}
