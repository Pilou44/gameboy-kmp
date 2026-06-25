package com.wechantloup.gameboykmp

import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import com.wechantloup.gameboykmp.apu.Apu
import com.wechantloup.gameboykmp.ui.MobileScreen
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.openFilePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@Composable
fun MainScreen() {
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation != ORIENTATION_LANDSCAPE

    MobileScreen(
        isPortrait = isPortrait,
        pickRom = ::pickRom,
        startAudio = ::startAudio,
    )
}

private suspend fun pickRom(): PlatformFile? {
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
        Apu.SAMPLES_PER_FRAME * 4 * 4 // Float = 4 bytes, buffer of 4 frames
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
        // receiveCatching() is a suspend function → responds to coroutine cancellation
        while (currentCoroutineContext().isActive) {
            val result = audioSamplesChannel.receiveCatching()
            val samples = result.getOrNull() ?: break

            var offset = 0
            // Non-blocking loop: the coroutine can be cancelled between writes
            while (offset < samples.size && currentCoroutineContext().isActive) {
                val written = audioTrack.write(
                    samples,
                    offset,
                    samples.size - offset,
                    AudioTrack.WRITE_NON_BLOCKING
                )
                when {
                    written > 0 -> offset += written
                    written == 0 -> delay(1) // buffer full, yield to scheduler
                    else -> break // write error
                }
            }
        }
    } finally {
        audioTrack.stop()
        audioTrack.release()
    }
}
