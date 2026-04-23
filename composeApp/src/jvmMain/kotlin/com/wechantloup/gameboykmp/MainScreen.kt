package com.wechantloup.gameboykmp

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.wechantloup.gameboykmp.cartridge.CartridgeFactory
import com.wechantloup.gameboykmp.cpu.Cpu
import com.wechantloup.gameboykmp.memory.Memory
import javax.swing.JFileChooser
import javax.swing.SwingUtilities.invokeAndWait
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
@Preview
fun MainScreen() {
    val coroutineScope = rememberCoroutineScope()
    var romBytes by remember { mutableStateOf<ByteArray?>(null) }

    if (romBytes == null) {
        Button(
            onClick = {
                coroutineScope.launch {
                    val rom = pickRom()
                    rom?.let {
                        romBytes = rom.readBytes()
                    }
                }
            },
        ) {
            Text("Load ROM")
        }
    } else {
        val cartridge = remember(romBytes) { CartridgeFactory.create(romBytes!!) }
        val memory = remember(cartridge) { Memory(cartridge) }
        val cpu = remember(memory) { Cpu(memory).also { it.reset() } }

        LaunchedEffect(cpu) {
            withContext(Dispatchers.Default) {
                while (true) {
                    cpu.step()
                }
            }
        }

        Text("Running!")
    }
}

private suspend fun pickRom(): File? {
    return withContext(Dispatchers.IO) {
        var rom: File? = null
        invokeAndWait {
            val chooser = JFileChooser().apply {
                fileSelectionMode = JFileChooser.FILES_ONLY
                dialogTitle = "Pick ROM"
                fileFilter = FileNameExtensionFilter("Sprite files (*.gb)", "gb")
            }
            val result = chooser.showOpenDialog(null)

            if (result == JFileChooser.APPROVE_OPTION) {
                rom = chooser.selectedFile.absoluteFile
            }
        }
        rom
    }
}
