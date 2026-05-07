package com.wechantloup.gameboykmp.ui

sealed interface MainIntent {
    data object ShowCommandsTable: MainIntent
}
