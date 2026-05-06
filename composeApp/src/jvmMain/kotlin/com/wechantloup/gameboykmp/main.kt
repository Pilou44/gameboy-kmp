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
import com.wechantloup.gameboykmp.logger.Logger
import com.wechantloup.gameboykmp.ui.GameBoyIntent
import com.wechantloup.gameboykmp.ui.GameBoyViewModel
import kotlinx.coroutines.delay
import org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_AXIS_LEFT_X
import org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y
import org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_A
import org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_B
import org.lwjgl.glfw.GLFW.GLFW_JOYSTICK_1
import org.lwjgl.glfw.GLFW.GLFW_JOYSTICK_LAST
import org.lwjgl.glfw.GLFW.glfwGetGamepadState
import org.lwjgl.glfw.GLFW.glfwGetJoystickAxes
import org.lwjgl.glfw.GLFW.glfwGetJoystickButtons
import org.lwjgl.glfw.GLFW.glfwGetJoystickName
import org.lwjgl.glfw.GLFW.glfwInit
import org.lwjgl.glfw.GLFW.glfwJoystickPresent
import org.lwjgl.system.Configuration

fun main() {
    Configuration.GLFW_CHECK_THREAD0.set(false)
    application {
        glfwInit()
        var viewModel: GameBoyViewModel? = null
//        val manager = remember { GamepadManager() }
        val gamepadState = remember { mutableStateOf<GamepadState?>(null) }
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
                while (true) {
//                    glfwPollEvents() ToDo may be useful on Linux
                    pollGamepad(gamepadState)
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
}

fun pollGamepad(gamepadState: MutableState<GamepadState?>) {
    val slot = (GLFW_JOYSTICK_1..GLFW_JOYSTICK_LAST)
        .firstOrNull { glfwJoystickPresent(it) }

    if (slot == null) {
        Logger.debug("GamePad","No gamepad found")
        return
    }

    // Tente d'abord l'API gamepad (mapping enrichi)
    val state = org.lwjgl.glfw.GLFWGamepadState.create()
    if (glfwGetGamepadState(slot, state)) {
        val leftX = state.axes(GLFW_GAMEPAD_AXIS_LEFT_X)
        val leftY = state.axes(GLFW_GAMEPAD_AXIS_LEFT_Y)
        val btnA  = state.buttons(GLFW_GAMEPAD_BUTTON_A)
        val btnB  = state.buttons(GLFW_GAMEPAD_BUTTON_B)
        println("(gamepad) Stick: ($leftX, $leftY) | A=$btnA B=$btnB")
        return
    }

    // Fallback : API joystick brute (axes/boutons sans mapping)
    val name    = glfwGetJoystickName(slot) ?: "inconnu"
    val axes    = glfwGetJoystickAxes(slot)
    val buttons = glfwGetJoystickButtons(slot)

//    println("(joystick brut) $name — axes: ${axes?.capacity()}, boutons: ${buttons?.capacity()}")
    val newState = GamepadState(
        axes = (0 until (axes?.capacity() ?: 0)).map { axes!!.get(it) },
        buttons = (0 until (buttons?.capacity() ?: 0)).map { buttons!!.get(it) != 0.toByte() },
    )

    if (newState != gamepadState.value) {
        println("axes=${newState.axes} buttons=${newState.buttons}")
        gamepadState.value = newState
    }
//    if (axes != null) {
//        for (i in 0 until axes.capacity()) print("  axe[$i]=${axes[i]}")
//        println()
//    }
//    if (buttons != null) {
//        for (i in 0 until buttons.capacity()) print("  btn[$i]=${buttons[i]}")
//        println()
//    }
}

//private fun handleState(
//    viewModel: GameBoyViewModel?,
//    newState: GamepadState?,
//    state: MutableState<GamepadState?>,
//) {
//    if (newState == null) return
//
//    if (newState.up != state.value?.up) {
//        val intent = if (newState.up) {
//            GameBoyIntent.ButtonPressed(JoypadButton.UP)
//        } else {
//            GameBoyIntent.ButtonReleased(JoypadButton.UP)
//        }
//        viewModel?.onIntent(intent)
//    }
//
//    if (newState.down != state.value?.down) {
//        val intent = if (newState.down) {
//            GameBoyIntent.ButtonPressed(JoypadButton.DOWN)
//        } else {
//            GameBoyIntent.ButtonReleased(JoypadButton.DOWN)
//        }
//        viewModel?.onIntent(intent)
//    }
//
//    if (newState.left != state.value?.left) {
//        val intent = if (newState.left) {
//            GameBoyIntent.ButtonPressed(JoypadButton.LEFT)
//        } else {
//            GameBoyIntent.ButtonReleased(JoypadButton.LEFT)
//        }
//        viewModel?.onIntent(intent)
//    }
//
//    if (newState.right != state.value?.right) {
//        val intent = if (newState.right) {
//            GameBoyIntent.ButtonPressed(JoypadButton.RIGHT)
//        } else {
//            GameBoyIntent.ButtonReleased(JoypadButton.RIGHT)
//        }
//        viewModel?.onIntent(intent)
//    }
//
//    state.value = newState
//}

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

data class GamepadState(
    val axes: List<Float>,
    val buttons: List<Boolean>,
)
