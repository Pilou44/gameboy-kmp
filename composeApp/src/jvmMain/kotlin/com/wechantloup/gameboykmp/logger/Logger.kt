package com.wechantloup.gameboykmp.logger

actual object Logger {
    private const val RESET  = "\u001B[0m"
    private const val RED    = "\u001B[31m"
    private const val YELLOW = "\u001B[33m"
    private const val CYAN   = "\u001B[36m"
    private const val WHITE  = "\u001B[37m"
    private const val GRAY   = "\u001B[90m"

    actual fun error(tag: String, msg: String, error: Throwable?) {
        log(color = RED, severity = "E", message = msg, tag = tag, throwable = error)
    }

    actual fun warning(tag: String, msg: String) {
        log(color = YELLOW, severity = "W", message = msg, tag = tag, throwable = null)
    }

    actual fun info(tag: String, msg: String) {
        log(color = WHITE, severity = "I", message = msg, tag = tag, throwable = null)
    }

    actual fun debug(tag: String, msg: String) {
        log(color = CYAN, severity = "D", message = msg, tag = tag, throwable = null)
    }

    actual fun verbose(tag: String, msg: String) {
        log(color = GRAY, severity = "V", message = msg, tag = tag, throwable = null)
    }

    private fun log(color: String, severity: String, message: String, tag: String, throwable: Throwable?) {
        val line  = "$color[$severity] $tag: $message$RESET"
        println(line)
        throwable?.let { println("$RED${it.stackTraceToString()}$RESET") }
    }
}
