package com.wechantloup.gameboykmp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.wechantloup.gameboykmp.bus.Bus
import com.wechantloup.gameboykmp.cartridge.CartridgeFactory
import com.wechantloup.gameboykmp.cpu.Cpu
import com.wechantloup.gameboykmp.ppu.Ppu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.reflect.KClass
import kotlin.time.Clock
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class GameBoyViewModel : ViewModel() {

    private val _stateFlow = MutableStateFlow(GameBoyState())
    val stateFlow: StateFlow<GameBoyState> = _stateFlow

    private lateinit var cpu: Cpu
    private lateinit var ppu: Ppu

    fun loadRom(romBytes: ByteArray) {
        val cartridge = CartridgeFactory.create(romBytes)
        val bus = Bus(cartridge)
        ppu = Ppu(bus)
        cpu = Cpu(bus).also { it.reset() }

        // Observe PPU frames
        viewModelScope.launch {
            ppu.frameFlow.collect { frame ->
                _stateFlow.value = GameBoyState(frameBuffer = frame)
            }
        }

        var frameStartNs = currentTimeNanos()
        // Emulation loop
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                // Run for 1 frame (70224 cycles)
                var frameCycles = 0
                while (frameCycles < 70224) {
                    val cycles = cpu.step()
                    ppu.step(cycles)
                    frameCycles += cycles
                }

                val newFrameNs = currentTimeNanos()
                val elapsed = newFrameNs - frameStartNs
                val remaining = (FRAME_DURATION_NS - elapsed).coerceAtLeast(0L)
                delay(remaining.toDuration(DurationUnit.NANOSECONDS))
                frameStartNs = newFrameNs
            }
        }
    }

    private fun currentTimeNanos(): Long {
        val now = Clock.System.now()
        return now.epochSeconds * 1_000_000_000L + now.nanosecondsOfSecond
    }

    class Factory : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T {
            @Suppress("UNCHECKED_CAST")
            return GameBoyViewModel() as T
        }
    }

    companion object {
        private const val FRAME_DURATION_NS = 16_740_000L
    }
}
