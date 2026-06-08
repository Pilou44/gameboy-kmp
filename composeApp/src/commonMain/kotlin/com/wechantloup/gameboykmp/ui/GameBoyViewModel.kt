package com.wechantloup.gameboykmp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.wechantloup.gameboykmp.apu.Apu
import com.wechantloup.gameboykmp.bus.Bus
import com.wechantloup.gameboykmp.cartridge.Cartridge
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
import kotlin.concurrent.Volatile
import kotlin.reflect.KClass
import kotlin.time.DurationUnit
import kotlin.time.TimeSource
import kotlin.time.TimeSource.Monotonic.ValueTimeMark
import kotlin.time.toDuration

class GameBoyViewModel : ViewModel() {

    private var emulationJob: Job? = null
    @Volatile
    private var isPaused = false
    private val _stateFlow = MutableStateFlow(GameBoyState())
    val stateFlow: StateFlow<GameBoyState> = _stateFlow
    val audioSamplesChannel = Channel<FloatArray>(8)
    val buttonChannel = Channel<JoypadEvent>(Channel.UNLIMITED)

    private var bus: Bus? = null
    private var timer: Timer? = null
    private var ppu: Ppu? = null
    private var apu: Apu? = null
    private var cartridge: Cartridge? = null
    var frameCycles = 0

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

        timer = Timer(bus)
        val ppu = Ppu(bus).also { ppu = it }
        val apu = Apu(bus).also { apu = it }
        val cpu = Cpu(bus, ::onMachineCycleTick).also { it.reset() }

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
        var frameStartMark = currentTimeMark()

        val frameDuration = FRAME_DURATION_NS.toDuration(DurationUnit.NANOSECONDS)
        // Emulation loop
        emulationJob = viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                while (isPaused) {
                    delay(200)
                    frameStartMark = currentTimeMark()
                }

                // Run for 1 frame (70224 cycles)
                frameCycles = 0
                while (frameCycles < 70224) {
                    cpu.step()
                }

                frameStartMark += frameDuration
                val remaining = frameStartMark.minus(currentTimeMark())
                if (remaining.isPositive()) {
                    delay(remaining)
                } else {
                    renderingIssueCount++
                }

                if (frameCount % 60 == 0 && renderingIssueCount > 0) {
                    Logger.error(TAG, "Rendering issue, $renderingIssueCount on last 60 taking too much time")
                    renderingIssueCount = 0
                }
                frameCount++
            }
        }
    }

    fun pause() {
        isPaused = true
    }

    fun resume() {
        isPaused = false
    }

    private fun onMachineCycleTick() {
        ppu?.step(4) // TODO Always 4, useless parameter
        timer?.step(4) // TODO Always 4, useless parameter
        apu?.step(4) // TODO Always 4, useless parameter
        bus?.stepDma()
        cartridge?.stepRtc()
        frameCycles += 4
    }

    private fun currentTimeMark(): ValueTimeMark {
        return TimeSource.Monotonic.markNow()
    }

    class Factory : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T {
            @Suppress("UNCHECKED_CAST")
            return GameBoyViewModel() as T
        }
    }

    companion object {
        private const val TAG = "GameBoyViewModel"
        private const val FRAME_DURATION_NS = (1_000_000_000.0 / 59.7275).toLong()  // ≈ 16_742_706
    }
}
