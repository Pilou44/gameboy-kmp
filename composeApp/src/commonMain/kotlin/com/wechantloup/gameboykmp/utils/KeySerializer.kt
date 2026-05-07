package com.wechantloup.gameboykmp.utils

import androidx.compose.ui.input.key.Key
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object KeySerializer : KSerializer<Key> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ComposeKey", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Key) {
        encoder.encodeLong(value.keyCode)
    }

    override fun deserialize(decoder: Decoder): Key {
        return Key(decoder.decodeLong())
    }
}
