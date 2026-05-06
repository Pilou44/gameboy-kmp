package com.wechantloup.gameboykmp

import net.java.games.input.Controller
import net.java.games.input.ControllerEnvironment
import net.java.games.input.Component.Identifier.Axis
import net.java.games.input.Component.Identifier.Button

class GamepadManager {

    private var controller: Controller? = null

    fun connect(): Boolean {
        val controllers = ControllerEnvironment.getDefaultEnvironment().controllers
        controller = controllers.firstOrNull {
            it.type == Controller.Type.GAMEPAD || it.type == Controller.Type.STICK
        }
        return controller != null
    }

    fun poll(): GamepadState? {
        val ctrl = controller ?: return null
        if (!ctrl.poll()) return null

        val components = ctrl.components.associateBy { it.identifier }

        val leftX = components[Axis.X]?.pollData ?: 0f
        val leftY = components[Axis.Y]?.pollData ?: 0f

        // D-pad (POV/Hat) — valeur normalisée entre 0f et 1f
        // 0.25=haut, 0.5=droite, 0.75=bas, 1.0=gauche (+ diagonales)
        val pov = components[Axis.POV]?.pollData ?: 0f

        val dpadUp    = pov in 0.2f..0.3f || pov in 0.1f..0.15f || pov in 0.85f..1.0f
        val dpadRight = pov in 0.4f..0.6f
        val dpadDown  = pov in 0.65f..0.8f
        val dpadLeft  = pov in 0.85f..1.0f || pov == 1.0f

        return GamepadState(
            up    = dpadUp    || leftY < -STICK_THRESHOLD,
            down  = dpadDown  || leftY >  STICK_THRESHOLD,
            left  = dpadLeft  || leftX < -STICK_THRESHOLD,
            right = dpadRight || leftX >  STICK_THRESHOLD,
            a = components[Button._0]?.pollData == 1f,
            b = components[Button._1]?.pollData == 1f,
            start = components[Button._2]?.pollData == 1f,
            select = components[Button._3]?.pollData == 1f,
        )
    }

    companion object {
        private const val STICK_THRESHOLD = 0.5f
    }
}

data class GamepadState(
    val up: Boolean,
    val down: Boolean,
    val left: Boolean,
    val right: Boolean,
    val a: Boolean,
    val b: Boolean,
    val start: Boolean,
    val select: Boolean,
)
