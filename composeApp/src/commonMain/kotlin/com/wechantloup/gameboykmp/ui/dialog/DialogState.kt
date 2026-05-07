package com.wechantloup.gameboykmp.ui.dialog

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

sealed interface DialogState

data object ClosedDialogState : DialogState

data class OpenedDialogState(
    val onDismiss: () -> Unit,
    private val titleRes: StringResource? = null,
    private val titleArgs: List<Any> = emptyList(),
    private val confirmButtonTextRes: StringResource? = null,
    private val cancelButtonTextRes: StringResource? = null,
    private val messageRes: StringResource? = null,
    private val messageArgs: List<Any> = emptyList(),
    private val message: String? = null,
    val body: @Composable (() -> Unit)? = null,
    val canDismiss: Boolean = true,
    val onConfirmButtonClicked: (() -> Unit)? = null,
    val onCancelButtonClicked: (() -> Unit)? = null,
    val canConfirm: () -> Boolean = { true }
) : DialogState {
    val isConfirmButtonEnabled: Boolean
        get() = canConfirm()

    val displayTitle: String? @Composable get() = titleRes?.let {
        stringResource(
            resource = it,
            formatArgs = *titleArgs.toTypedArray(),
        )
    }

    val displayMessage: String?
        @Composable get() = message
            ?: messageRes?.let {
                stringResource(
                    resource = messageRes,
                    *messageArgs.toTypedArray()
                )
            }

    val displayConfirmButtonText: String? @Composable get() = confirmButtonTextRes?.let { stringResource(resource = it) }

    val displayCancelButtonText: String? @Composable get() = cancelButtonTextRes?.let { stringResource(resource = it) }
}
