package com.wechantloup.gameboykmp

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wechantloup.gameboykmp.commands.JoypadController
import com.wechantloup.gameboykmp.commands.JoypadControllerHolder
import com.wechantloup.gameboykmp.ui.GameBoyViewModel
import com.wechantloup.gameboykmp.ui.MainScreen
import com.wechantloup.gameboykmp.ui.MainViewModel
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

private const val AXIS_VIRTUAL_BUTTON_OFFSET = 1000
private const val AXIS_THRESHOLD = 0.5f

fun main() = application {
    Configuration.GLFW_CHECK_THREAD0.set(false)
    glfwInit()

    val gamepadState = remember { mutableStateOf<GamepadState?>(null) }
    val joypadController = JoypadControllerHolder.instance

    var mainViewModel: MainViewModel? = null
    var gameBoyViewModel: GameBoyViewModel ?= null

    Window(
        onCloseRequest = ::exitApplication,
        onKeyEvent = { keyEvent ->
            if (mainViewModel?.catchAllInputs() == true) {
                mainViewModel?.onKeyEvent(keyEvent) == true
            } else {
                joypadController.handleKeyEvent(keyEvent.key, keyEvent.type)
            }
        },
        title = "GameBoyKMP",
    ) {
        val owner = checkNotNull(LocalViewModelStoreOwner.current)
        mainViewModel = viewModel<MainViewModel>(
            viewModelStoreOwner = owner,
            factory = MainViewModel.Factory()
        )
        gameBoyViewModel = viewModel<GameBoyViewModel>(
            viewModelStoreOwner = owner,
            factory = GameBoyViewModel.Factory(joypadController.buttonChannel)
        )

        val gbState by gameBoyViewModel.stateFlow.collectAsState()
        LaunchedEffect(gbState.frameCount) {
//                    glfwPollEvents() ToDo may be useful on Linux
            pollGamepad(gamepadState, mainViewModel, joypadController)
        }

        MaterialTheme {
            MainScreen()
        }
    }
}

fun pollGamepad(
    gamepadState: MutableState<GamepadState?>,
    mainViewModel: MainViewModel,
    joypadController: JoypadController,
) {
    val gamepadId = (GLFW_JOYSTICK_1..GLFW_JOYSTICK_LAST)
        .firstOrNull { glfwJoystickPresent(it) }
        ?: return

    // Tente d'abord l'API gamepad (mapping enrichi)
    // TODO test with xbox gamepad
    val state = org.lwjgl.glfw.GLFWGamepadState.create()
    if (glfwGetGamepadState(gamepadId, state)) {
        val leftX = state.axes(GLFW_GAMEPAD_AXIS_LEFT_X)
        val leftY = state.axes(GLFW_GAMEPAD_AXIS_LEFT_Y)
        val btnA  = state.buttons(GLFW_GAMEPAD_BUTTON_A)
        val btnB  = state.buttons(GLFW_GAMEPAD_BUTTON_B)
        println("(gamepad) Stick: ($leftX, $leftY) | A=$btnA B=$btnB")
        return
    }

    // Fallback : API joystick brute (axes/boutons sans mapping)
    val name    = glfwGetJoystickName(gamepadId) ?: "inconnu" // ToDo May be used to handle several gamepads
    val axes    = glfwGetJoystickAxes(gamepadId)
    val buttons = glfwGetJoystickButtons(gamepadId)

    val newState = GamepadState(
        axes = (0 until (axes?.capacity() ?: 0)).map { axes!!.get(it) },
        buttons = (0 until (buttons?.capacity() ?: 0)).map { buttons!!.get(it) != 0.toByte() },
    )

    val prevState = gamepadState.value

    if (newState != prevState) {
        // Physical buttons
        newState.buttons.forEachIndexed { index, pressed ->
            if (prevState?.buttons?.getOrNull(index) != pressed) {
                handleGamepadEvent(mainViewModel, joypadController, index, pressed)
            }
        }

        // Axes → boutons virtuels
        val newVirtual  = newState.toVirtualButtons()
        val prevVirtual = prevState?.toVirtualButtons() ?: emptyMap()
        newVirtual.forEach { (virtualIndex, pressed) ->
            if (prevVirtual[virtualIndex] != pressed) {
                handleGamepadEvent(mainViewModel, joypadController, virtualIndex, pressed)
            }
        }

        gamepadState.value = newState
    }
}

private fun handleGamepadEvent(
    mainViewModel: MainViewModel,
    joypadController: JoypadController,
    index: Int,
    pressed: Boolean,
) {
    if (mainViewModel.catchAllInputs()) {
        mainViewModel.onGamepadEvent(index, pressed)
    } else {
        joypadController.handleGamepadEvent(index, pressed)
    }
}

private fun GamepadState.toVirtualButtons(): Map<Int, Boolean> {
    val result = mutableMapOf<Int, Boolean>()
    axes.forEachIndexed { axisIndex, value ->
        result[AXIS_VIRTUAL_BUTTON_OFFSET + axisIndex * 2]     = value < -AXIS_THRESHOLD
        result[AXIS_VIRTUAL_BUTTON_OFFSET + axisIndex * 2 + 1] = value >  AXIS_THRESHOLD
    }
    return result
}

data class GamepadState(
    val axes: List<Float>,
    val buttons: List<Boolean>,
)
