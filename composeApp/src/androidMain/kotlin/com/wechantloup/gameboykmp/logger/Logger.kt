package com.wechantloup.gameboykmp.logger

import android.util.Log

actual object Logger {
    actual fun error(tag: String, msg: String, error: Throwable?) {
        Log.e(tag, msg, error)
    }

    actual fun warning(tag: String, msg: String) {
        Log.w(tag, msg)
    }

    actual fun info(tag: String, msg: String) {
        Log.i(tag, msg)
    }

    actual fun debug(tag: String, msg: String) {
        Log.d(tag, msg)
    }

    actual fun verbose(tag: String, msg: String) {
        Log.d(tag, msg)
    }
}
