package com.wechantloup.gameboykmp.utils

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

// public only for inline function call
val defaultSerializer: Json = Json {
    explicitNulls = false
    ignoreUnknownKeys = true
    isLenient = true
}

@Throws(SerializationException::class, IllegalArgumentException::class, Throwable::class)
inline fun <reified T> String.deserialize(): T = defaultSerializer.decodeFromString<T>(this)

@Throws(SerializationException::class, Throwable::class)
inline fun <reified T> T.serialize(): String = defaultSerializer.encodeToString(this)

@Throws(SerializationException::class, Throwable::class)
inline fun <reified T> T.deepCopy(): T {
    val serialized = defaultSerializer.encodeToString(this)
    return defaultSerializer.decodeFromString(serialized)
}
