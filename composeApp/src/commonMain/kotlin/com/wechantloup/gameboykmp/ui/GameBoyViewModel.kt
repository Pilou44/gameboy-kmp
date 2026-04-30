package com.wechantloup.gameboykmp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.wechantloup.gameboykmp.apu.Apu
import com.wechantloup.gameboykmp.bus.Bus
import com.wechantloup.gameboykmp.cartridge.CartridgeFactory
import com.wechantloup.gameboykmp.cpu.Cpu
import com.wechantloup.gameboykmp.ppu.Ppu
import com.wechantloup.gameboykmp.timer.Timer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
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
    val audioSamplesChannel = Channel<FloatArray>(8)

    private var bus: Bus? = null

    fun loadRom(romBytes: ByteArray, romName: String) {
        val cartridge = CartridgeFactory.create(
            rom = romBytes,
            romName = romName,
            scope = viewModelScope,
        )
        val bus = Bus(cartridge).also { bus = it }

        viewModelScope.launch {
            cartridge.isSaving.collect {
                _stateFlow.value = stateFlow.value.copy(isSaving = it)
            }
        }

        val timer = Timer(bus)
        val ppu = Ppu(bus)
        val apu = Apu(bus)
        val cpu = Cpu(bus).also { it.reset() }

        viewModelScope.launch {
            for (samples in apu.samplesChannel) {
                audioSamplesChannel.trySend(samples)
            }
        }

        // Observe PPU frames
        viewModelScope.launch {
            ppu.frameFlow.collect { frame ->
                _stateFlow.value = stateFlow.value.copy(frameBuffer = frame)
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
                    apu.step(cycles)
                    timer.step(cycles)
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

    fun onIntent(intent: GameBoyIntent) {
        when(intent) {
            is GameBoyIntent.ButtonPressed -> bus?.setButtonPressed(intent.button)
            is GameBoyIntent.ButtonReleased -> bus?.setButtonReleased(intent.button)
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
