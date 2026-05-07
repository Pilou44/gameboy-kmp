package com.wechantloup.gameboykmp.ui

import com.wechantloup.gameboykmp.ui.dialog.ClosedDialogState
import com.wechantloup.gameboykmp.ui.dialog.DialogState

data class MainState(
    val dialog: DialogState = ClosedDialogState,
)
