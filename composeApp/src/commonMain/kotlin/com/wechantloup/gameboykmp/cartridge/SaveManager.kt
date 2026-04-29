package com.wechantloup.gameboykmp.cartridge

expect object SaveManager {
    fun save(name: String, data: IntArray)
    fun load(name: String): IntArray?
}
