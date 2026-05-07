package com.wechantloup.gameboykmp.repositories

import com.russhwolf.settings.PropertiesSettings
import com.russhwolf.settings.Settings
import com.wechantloup.gameboykmp.commands.CommandsMap
import com.wechantloup.gameboykmp.repository.SettingsRepository
import java.io.File
import java.util.Properties

class JvmSettingsRepository(settings: Settings): SettingsRepository(settings) {
    var commandsMap: CommandsMap?
        get() = getSerializable<CommandsMap?>(KEY_COMMANDS_MAP)
        set(value) = putSerializable(KEY_COMMANDS_MAP, value)

    companion object {
        private const val KEY_COMMANDS_MAP = "key_commands_map"

        private var _settings: Settings? = null

        fun getSettings(): Settings = _settings ?: createSettings().also { _settings = it }

        private fun createSettings(): Settings {
            val file = File(System.getProperty("user.home"), ".wechantloup/gbemulator/settings.properties")
            val properties = Properties().apply {
                if (file.exists()) file.inputStream().use { load(it) }
            }
            return PropertiesSettings(properties) {
                file.parentFile.mkdirs()
                file.outputStream().use { properties.store(it, "Game Boy Emulator settings") }
            }
        }
    }
}
