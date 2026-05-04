package com.wechantloup.gameboykmp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wechantloup.gameboykmp.apu.Apu
import com.wechantloup.gameboykmp.ui.GameBoyScreen
import com.wechantloup.gameboykmp.ui.GameBoyViewModel
import com.wechantloup.gameboykmp.ui.Palette
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.swing.JFileChooser
import javax.swing.SwingUtilities.invokeAndWait
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
@Preview
fun MainScreen() {
    val owner = checkNotNull(LocalViewModelStoreOwner.current)
    val viewModel = viewModel<GameBoyViewModel>(
        viewModelStoreOwner = owner,
        factory = GameBoyViewModel.Factory()
    )

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            startAudio(viewModel.audioSamplesChannel)
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val uiState by viewModel.stateFlow.collectAsState()

    var selectedPalette by remember { mutableStateOf<Palette>(Palette.Dmg) }
    var scale by remember { mutableIntStateOf(3) }

    Column {
        Row {
            Button(
                onClick = {
                    coroutineScope.launch {
                        val rom = pickRom()
                        rom?.let {
                            viewModel.loadRom(
                                romBytes = it.readBytes(),
                                romName = it.nameWithoutExtension,
                            )
                        }
                    }
                },
            ) {
                Text("Load ROM")
            }

            var paletteExpanded by remember { mutableStateOf(false) }

            Box {
                Text(selectedPalette.name, modifier = Modifier.clickable { paletteExpanded = true })
                DropdownMenu(expanded = paletteExpanded, onDismissRequest = { paletteExpanded = false }) {
                    Palette.all.forEach { palette ->
                        DropdownMenuItem(
                            text = { Text(palette.name) },
                            onClick = {
                                selectedPalette = palette
                                paletteExpanded = false
                            }
                        )
                    }
                }
            }

            var scaleExpanded by remember { mutableStateOf(false) }

            Box {
                Text("Scale: $scale", modifier = Modifier.clickable { scaleExpanded = true })
                DropdownMenu(expanded = scaleExpanded, onDismissRequest = { scaleExpanded = false }) {
                    for (availableScale in 1..6) {
                        DropdownMenuItem(
                            text = { Text("$availableScale") },
                            onClick = {
                                scale = availableScale
                                scaleExpanded = false
                            }
                        )
                    }
                }
            }
        }

        uiState.frameBuffer?.let {
            GameBoyScreen(
                frameBuffer = it,
                palette = selectedPalette,
                scale = scale,
            )
        }
    }
}

private suspend fun startAudio(audioSamplesChannel: Channel<FloatArray>) {
    val format = AudioFormat(
        /* sampleRate = */ 44100f,
        /* sampleSizeInBits = */ 16,
        /* channels = */ 1,
        /* signed = */ true,
        /* bigEndian = */ false
    )
    val line = AudioSystem.getSourceDataLine(format)
    val bufferSize = Apu.SAMPLES_PER_FRAME * 2 * 4 // Each sample on 2 bytes, 4 frames buffer
    line.open(format, bufferSize) // bufferSize en bytes
    line.start()

    val byteArray = ByteArray(Apu.SAMPLES_PER_FRAME * 2)
//    val debugFile = File("samples_debug.txt")
//    var frameCount = 0
    audioSamplesChannel.consumeEach { samples ->
//        if (frameCount < 100) {
//            samples.forEach { debugFile.appendText("$it\n") }
//            frameCount++
//        }
        samples.forEachIndexed { index, value ->
            val intValue = (value * 32767).toInt().coerceIn(-32768, 32767)
            byteArray[index * 2] = (intValue and 0xFF).toByte()        // byte bas
            byteArray[index * 2 + 1] = (intValue shr 8 and 0xFF).toByte()  // byte haut
        }
        line.write(byteArray, 0, Apu.SAMPLES_PER_FRAME * 2)
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
