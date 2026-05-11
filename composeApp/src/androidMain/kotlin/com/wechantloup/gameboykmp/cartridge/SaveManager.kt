package com.wechantloup.gameboykmp.cartridge

import android.content.Context
import java.io.File

actual object SaveManager {
    var filesDir: File? = null

    fun init(context: Context) {
        filesDir = context.applicationContext.filesDir
    }

    actual fun save(name: String, data: IntArray) {
        val filesDir = requireNotNull(filesDir) {
            "SaveManager has not been initialized. Call SaveManager.init(Context) first"
        }
        val file = File(filesDir, "$name.sav")
        file.writeBytes(ByteArray(data.size) { data[it].toByte() })
    }

    actual fun load(name: String): IntArray? {
        val filesDir = requireNotNull(filesDir) {
            "SaveManager has not been initialized. Call SaveManager.init(Context) first"
        }
        val file = File(filesDir, "$name.sav")
        if (!file.exists()) return null
        return file.readBytes().map { it.toInt() and 0xFF }.toIntArray()
    }
}
