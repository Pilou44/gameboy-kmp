package com.wechantloup.gameboykmp.logger

expect object Logger {
    fun error(tag: String, msg: String, error: Throwable? = null)
    fun warning(tag: String, msg: String)
    fun info(tag: String, msg: String)
    fun debug(tag: String, msg: String)
    fun verbose(tag: String, msg: String)
}
