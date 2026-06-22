package com.wechantloup.gameboykmp.logger

// commonMain
interface LogSink {
    fun error(tag: String, msg: String, error: Throwable?)
    fun warning(tag: String, msg: String)
    fun info(tag: String, msg: String)
    fun debug(tag: String, msg: String)
    fun verbose(tag: String, msg: String)
}

// Same call sites everywhere (Logger.debug(...) unchanged); only the internals move. The
// platform piece is now just the default sink, behind expect/actual.
object Logger {
    var sink: LogSink = platformLogSink()
    fun error(tag: String, msg: String, error: Throwable? = null) = sink.error(tag, msg, error)
    fun warning(tag: String, msg: String) = sink.warning(tag, msg)
    fun info(tag: String, msg: String) = sink.info(tag, msg)
    fun debug(tag: String, msg: String) = sink.debug(tag, msg)
    fun verbose(tag: String, msg: String) = sink.verbose(tag, msg)
}

expect fun platformLogSink(): LogSink

object NoOpLogSink : LogSink {
    override fun error(tag: String, msg: String, error: Throwable?) {}
    override fun warning(tag: String, msg: String) {}
    override fun info(tag: String, msg: String) {}
    override fun debug(tag: String, msg: String) {}
    override fun verbose(tag: String, msg: String) {}
}
