package com.wechantloup.gameboykmp.repository

import com.russhwolf.settings.Settings
import com.wechantloup.gameboykmp.serializer.deserialize
import com.wechantloup.gameboykmp.serializer.serialize

open class SettingsRepository(
    val settings: Settings,
) {
    inline fun <reified T> putSerializable(key: String, value: T) {
        settings.putString(key, value.serialize())
    }
    inline fun <reified T> getSerializable(key: String): T? {
        return settings.getStringOrNull(key)?.deserialize()
    }
}
