package com.wechantloup.gameboykmp.cartridge

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class RomRamCartridge(
    private val rom: ByteArray,
    private val romName: String,
    private val scope: CoroutineScope,
    private val withBattery: Boolean,
) : Cartridge {
    private val _isSaving = MutableStateFlow(false)
    override val isSaving: StateFlow<Boolean> = _isSaving

    private val ram = IntArray(0x2000)  // 8KB max
        .also { ram ->
            if (withBattery) { SaveManager.load(romName)?.copyInto(ram) }
        }

    private var saveJob: Job? = null

    override fun readRom(address: Int): Int {
        return rom[address].toInt() and 0xFF
    }

    override fun writeRom(address: Int, value: Int) {
        // Read-only, ignore writes
    }

    override fun readRam(address: Int): Int {
        return ram[address]
    }

    override fun writeRam(address: Int, value: Int) {
        ram[address] = value
        onRamWritten()  // persist on every write
    }

    private fun onRamWritten() {
        if (!withBattery) return

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
