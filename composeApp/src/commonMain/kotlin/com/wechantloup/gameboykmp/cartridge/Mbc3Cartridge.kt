package com.wechantloup.gameboykmp.cartridge

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class Mbc3Cartridge(
    private val rom: ByteArray,
    private val romName: String,
    private val scope: CoroutineScope,
    private val withSave: Boolean,
    private val withRtc: Boolean,
) : Cartridge {
    private var saveJob: Job? = null
    private val _isSaving = MutableStateFlow(false)
    override val isSaving: StateFlow<Boolean> = _isSaving

    private val cartridgeSave: Mbc3CartridgeSave = SaveManager.load(romName)
        ?.let { Mbc3CartridgeSave(it) }
        ?: Mbc3CartridgeSave()
    private val ram: IntArray
        get() = cartridgeSave.ram
    private val carry: Boolean
        get() = cartridgeSave.carry
    private val rtcOffset: Long
        get() = cartridgeSave.rtcOffset

    override fun readRom(address: Int): Int {
        TODO("Not yet implemented")
    }

    override fun writeRom(address: Int, value: Int) {
        TODO("Not yet implemented")
    }

    override fun readRam(address: Int): Int {
        TODO("Not yet implemented")
    }

    override fun writeRam(address: Int, value: Int) {
        TODO("Not yet implemented")
    }

    private fun onRamWritten() {
        if (!withSave) return

        _isSaving.value = true
        saveJob?.cancel()
        saveJob = scope.launch(Dispatchers.IO) {
            delay(DEBOUNCE_DURATION_MS) // debounce: wait for writes to settle before persisting
            SaveManager.save(romName, cartridgeSave.toIntArray())
            _isSaving.value = false
        }
    }

    companion object {
        private const val DEBOUNCE_DURATION_MS = 500L
    }
}
