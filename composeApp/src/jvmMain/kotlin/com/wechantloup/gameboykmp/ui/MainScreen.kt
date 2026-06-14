package com.wechantloup.gameboykmp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wechantloup.gameboykmp.apu.Apu
import com.wechantloup.gameboykmp.commands.JoypadControllerHolder
import com.wechantloup.gameboykmp.ui.dialog.Dialog
import com.wechantloup.gameboykmp.ui.dialog.OpenedDialogState
import com.wechantloup.gameboykmp.ui.dmg.DmgShell
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
    val gameBoyViewModel = viewModel<GameBoyViewModel>(
        viewModelStoreOwner = owner,
        factory = GameBoyViewModel.Factory(),
    )
    val mainViewModel = viewModel<MainViewModel>(
        viewModelStoreOwner = owner,
        factory = MainViewModel.Factory()
    )

    JoypadControllerHolder.instance.setTargetChannel(gameBoyViewModel.buttonChannel)

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            startAudio(gameBoyViewModel.audioSamplesChannel)
        }
    }

    val gameBoyState by gameBoyViewModel.stateFlow.collectAsState()
    val mainState by mainViewModel.stateFlow.collectAsState()

    val scale = remember { mutableIntStateOf(3) }

    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            Row {
                Layout(
                    content = {
                        DmgShell(
                            scale = scale.value,
                            screenBorderColor = gameBoyState.dmgPalette.colors.first(),
                        ) {
                            gameBoyState.coloredFrameBuffer?.let {
                                BitmapGameBoyScreen(
                                    coloredFrameBuffer = it,
                                    scale = scale.value,
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) { measurables, constraints ->
                    val shellPlaceable = measurables.first().measure(Constraints())

                    // In DmgShell, `scale` is a direct pixel multiplier (used via scale.toDp()),
                    // so all shell dimensions are already in pixels — no density conversion needed.
                    // The Game Boy screen center sits at scale×145 px from the shell top.
                    val screenCenterPx = scale.value * 145

                    // Shift the shell up if the screen center would fall below the window center.
                    // coerceAtMost(0): never push the shell down, only up or stay.
                    val yOffset = (constraints.maxHeight / 2 - screenCenterPx).coerceAtMost(0)

                    // Center the shell horizontally.
                    val xOffset = (constraints.maxWidth - shellPlaceable.width) / 2

                    layout(constraints.maxWidth, constraints.maxHeight) {
                        shellPlaceable.place(xOffset, yOffset)
                    }
                }

                val showCommands = {
                    mainViewModel.onIntent(MainIntent.ShowCommandsTable)
                }
                Commands(
                    selectedPalette = gameBoyState.dmgPalette,
                    scale = scale,
                    loadRom = gameBoyViewModel::loadRom,
                    setDmgPalette = gameBoyViewModel::setDmgPalette,
                    showCommands = showCommands,
                    modifier = Modifier
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.background),
                )
            }

            if (mainState.dialog is OpenedDialogState) {
                Dialog(mainState.dialog as OpenedDialogState)
            }
        }
    }
}

@Composable
private fun Commands(
    selectedPalette: Palette,
    scale: MutableState<Int>,
    loadRom: (romBytes: ByteArray, romName: String) -> Unit,
    setDmgPalette: (Palette) -> Unit,
    showCommands: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val currentDirectoryPath = remember { mutableStateOf<String?>(null) }
    Column(
        modifier = modifier
            .focusProperties { canFocus = false }
            .width(IntrinsicSize.Max),
    ) {
        Button(
            onClick = {
                coroutineScope.launch {
                    val rom = pickRom(currentDirectoryPath)
                    rom?.let {
                        loadRom(
                            it.readBytes(),
                            it.nameWithoutExtension,
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Load ROM")
        }

        var paletteExpanded by remember { mutableStateOf(false) }

        Box {
            OutlinedButton(
                onClick = { paletteExpanded = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(min = 150.dp),
            ) {
                Text(selectedPalette.displayName)
            }
            DropdownMenu(expanded = paletteExpanded, onDismissRequest = { paletteExpanded = false }) {
                Palette.entries.forEach { palette ->
                    DropdownMenuItem(
                        text = { Text(palette.displayName) },
                        onClick = {
                            setDmgPalette(palette)
                            paletteExpanded = false
                        }
                    )
                }
            }
        }

        var scaleExpanded by remember { mutableStateOf(false) }

        Box {
            OutlinedButton(
                onClick = { scaleExpanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Scale: ${scale.value}")
            }
            DropdownMenu(expanded = scaleExpanded, onDismissRequest = { scaleExpanded = false }) {
                for (availableScale in 1..6) {
                    DropdownMenuItem(
                        text = { Text("$availableScale") },
                        onClick = {
                            scale.value = availableScale
                            scaleExpanded = false
                        }
                    )
                }
            }
        }

        Button(
            onClick = showCommands,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Set controls")
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
    audioSamplesChannel.consumeEach { samples ->
        samples.forEachIndexed { index, value ->
            val intValue = (value * 32767).toInt().coerceIn(-32768, 32767)
            byteArray[index * 2] = (intValue and 0xFF).toByte()        // byte bas
            byteArray[index * 2 + 1] = (intValue shr 8 and 0xFF).toByte()  // byte haut
        }
        line.write(byteArray, 0, Apu.SAMPLES_PER_FRAME * 2)
    }
}

private suspend fun pickRom(
    currentDirectoryPath: MutableState<String?>,
): File? {
    return withContext(Dispatchers.IO) {
        var rom: File? = null
        invokeAndWait {
            val chooser = JFileChooser(currentDirectoryPath.value).apply {
                fileSelectionMode = JFileChooser.FILES_ONLY
                dialogTitle = "Pick ROM"
                fileFilter = FileNameExtensionFilter("Game Boy ROM (*.gb, *.gbc)", "gb", "gbc")
            }
            val result = chooser.showOpenDialog(null)

            if (result == JFileChooser.APPROVE_OPTION) {
                currentDirectoryPath.value = chooser.selectedFile.path
                rom = chooser.selectedFile.absoluteFile
            }
        }
        rom
    }
}
