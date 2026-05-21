package com.wechantloup.gameboykmp.timer

import com.wechantloup.gameboykmp.bus.Bus

class Timer(private val bus: Bus) {

    private var cycleCount = 0

    private var timaCycleCount = 0

    private var timaOverflowPending = false
    private var timaOverflowCycles = 0

    init {
        bus.onDivReset = {
            cycleCount = 0
            timaCycleCount = 0
        }
        bus.canWriteOnTima = {
            !timaOverflowPending
        }
    }

    private var tima: Int
        get() = bus.read(TIMA_ADDR)
        set(value) = bus.write(TIMA_ADDR, value)

    private val tma: Int
        get() = bus.read(TMA_ADDR)

    private val timerActivated: Boolean
        get() = bus.read(TAC_ADDR) and 0x04 > 0
    private val timerFrequency: Int
        get() = bus.read(TAC_ADDR) and 0x03
    private val timerPeriod: Int
        get() = when (timerFrequency) {
            0 -> 1024
            1 -> 16
            2 -> 64
            3 -> 256
            else -> throw IllegalStateException("timerFrequency should be 0 to 3, bad value $timerFrequency")
        }

    fun step(cycles: Int) {
        cycleCount += cycles

        if (cycleCount % 256 < cycles) {
            bus.incDiv()
        }

        // Handle pending TIMA overflow (delayed by 4 cycles after overflow)
        if (timaOverflowPending) {
            timaOverflowCycles -= cycles
            if (timaOverflowCycles <= 0) {
                timaOverflowPending = false
                tima = tma
                bus.setIF(bus.iF or 0x04)
            }
        }

        if (timerActivated) {
            timaCycleCount += cycles
            while (timaCycleCount >= timerPeriod) {
                timaCycleCount -= timerPeriod
                val nextTimaValue = tima + 1
                if (nextTimaValue > 0xFF) {
                    tima = 0x00              // write succeeds: pending is still false here
                    timaOverflowPending = true
                    timaOverflowCycles = 4
                } else {
                    tima = nextTimaValue and 0xFF
                }
            }
        }
    }

    companion object {
        private const val TIMA_ADDR = 0xFF05
        private const val TMA_ADDR = 0xFF06
        private const val TAC_ADDR = 0xFF07
    }
}
