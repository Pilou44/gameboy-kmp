package com.wechantloup.gameboykmp.cartridge

import java.io.File

actual object SaveManager {
    actual fun save(name: String, data: IntArray) {
        val file = File("$name.sav")
        file.writeBytes(ByteArray(data.size) { data[it].toByte() })
    }

    actual fun load(name: String): IntArray? {
        val file = File("$name.sav")
        if (!file.exists()) return null
        return file.readBytes().map { it.toInt() and 0xFF }.toIntArray()
    }
}
