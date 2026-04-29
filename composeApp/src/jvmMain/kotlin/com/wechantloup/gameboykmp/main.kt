package com.wechantloup.gameboykmp

import androidx.compose.material.MaterialTheme
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

fun main() = application {
    var viewModel: GameBoyViewModel? = null
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
