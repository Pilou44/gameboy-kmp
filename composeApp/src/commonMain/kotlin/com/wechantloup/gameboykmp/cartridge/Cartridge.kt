package com.wechantloup.gameboykmp.cartridge

import kotlinx.coroutines.flow.StateFlow

sealed interface Cartridge {
    val isSaving: StateFlow<Boolean>
    fun readRom(address: Int): Int
    fun writeRom(address: Int, value: Int)
    fun readRam(address: Int): Int
    fun writeRam(address: Int, value: Int)
}
