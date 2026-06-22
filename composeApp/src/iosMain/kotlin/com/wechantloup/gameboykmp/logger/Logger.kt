package com.wechantloup.gameboykmp.logger

import platform.Foundation.NSLog

actual fun platformLogSink() = object:LogSink {
    override fun error(tag: String, msg: String, error: Throwable?) {
        NSLog("❌ ERROR/$tag: $msg${error?.let { "\n$it" } ?: ""}")
    }
    override fun warning(tag: String, msg: String) {
        NSLog("⚠️ WARN/$tag: $msg")
    }
    override fun info(tag: String, msg: String) {
        NSLog("ℹ️ INFO/$tag: $msg")
    }
    override fun debug(tag: String, msg: String) {
        NSLog("🐛 DEBUG/$tag: $msg")
    }
    override fun verbose(tag: String, msg: String) {
        NSLog("🔬 VERBOSE/$tag: $msg")
    }
}
