package com.wechantloup.gameboykmp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wechantloup.gameboykmp.joypad.JoypadButton
import com.wechantloup.gameboykmp.joypad.JoypadEvent
import com.wechantloup.gameboykmp.logger.Logger
import com.wechantloup.gameboykmp.ui.BitmapGameBoyScreen
import com.wechantloup.gameboykmp.ui.GAME_BOY_SCREEN_HEIGHT_PX
import com.wechantloup.gameboykmp.ui.GAME_BOY_SCREEN_WIDTH_PX
import com.wechantloup.gameboykmp.ui.GameBoyState
import com.wechantloup.gameboykmp.ui.GameBoyViewModel
import com.wechantloup.gameboykmp.ui.Palette
import com.wechantloup.gameboykmp.ui.dmg.ABButtons
import com.wechantloup.gameboykmp.ui.dmg.DMG_SHELL_COLOR
import com.wechantloup.gameboykmp.ui.dmg.DPad
import com.wechantloup.gameboykmp.ui.dmg.StartSelectButton
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.nameWithoutExtension
import io.github.vinceglb.filekit.readBytes
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPlayerNode
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.Foundation.NSError
import platform.posix.memcpy
import kotlin.math.min

@Composable
fun MainScreen() {
    val owner = checkNotNull(LocalViewModelStoreOwner.current)
    val gameBoyViewModel = viewModel<GameBoyViewModel>(
        viewModelStoreOwner = owner,
        factory = GameBoyViewModel.Factory(),
    )
    val buttonChannel = gameBoyViewModel.buttonChannel

    val gameBoyState by gameBoyViewModel.stateFlow.collectAsState()

    val selectedPalette = remember { mutableStateOf(Palette.DMG) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> gameBoyViewModel.pause()
                Lifecycle.Event.ON_RESUME -> gameBoyViewModel.resume()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            startAudio(gameBoyViewModel.audioSamplesChannel)
        }
    }

    BoxWithConstraints {
        val isLandscape = maxWidth > maxHeight
        key(isLandscape) {
            if (isLandscape) {
                LandscapeEmulator(
                    gameBoyState = gameBoyState,
                    buttonChannel = buttonChannel,
                    selectedPalette = selectedPalette,
                    loadRom = gameBoyViewModel::loadRom,
                )
            } else {
                PortraitEmulator(
                    gameBoyState = gameBoyState,
                    buttonChannel = buttonChannel,
                    selectedPalette = selectedPalette,
                    loadRom = gameBoyViewModel::loadRom,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PortraitEmulator(
    gameBoyState: GameBoyState,
    buttonChannel: Channel<JoypadEvent>,
    selectedPalette: MutableState<Palette>,
    loadRom: (ByteArray, String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Game Boy Emulator") },
                actions = {
                    Button(
                        onClick = {
                            scope.launch {
                                val rom = pickRom()
                                rom?.let {
                                    loadRom(
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
        containerColor = Color(DMG_SHELL_COLOR),
    ) { paddingValues ->
        val density = LocalDensity.current
        var screenSize by remember { mutableStateOf(IntSize.Zero) }
        val scale by remember {
            derivedStateOf {
                val widthScale = screenSize.width / GAME_BOY_SCREEN_WIDTH_PX
                val heightScale = screenSize.height / 2 / GAME_BOY_SCREEN_HEIGHT_PX
                min(widthScale, heightScale)
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(paddingValues)
                .onSizeChanged { screenSize = it }
                .fillMaxSize(),
        ) {
            val screenBoxHeight = with (density) { (screenSize.height / 2).toDp() }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(screenBoxHeight),
            ) {
                Box(
                    modifier = Modifier
                        .size(
                            width = with(density) { (scale * GAME_BOY_SCREEN_WIDTH_PX).toDp() },
                            height = with(density) { (scale * GAME_BOY_SCREEN_HEIGHT_PX).toDp() },
                        )
                        .background(Color(selectedPalette.value.colors[0])),
                )
                gameBoyState.coloredFrameBuffer?.let {
                    BitmapGameBoyScreen(
                        coloredFrameBuffer = it,
                        scale = scale,
                    )
                }
            }
            Controls(
                buttonChannel = buttonChannel,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LandscapeEmulator(
    gameBoyState: GameBoyState,
    buttonChannel: Channel<JoypadEvent>,
    selectedPalette: MutableState<Palette>,
    loadRom: (ByteArray, String) -> Unit,
) {
    Scaffold(
        containerColor = Color(DMG_SHELL_COLOR),
    ) { paddingValues ->
        val density = LocalDensity.current
        var screenSize by remember { mutableStateOf(IntSize.Zero) }
        val scale by remember {
            derivedStateOf {
                val widthScale = screenSize.width / 2 / GAME_BOY_SCREEN_WIDTH_PX
                val heightScale = screenSize.height / GAME_BOY_SCREEN_HEIGHT_PX
                min(widthScale, heightScale)
            }
        }
        Row(
            modifier = Modifier
                .padding(paddingValues)
                .onSizeChanged { screenSize = it }
                .fillMaxSize(),
        ) {
            LeftControls(
                buttonChannel = buttonChannel,
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxHeight(),
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                Box(
                    modifier = Modifier
                        .size(
                            width = with(density) { (scale * GAME_BOY_SCREEN_WIDTH_PX).toDp() },
                            height = with(density) { (scale * GAME_BOY_SCREEN_HEIGHT_PX).toDp() },
                        )
                        .background(Color(selectedPalette.value.colors[0])),
                )
                gameBoyState.coloredFrameBuffer?.let {
                    BitmapGameBoyScreen(
                        coloredFrameBuffer = it,
                        scale = scale,
                    )
                }
            }
            RightControls(
                buttonChannel = buttonChannel,
                loadRom = loadRom,
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun Controls(
    buttonChannel: Channel<JoypadEvent>,
    modifier: Modifier = Modifier,
) {
    val onButtonPressed: (JoypadButton) -> Unit = {
        val event = JoypadEvent.Pressed(it)
        buttonChannel.trySend(event)
    }
    val onButtonReleased: (JoypadButton) -> Unit = {
        val event = JoypadEvent.Released(it)
        buttonChannel.trySend(event)
    }
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(top = 32.dp)
                .fillMaxHeight(),
        ) {
            Box(
                contentAlignment = Alignment.TopCenter,
                modifier = Modifier
                    .weight(1f),
            ) {
                DPad(
                    onDirectionPressed = { direction ->
                        onButtonPressed(direction)
                    },
                    onDirectionReleased = { direction ->
                        onButtonReleased(direction)
                    }
                )
            }
            Box(
                contentAlignment = Alignment.TopCenter,
                modifier = Modifier
                    .weight(1f),
            ) {
                ABButtons(
                    onButtonPressed = { onButtonPressed(it) },
                    onButtonReleased = { onButtonReleased(it) },
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth(),
        ) {
            StartSelectButton(
                label      = "SELECT",
//                fontFamily = nintendoFont,
                onPressed  = { onButtonPressed(JoypadButton.SELECT) },
                onReleased = { onButtonReleased(JoypadButton.SELECT) },
            )
            StartSelectButton(
                label      = "START",
//                fontFamily = nintendoFont,
                onPressed  = { onButtonPressed(JoypadButton.START) },
                onReleased = { onButtonReleased(JoypadButton.START) },
            )
        }
    }
}

@Composable
private fun LeftControls(
    buttonChannel: Channel<JoypadEvent>,
    modifier: Modifier = Modifier,
) {
    val onButtonPressed: (JoypadButton) -> Unit = {
        val event = JoypadEvent.Pressed(it)
        buttonChannel.trySend(event)
    }
    val onButtonReleased: (JoypadButton) -> Unit = {
        val event = JoypadEvent.Released(it)
        buttonChannel.trySend(event)
    }
    Box(modifier = modifier) {
        DPad(
            onDirectionPressed = { direction ->
                onButtonPressed(direction)
            },
            onDirectionReleased = { direction ->
                onButtonReleased(direction)
            },
            modifier = Modifier.align(Alignment.Center),
        )
        StartSelectButton(
            label      = "SELECT",
//                fontFamily = nintendoFont,
            onPressed  = { onButtonPressed(JoypadButton.SELECT) },
            onReleased = { onButtonReleased(JoypadButton.SELECT) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun RightControls(
    buttonChannel: Channel<JoypadEvent>,
    loadRom: (ByteArray, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val onButtonPressed: (JoypadButton) -> Unit = {
        val event = JoypadEvent.Pressed(it)
        buttonChannel.trySend(event)
    }
    val onButtonReleased: (JoypadButton) -> Unit = {
        val event = JoypadEvent.Released(it)
        buttonChannel.trySend(event)
    }
    Box(
        modifier = modifier,
    ) {
        Button(
            onClick = {
                scope.launch {
                    val rom = pickRom()
                    rom?.let {
                        loadRom(
                            rom.readBytes(),
                            rom.nameWithoutExtension,
                        )
                    }
                }
            },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Text("Load ROM")
        }
        ABButtons(
            onButtonPressed = { onButtonPressed(it) },
            onButtonReleased = { onButtonReleased(it) },
            modifier = Modifier.align(Alignment.Center),
        )
        StartSelectButton(
            label      = "START",
//                fontFamily = nintendoFont,
            onPressed  = { onButtonPressed(JoypadButton.START) },
            onReleased = { onButtonReleased(JoypadButton.START) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

private suspend fun pickRom(): PlatformFile? {
    return withContext(Dispatchers.IO) {
        FileKit.openFilePicker()
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private suspend fun startAudio(audioSamplesChannel: Channel<FloatArray>) {
    val session = AVAudioSession.sharedInstance()
    memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        session.setCategory(AVAudioSessionCategoryPlayback, error = error.ptr)
        error.value?.let {
            Logger.error("Audio", "AVAudioSession setCategory failed: ${it.localizedDescription}")
            return
        }
        session.setActive(true, error = error.ptr)
        error.value?.let {
            Logger.error("Audio", "AVAudioSession setActive failed: ${it.localizedDescription}")
            return
        }
    }

    val engine = AVAudioEngine()
    val playerNode = AVAudioPlayerNode()

    // Normalized float samples [-1.0, 1.0], mono, 44100 Hz — matches APU output
    val format = AVAudioFormat(
        standardFormatWithSampleRate = 44100.0,
        channels = 1u,
    )

    engine.attachNode(playerNode)
    engine.connect(playerNode, engine.mainMixerNode, format = format)

    memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        if (!engine.startAndReturnError(error.ptr)) {
            Logger.error("Audio", "AVAudioEngine start failed: ${error.value?.localizedDescription}")
            return
        }
    }

    // Do NOT call playerNode.play() here — no buffers are ready yet

    for (samples in audioSamplesChannel) {
        val frameCount = samples.size.toUInt()
        val buffer = AVAudioPCMBuffer(pCMFormat = format, frameCapacity = frameCount)
        buffer.frameLength = frameCount

        val dst = buffer.floatChannelData!![0]!!
        samples.usePinned { pinned ->
            memcpy(dst, pinned.addressOf(0), (samples.size * 4).toULong())
        }

        playerNode.scheduleBuffer(buffer, completionHandler = null)

        // Reference engine inside the loop to prevent Kotlin/Native GC from collecting it
        // while the coroutine is suspended between iterations
        if (!playerNode.isPlaying() && engine.running) {
            playerNode.play()
        }
    }
}
