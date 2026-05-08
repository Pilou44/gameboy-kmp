package com.wechantloup.gameboykmp

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wechantloup.gameboykmp.apu.Apu
import com.wechantloup.gameboykmp.joypad.JoypadEvent
import com.wechantloup.gameboykmp.ui.GAME_BOY_SCREEN_HEIGHT_PX
import com.wechantloup.gameboykmp.ui.GAME_BOY_SCREEN_WIDTH_PX
import com.wechantloup.gameboykmp.ui.GameBoyScreen
import com.wechantloup.gameboykmp.ui.GameBoyViewModel
import com.wechantloup.gameboykmp.ui.Palette
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.nameWithoutExtension
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val mockButtonChannel = Channel<JoypadEvent>()
    val owner = checkNotNull(LocalViewModelStoreOwner.current)
    val gameBoyViewModel = viewModel<GameBoyViewModel>(
        viewModelStoreOwner = owner,
        factory = GameBoyViewModel.Factory(mockButtonChannel),
    )

    val gameBoyState by gameBoyViewModel.stateFlow.collectAsState()

    val selectedPalette = remember { mutableStateOf<Palette>(Palette.Dmg) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            startAudio(gameBoyViewModel.audioSamplesChannel)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Game Boy Emulator") },
                actions = {
                    Button(
                        onClick = {
                            scope.launch {
                                val rom = loadRom()
                                rom?.let {
                                    gameBoyViewModel.loadRom(
                                        rom.readBytes(),
                                        rom.nameWithoutExtension,
                                    )
                                }
                            }
                        }
                    ) {
                        Text("Load ROM")
                    }
                }
            )
        },
    ) { paddingValues ->
        Column( // ToDo should be row for landscape
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
        ) {
            var screenSize by remember { mutableStateOf(IntSize.Zero) }
            val scale by remember {
                derivedStateOf {
                    val widthScale = screenSize.width / GAME_BOY_SCREEN_WIDTH_PX
                    val heightScale = screenSize.height / GAME_BOY_SCREEN_HEIGHT_PX
                    min(widthScale, heightScale)
                }
            }
            Box(
                contentAlignment = Alignment.TopCenter,
                modifier = Modifier
                    .onSizeChanged { screenSize = it }
                    .fillMaxSize(),
            ) {
                gameBoyState.frameBuffer?.let {
                    GameBoyScreen(
                        frameBuffer = it,
                        palette = selectedPalette.value,
                        scale = scale,
                    )
                }
            }
        }
    }
}

private suspend fun loadRom(): PlatformFile? {
    return withContext(Dispatchers.IO) {
        FileKit.openFilePicker()
    }
}

private suspend fun startAudio(audioSamplesChannel: Channel<FloatArray>) {
    val sampleRate = 44100
    val bufferSize = maxOf(
        AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        ),
        Apu.SAMPLES_PER_FRAME * 4 * 4 // Float = 4 bytes, 4 frames de buffer
    )

    val audioTrack = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
        )
        .setBufferSizeInBytes(bufferSize)
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()

    audioTrack.play()

    try {
        audioSamplesChannel.consumeEach { samples ->
            val written = audioTrack.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            if (written < 0) {
                // Gérer l'erreur, ex: log ou recréer l'AudioTrack
            }
        }
    } finally {
        audioTrack.stop()
        audioTrack.release()
    }
}
