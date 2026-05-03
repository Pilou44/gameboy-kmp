package com.wechantloup.gameboykmp.bus

import com.wechantloup.gameboykmp.cartridge.Cartridge
import com.wechantloup.gameboykmp.joypad.JoypadButton
import kotlin.concurrent.Volatile

/**
 * Represents the Game Boy memory bus - the full 64KB addressable space.
 *
 * Memory map:
 * 0x0000 - 0x3FFF  ROM Bank 0 (cartridge, fixed)
 * 0x4000 - 0x7FFF  ROM Bank N (cartridge, switchable via MBC)
 * 0x8000 - 0x9FFF  VRAM (Video RAM)
 * 0xA000 - 0xBFFF  External RAM (cartridge)
 * 0xC000 - 0xCFFF  Work RAM Bank 0
 * 0xD000 - 0xDFFF  Work RAM Bank 1
 * 0xE000 - 0xFDFF  Echo RAM (mirror of 0xC000-0xDDFF, avoid using)
 * 0xFE00 - 0xFE9F  OAM (Sprite Attribute Table)
 * 0xFEA0 - 0xFEFF  Unusable
 * 0xFF00 - 0xFF7F  I/O Registers
 * 0xFF80 - 0xFFFE  High RAM (HRAM)
 * 0xFFFF           Interrupt Enable Register
 */
class Bus(
    private val cartridge: Cartridge,
) {

    // Bit 0 : V-Blank  - PPU entered V-Blank period (LY == 144)
    // Bit 1 : LCD STAT - PPU mode change or LY==LYC coincidence (depends on STAT bits 3-6)
    // Bit 2 : Timer    - TIMA overflowed and was reloaded from TMA
    // Bit 3 : Serial   - Serial transfer complete
    // Bit 4 : Joypad   - Joypad button pressed (high-to-low transition)
    // Bits 5-7 : unused, always 0
    val ie: Int get() = read(0xFFFF) // Enabled interrupts
    val iF: Int get() = read(0xFF0F) // Requested interrupts

    private val internalRam = IntArray(0x10000).also { initPostBootRegisters(it) }
    private val vram = IntArray(0x2000)  // 8KB
    private val oam = IntArray(0xA0) // 160 bytes = 40 sprites × 4 bytes
    @Volatile
    private var joypadState = 0xFF  // all buttons released

    /**
     * Callback invoked when the APU is powered off (NR52 bit 7: 1 -> 0).
     * The APU should register here to reset its channels' internal state.
     */
    var onApuPowerOff: (() -> Unit)? = null

    var onChannel1Trigger: (() -> Unit)? = null
    var onChannel2Trigger: (() -> Unit)? = null
    var onChannel3Trigger: (() -> Unit)? = null
    var onChannel4Trigger: (() -> Unit)? = null
    var onDivReset: (() -> Unit)? = null

    val apuPoweredOn: Boolean get() = internalRam[0xFF26] and 0x80 != 0

    fun read(address: Int): Int = when (address) {
        0xFF00 -> {
            val p1 = internalRam[0xFF00]
            // Bits 0-3 are active-low: 0=pressed, 1=released
            // Bit 5 selects direction keys, bit 4 selects action buttons
            return when {
                p1 and 0x20 == 0 -> (p1 and 0xF0) or (joypadState shr 4 and 0x0F)  // directions
                p1 and 0x10 == 0 -> (p1 and 0xF0) or (joypadState and 0x0F)         // buttons
                else -> p1 or 0x0F
            }
        }
        in 0x0000..0x7FFF -> cartridge.readRom(address)
        in 0x8000..0x9FFF -> readVram(address - 0x8000)
        in 0xA000..0xBFFF -> cartridge.readRam(address - 0xA000)
        in 0xFE00..0xFE9F -> readOam(address - 0XFE00)
        in 0xFF10..0xFF3F -> readApuRegister(address)
        else -> internalRam[address]
    }

    fun write(address: Int, value: Int) {
        val v = value and 0xFF
        when (address) {
            0xFF04 -> {
                internalRam[0xFF04] = 0
                onDivReset?.invoke()
            }
            0xFF46 -> triggerDmaTransfer(v)
            0xFF26 -> writeNR52(v)
            // When APU is off, writes to NR10-NR25 are ignored (wave RAM 0xFF30-0xFF3F is always writable)
            // TODO: NR41 (0xFF20) length counter should be writable even when APU is powered off (DMG quirk)
            in 0xFF10..0xFF25 -> if (apuPoweredOn) {
                internalRam[address] = v
                when (address) {
                    // TODO: bit 7 (trigger) is write-only on real hardware and should not be stored
                    0xFF14 -> if (v and 0x80 != 0) onChannel1Trigger?.invoke()
                    0xFF19 -> if (v and 0x80 != 0) onChannel2Trigger?.invoke()
                    0xFF1E -> if (v and 0x80 != 0) onChannel3Trigger?.invoke()
                    0xFF23 -> if (v and 0x80 != 0) onChannel4Trigger?.invoke()
                }
            }
            in 0x0000..0x7FFF -> cartridge.writeRom(address, v)
            in 0x8000..0x9FFF -> writeVram(address - 0x8000, v)
            in 0xA000..0xBFFF -> cartridge.writeRam(address - 0xA000, v)
            in 0xFE00..0xFE9F -> writeOam(address - 0XFE00, v)
            else -> internalRam[address] = v
        }
    }

    /**
     * Direct register read bypassing APU CPU-view masks.
     * For internal APU use only — returns the raw stored value.
     */
    fun readRaw(address: Int): Int = internalRam[address]

    /**
     * Called by channels to update their status bit in NR52 (bits 3-0).
     * Bypasses the normal write path to avoid triggering power-off logic.
     */
    fun setChannelEnabled(channelBit: Int, enabled: Boolean) {
        val current = internalRam[0xFF26]
        internalRam[0xFF26] = if (enabled) current or channelBit else current and channelBit.inv()
    }

    fun setButtonPressed(button: JoypadButton) {
        val mask = buttonMask(button)
        joypadState = joypadState and mask.inv()  // set bit to 0 (active-low)
        // Trigger joypad interrupt
        setIF(iF or 0x10)
    }

    fun setButtonReleased(button: JoypadButton) {
        val mask = buttonMask(button)
        joypadState = joypadState or mask  // set bit to 1 (released)
    }

    private fun buttonMask(button: JoypadButton): Int = when (button) {
        JoypadButton.RIGHT  -> 0x01
        JoypadButton.LEFT   -> 0x02
        JoypadButton.UP     -> 0x04
        JoypadButton.DOWN   -> 0x08
        JoypadButton.A      -> 0x10
        JoypadButton.B      -> 0x20
        JoypadButton.SELECT -> 0x40
        JoypadButton.START  -> 0x80
    }

    /**
     * NR52 (0xFF26) write handler.
     *
     * Only bit 7 (APU power) is writable by the CPU.
     * Bits 6-4 are always 1 (handled by readApuRegister mask).
     * Bits 3-0 reflect channel enable status and are read-only from the CPU's perspective;
     * they are updated directly via setChannelEnabled().
     *
     * Power off (bit 7: 1 -> 0):
     *   - Notifies APU to reset all channel internal state
     *   - Clears NR10-NR51 registers to 0
     *   - Clears NR52 entirely (including channel status bits 3-0)
     *
     * Power on (bit 7: 0 -> 1):
     *   - Sets bit 7 only; channel status bits remain 0
     *
     * Already on:
     *   - Only bit 7 is updated; channel status bits 3-0 are preserved
     */
    private fun writeNR52(value: Int) {
        val wasOn = internalRam[0xFF26] and 0x80 != 0
        val isOn = value and 0x80 != 0

        when {
            wasOn && !isOn -> {
                // Power off: notify APU, then clear all audio registers and channel status
                onApuPowerOff?.invoke()
                for (addr in 0xFF10..0xFF25) {
                    internalRam[addr] = 0
                }
                internalRam[0xFF26] = 0  // bit 7 = 0, status bits = 0
            }
            !wasOn && isOn -> {
                // Power on: set bit 7 only, channel status bits stay 0
                internalRam[0xFF26] = 0x80
            }
            else -> {
                // No power state change: preserve channel status bits 3-0
                val currentStatus = internalRam[0xFF26] and 0x0F
                internalRam[0xFF26] = (value and 0x80) or currentStatus
            }
        }
    }

    private fun readApuRegister(address: Int): Int {
        val raw = internalRam[address]
        return when (address) {
            0xFF10 -> raw or 0x80  // NR10 : bit 7 always 1
            0xFF11 -> raw or 0x3F  // NR11 : bits 5-0 write-only → read as 1
            0xFF12 -> raw          // NR12 : fully readable
            0xFF13 -> 0xFF         // NR13 : write-only
            // TODO: bit 7 reads as 1 due to or 0xBF mask, but it should reflect nothing — trigger bit is write-only
            0xFF14 -> raw or 0xBF  // NR14 : bits 5-0 and 7 read as 1, except bit 6
            0xFF15 -> 0xFF         // NR20 : unused, always 0xFF
            0xFF16 -> raw or 0x3F  // NR21 : bits 5-0 write-only
            0xFF17 -> raw          // NR22 : fully readable
            0xFF18 -> 0xFF         // NR23 : write-only
            // TODO: bit 7 reads as 1 due to or 0xBF mask, but it should reflect nothing — trigger bit is write-only
            0xFF19 -> raw or 0xBF  // NR24 : same mask as NR14
            0xFF1A -> raw or 0x7F  // NR30 : bits 6-0 always 1
            0xFF1B -> 0xFF         // NR31 : write-only
            0xFF1C -> raw or 0x9F  // NR32 : bits 4-0 and 7 always 1
            0xFF1D -> 0xFF         // NR33 : write-only
            // TODO: bit 7 reads as 1 due to or 0xBF mask, but it should reflect nothing — trigger bit is write-only
            0xFF1E -> raw or 0xBF  // NR34 : same mask as NR14
            0xFF1F -> 0xFF         // NR40 : unused, always 0xFF
//            0xFF20 -> raw or 0xFF  // NR41 : fully masked → always 0xFF
            0xFF20 -> 0xFF  // NR41 : write-only, reads as 0xFF
            0xFF21 -> raw          // NR42 : fully readable
            0xFF22 -> raw          // NR43 : fully readable
            // TODO: bit 7 reads as 1 due to or 0xBF mask, but it should reflect nothing — trigger bit is write-only
            0xFF23 -> raw or 0xBF  // NR44 : same mask as NR14
            0xFF24 -> raw          // NR50 : fully readable
            0xFF25 -> raw          // NR51 : fully readable
            0xFF26 -> raw or 0x70  // NR52 : bits 6-4 always 1
            in 0xFF27..0xFF2F -> 0xFF  // unused registers → read as 0xFF
            in 0xFF30..0xFF3F -> raw   // Wave RAM : fully readable
            else -> raw
        }
    }

    private fun triggerDmaTransfer(sourceHighByte: Int) {
        // OAM DMA: copies 160 bytes from (sourceHighByte * 0x100) to OAM (0xFE00-0xFE9F)
        // TODO: DMA should block CPU access to non-HRAM memory for 160 microseconds (640 T-cycles)
        val sourceBase = sourceHighByte shl 8
        for (i in 0 until 0xA0) {
            oam[i] = read(sourceBase + i)
        }
    }

    fun incDiv() {
        internalRam[0xFF04] = (internalRam[0xFF04] + 1) and 0xFF
    }

    fun readVram(address: Int): Int = vram[address]        // address 0x0000..0x1FFF
    fun writeVram(address: Int, value: Int) { vram[address] = value }

    fun readOam(address: Int): Int = oam[address]
    fun writeOam(address: Int, value: Int) { oam[address] = value }

    fun setIF(value: Int) = write(0xFF0F, value)

    companion object {
        /**
         * I/O register state left by the DMG boot ROM.
         * We skip the boot ROM and start at 0x0100, so we must reproduce this state.
         * Without it, LCDC=0 (LCD off) and games that poll LY==144 loop forever.
         */
        private fun initPostBootRegisters(ram: IntArray) {
            ram[0xFF05] = 0x00  // TIMA
            ram[0xFF06] = 0x00  // TMA
            ram[0xFF07] = 0x00  // TAC
            ram[0xFF10] = 0x80  // NR10
            ram[0xFF11] = 0xBF  // NR11
            ram[0xFF12] = 0xF3  // NR12
            ram[0xFF14] = 0xBF  // NR14
            ram[0xFF16] = 0x3F  // NR21
            ram[0xFF17] = 0x00  // NR22
            ram[0xFF19] = 0xBF  // NR24
            ram[0xFF1A] = 0x7F  // NR30
            ram[0xFF1B] = 0xFF  // NR31
            ram[0xFF1C] = 0x9F  // NR32
            ram[0xFF1E] = 0xBF  // NR33
            ram[0xFF20] = 0xFF  // NR41
            ram[0xFF21] = 0x00  // NR42
            ram[0xFF22] = 0x00  // NR43
            ram[0xFF23] = 0xBF  // NR44
            ram[0xFF24] = 0x77  // NR50
            ram[0xFF25] = 0xF3  // NR51
            ram[0xFF26] = 0xF1  // NR52
            ram[0xFF40] = 0x91  // LCDC — LCD on, BG on, tile data 0x8800, tile map 0x9800
            ram[0xFF41] = 0x85  // STAT — mode 1 (V-Blank)
            ram[0xFF42] = 0x00  // SCY
            ram[0xFF43] = 0x00  // SCX
            ram[0xFF44] = 0x00  // LY
            ram[0xFF45] = 0x00  // LYC
            ram[0xFF47] = 0xFC  // BGP
            ram[0xFF48] = 0xFF  // OBP0
            ram[0xFF49] = 0xFF  // OBP1
            ram[0xFF4A] = 0x00  // WY
            ram[0xFF4B] = 0x00  // WX
            ram[0xFFFF] = 0x00  // IE
        }
    }
}
