package com.wechantloup.gameboykmp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.wechantloup.gameboykmp.MachineMode
import com.wechantloup.gameboykmp.apu.Apu
import com.wechantloup.gameboykmp.bus.Bus
import com.wechantloup.gameboykmp.cartridge.Cartridge
import com.wechantloup.gameboykmp.cartridge.CartridgeFactory
import com.wechantloup.gameboykmp.cpu.Cpu
import com.wechantloup.gameboykmp.joypad.JoypadEvent
import com.wechantloup.gameboykmp.logger.Logger
import com.wechantloup.gameboykmp.ppu.Ppu
import com.wechantloup.gameboykmp.timer.Timer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
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

    private var machineModeForMixtGame = MachineMode.CGB
    private var machineModeForDMGGame = MachineMode.DMG

    private var bus: Bus? = null
    private var timer: Timer? = null
    private var ppu: Ppu? = null
    private var apu: Apu? = null
    private var cartridge: Cartridge? = null
    var frameCycles = 0

    private val dmgPalette: Palette
        get() = stateFlow.value.dmgPalette

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

    /**
     * Cancels the emulation loop and all coroutines launched in this ViewModel.
     * Must be called explicitly in non-Android contexts (e.g. JVM tests) where
     * onCleared() is never triggered by a ViewModelStore.
     */
    fun stop() {
        viewModelScope.cancel()
    }

    fun loadRom(
        romBytes: ByteArray,
        romName: String,
        machineMode: MachineMode = getMachineMode(romBytes),
    ) {
        viewModelScope.launch {
            emulationJob?.cancelAndJoin() // Now safe to await in a coroutine

            _stateFlow.value = GameBoyState(dmgPalette = dmgPalette)
            isPaused = false

            val job = Job(parent = coroutineContext[Job]).also { emulationJob = it }
            val emulationScope = CoroutineScope(coroutineContext + job)

            val cartridge = CartridgeFactory.create(
                rom = romBytes,
                romName = romName,
                scope = emulationScope,
            ).also { cartridge = it }

            val bus = Bus(cartridge, machineMode).also { bus = it }

            emulationScope.launch {
                cartridge.isSaving.collect { isSaving ->
                    _stateFlow.update { it.copy(isSaving = isSaving) }
                }
            }

            timer = Timer(bus)
            val ppu = Ppu(bus).also { ppu = it }
            val apu = Apu(bus).also { apu = it }
            val cpu = Cpu(bus, ::onMachineCycleTick).also { it.reset() }

            emulationScope.launch {
                for (samples in apu.samplesChannel) {
                    audioSamplesChannel.trySend(samples)
                }
            }

            emulationScope.launch {
                ppu.frameChannel.consumeEach { frame ->
                    _stateFlow.update {
                        it.copy(
                            coloredFrameBuffer = getColoredFrameBuffer(frame, bus.machineMode),
                            frameCount = stateFlow.value.frameCount + 1,
                        )
                    }
                }
            }

            var renderingIssueCount = 0
            var frameCount = 0
            var frameStartMark = currentTimeMark()
            val frameDuration = FRAME_DURATION_NS.toDuration(DurationUnit.NANOSECONDS)

            emulationScope.launch(Dispatchers.Default) {
                while (true) {
                    while (isPaused) {
                        delay(200)
                        frameStartMark = currentTimeMark()
                    }

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
    }

    fun pause() {
        isPaused = true
    }

    fun resume() {
        isPaused = false
    }

    fun setDmgPalette(palette: Palette) {
        // TODO live palette change only repaints on the next frame; a frozen frame
        //  (reachable only once pause() is wired to the UI) would keep the old palette.
        //  Fix then by keeping the last raw frame and re-colorizing it here.
        _stateFlow.update {
            it.copy(dmgPalette = palette)
        }
    }

    private fun onMachineCycleTick() {
        // PPU and APU stay at their normal rate: in double speed the loop ticks twice as
        // often, so they must advance half as many dots per tick to keep the same wall-clock rate.
        val ppuCycles = if (bus?.isDoubleSpeed == true) 2 else 4
        ppu?.step(ppuCycles)
        // Timer/DIV runs twice as fast in double speed. It stays at 4 T-cycles per M-cycle and
        // doubles automatically because the loop is called twice as often — do NOT scale it.
        timer?.step(4)
        apu?.step(ppuCycles)
        // OAM DMA is 1 byte per M-cycle, 160 M-cycles total, in both speeds. Called once per
        // tick → already correct (just faster in wall-clock during double speed).
        bus?.stepDma()
        // RTC tracks real time, not CPU cycles: passing the scaled value keeps it real-time.
        cartridge?.stepRtc(ppuCycles)
        // frameCycles counts PPU dots; a frame is a fixed 70224-dot count, so it must advance at
        // the PPU rate. In double speed this makes the inner loop run 35112 M-cycles/frame (2x).
        frameCycles += ppuCycles
    }

    private fun currentTimeMark(): ValueTimeMark {
        return TimeSource.Monotonic.markNow()
    }

    private fun getMachineMode(romBytes: ByteArray): MachineMode {
        val cartridgeType = romBytes[0x0143].toInt() and 0xC0
        return when (cartridgeType) {
            0xC0 -> MachineMode.CGB
            0x80 -> machineModeForMixtGame
            else -> machineModeForDMGGame
        }.also {
            Logger.debug(TAG, "Cartridge byte 0x043 = ${romBytes[0x0143].toInt() and 0xFF}")
            Logger.debug(TAG, "Machine mode = ${it.name}")
        }
    }

    private fun getColoredFrameBuffer(frame: IntArray, machineMode: MachineMode): IntArray {
        return when (machineMode) {
            MachineMode.CGB_COMPAT,
            MachineMode.CGB,
            -> IntArray(frame.size) { rgb555ToArgb(frame[it]) }
            MachineMode.DMG -> IntArray(frame.size) { dmgPalette.colors[frame[it]] }
        }
    }

    // RGB555 (15-bit, little-endian as stored in CGB palette RAM) → ARGB8888.
    // Channel expansion: (c shl 3) or (c shr 2) maps 0x1F to 0xFF (full 8-bit range).
    private fun rgb555ToArgb(color: Int): Int {
        val r = color and 0x1F
        val g = (color shr 5) and 0x1F
        val b = (color shr 10) and 0x1F
        val r8 = (r shl 3) or (r shr 2)
        val g8 = (g shl 3) or (g shr 2)
        val b8 = (b shl 3) or (b shr 2)
        return (0xFF shl 24) or (r8 shl 16) or (g8 shl 8) or b8
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
