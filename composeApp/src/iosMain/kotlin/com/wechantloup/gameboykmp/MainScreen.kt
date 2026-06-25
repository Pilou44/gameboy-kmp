package com.wechantloup.gameboykmp

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import com.wechantloup.gameboykmp.logger.Logger
import com.wechantloup.gameboykmp.ui.MobileScreen
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.openFilePicker
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

@Composable
fun MainScreen() {
    BoxWithConstraints {
        val isPortrait = maxHeight > maxWidth
        MobileScreen(
            isPortrait = isPortrait,
            pickRom = ::pickRom,
            startAudio = ::startAudio,
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
