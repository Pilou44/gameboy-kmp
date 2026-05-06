package com.wechantloup.gameboykmp

//import com.studiohartman.jamepad.ControllerManager
//import com.studiohartman.jamepad.ControllerIndex

class GamepadManager {
//    private val controllers = ControllerManager()
//
//    init {
//        controllers.initSDLGamepad()
//    }
//
//    fun poll(): GamepadState? {
//        controllers.update()
//        val state = controllers.getState(ControllerIndex.CONTROLLER_1)
//        if (!state.isConnected) return null
//
//        return GamepadState(
//            up    = state.dpadUp    || state.leftStickY >  0.5f,
//            down  = state.dpadDown  || state.leftStickY < -0.5f,
//            left  = state.dpadLeft  || state.leftStickX < -0.5f,
//            right = state.dpadRight || state.leftStickX >  0.5f,
//            leftX = state.leftStickX,
//            leftY = state.leftStickY,
//            a = state.a,
//            b = state.b,
//            x = state.x,
//            y = state.y,
//        )
//    }

//    fun dispose() {
//        controllers.quitSDLGamepad()
//    }
}

//data class GamepadState(
//    val axes: List<Float>,
//    val buttons: List<Boolean>,
////    val up: Boolean,
////    val down: Boolean,
////    val left: Boolean,
////    val right: Boolean,
////    val a: Boolean,
////    val b: Boolean,
////    val start: Boolean,
////    val select: Boolean,
//)
