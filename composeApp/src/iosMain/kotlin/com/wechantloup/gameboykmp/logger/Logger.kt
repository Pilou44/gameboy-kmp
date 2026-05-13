package com.wechantloup.gameboykmp.logger

import platform.Foundation.NSLog

actual object Logger {
    actual fun error(tag: String, msg: String, error: Throwable?) {
        NSLog("❌ ERROR/$tag: $msg${error?.let { "\n$it" } ?: ""}")
    }
    actual fun warning(tag: String, msg: String) {
        NSLog("⚠️ WARN/$tag: $msg")
    }
    actual fun info(tag: String, msg: String) {
        NSLog("ℹ️ INFO/$tag: $msg")
    }
    actual fun debug(tag: String, msg: String) {
        NSLog("🐛 DEBUG/$tag: $msg")
    }
    actual fun verbose(tag: String, msg: String) {
        NSLog("🔬 VERBOSE/$tag: $msg")
    }
}
