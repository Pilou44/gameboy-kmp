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

    private val ram = IntArray(0x8000)  // 32KB max - 4 banks × 8KB
        .also { ram ->
            if (withSave) { SaveManager.load(romName)?.copyInto(ram) } // ToDo
        }
    private val carry: Boolean = false
        .also {
            if (withRtc) TODO() // Load carry
        }
    private val rtcOffset: Int = 0x00
        .also {
            if (withRtc) TODO() // Load rtc
        }

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
            SaveManager.save(romName, ram)
            _isSaving.value = false
        }
    }

    companion object {
        private const val DEBOUNCE_DURATION_MS = 500L
    }
}
