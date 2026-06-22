package com.wechantloup.gameboykmp.logger

import android.util.Log

actual fun platformLogSink() = object:LogSink {
    override fun error(tag: String, msg: String, error: Throwable?) {
        Log.e(tag, msg, error)
    }

    override fun warning(tag: String, msg: String) {
        Log.w(tag, msg)
    }

    override fun info(tag: String, msg: String) {
        Log.i(tag, msg)
    }

    override fun debug(tag: String, msg: String) {
        Log.d(tag, msg)
    }

    override fun verbose(tag: String, msg: String) {
        Log.d(tag, msg)
    }
}
