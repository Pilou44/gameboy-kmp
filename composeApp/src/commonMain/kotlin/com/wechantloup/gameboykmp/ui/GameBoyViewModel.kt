package com.wechantloup.gameboykmp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.wechantloup.gameboykmp.apu.Apu
import com.wechantloup.gameboykmp.bus.Bus
import com.wechantloup.gameboykmp.cartridge.CartridgeFactory
import com.wechantloup.gameboykmp.cpu.Cpu
import com.wechantloup.gameboykmp.joypad.JoypadEvent
import com.wechantloup.gameboykmp.logger.Logger
import com.wechantloup.gameboykmp.ppu.Ppu
import com.wechantloup.gameboykmp.timer.Timer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.reflect.KClass
import kotlin.time.Clock
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class GameBoyViewModel(
    buttonChannel: Channel<JoypadEvent>,
) : ViewModel() {

    private var emulationJob: Job? = null
    private val _stateFlow = MutableStateFlow(GameBoyState())
    val stateFlow: StateFlow<GameBoyState> = _stateFlow
    val audioSamplesChannel = Channel<FloatArray>(8)

    private var bus: Bus? = null

    init {
        viewModelScope.launch {
            buttonChannel.consumeEach { event ->
                when (event) {
                    is JoypadEvent.Pressed  -> bus?.setButtonPressed(event.button)
                    is JoypadEvent.Released -> bus?.setButtonReleased(event.button)
                }
            }
        }
    }

    fun loadRom(romBytes: ByteArray, romName: String) {
        emulationJob?.cancel()

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
            ppu.frameChannel.consumeEach { frame ->
                _stateFlow.value = stateFlow.value.copy(
                    frameBuffer = frame,
                    frameCount = stateFlow.value.frameCount + 1,
                )
            }
        }

        var renderingIssueCount = 0
        var frameCount = 0
        var frameStartNs = currentTimeNanos()
        // Emulation loop
        emulationJob = viewModelScope.launch(Dispatchers.Default) {
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

                frameStartNs += FRAME_DURATION_NS
                val remaining = frameStartNs - currentTimeNanos()
                if (remaining > 0) {
                    delay(remaining.toDuration(DurationUnit.NANOSECONDS))
                } else {
                    renderingIssueCount++
                }

                if (frameCount % 60 == 0) {
                    Logger.error(TAG, "Rendering issue, $renderingIssueCount on last 60 taking too much time")
                    renderingIssueCount = 0
                }
                frameCount++
            }
        }
    }

    private fun currentTimeNanos(): Long {
        val now = Clock.System.now()
        return now.epochSeconds * 1_000_000_000L + now.nanosecondsOfSecond
    }

    class Factory(
        private  val buttonChannel: Channel<JoypadEvent>,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T {
            @Suppress("UNCHECKED_CAST")
            return GameBoyViewModel(buttonChannel) as T
        }
    }

    companion object {
        private const val TAG = "GameBoyViewModel"
        private const val FRAME_DURATION_NS = (1_000_000_000.0 / 59.7275).toLong()  // ≈ 16_742_706
    }
}
