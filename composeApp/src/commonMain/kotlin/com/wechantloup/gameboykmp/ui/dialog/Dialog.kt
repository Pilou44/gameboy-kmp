package com.wechantloup.gameboykmp.ui.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.max

@Composable
fun Dialog(
    state: OpenedDialogState,
) {
    Dialog(
        onDismissRequest = state.onDismiss,
        title = state.displayTitle,
        message = state.displayMessage,
        body = state.body,
        confirmButtonText = state.displayConfirmButtonText,
        dismissButtonText = state.displayCancelButtonText,
        canDismiss = state.canDismiss,
        onConfirmButtonClicked = state.onConfirmButtonClicked,
        onDismissButtonClicked = state.onCancelButtonClicked,
        isConfirmButtonEnabled = state.isConfirmButtonEnabled,
    )
}

@Composable
fun Dialog(
    onDismissRequest: () -> Unit,
    titleResource: StringResource? = null,
    messageResource: StringResource? = null,
    confirmButtonText: String? = null,
    onConfirmButtonClicked: (() -> Unit)? = null,
    dismissButtonText: String? = null,
    onDismissButtonClicked: (() -> Unit)? = null,
    canDismiss: Boolean = true,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        title = titleResource?.let { stringResource(resource = titleResource) },
        message = messageResource?.let { stringResource(resource = messageResource) },
        confirmButtonText = confirmButtonText,
        onConfirmButtonClicked = onConfirmButtonClicked,
        dismissButtonText = dismissButtonText,
        onDismissButtonClicked = onDismissButtonClicked,
        canDismiss = canDismiss,
    )
}

@Composable
fun Dialog(
    onDismissRequest: () -> Unit,
    titleResource: StringResource? = null,
    body: @Composable (() -> Unit)? = null,
    confirmButtonText: String? = null,
    onConfirmButtonClicked: (() -> Unit)? = null,
    dismissButtonText: String? = null,
    onDismissButtonClicked: (() -> Unit)? = null,
    canDismiss: Boolean = true,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        title = titleResource?.let { stringResource(resource = titleResource) },
        body = body,
        confirmButtonText = confirmButtonText,
        onConfirmButtonClicked = onConfirmButtonClicked,
        dismissButtonText = dismissButtonText,
        onDismissButtonClicked = onDismissButtonClicked,
        canDismiss = canDismiss,
    )
}

@Composable
fun Dialog(
    onDismissRequest: () -> Unit,
    title: String? = null,
    message: String? = null,
    body: @Composable (() -> Unit)? = null,
    confirmButtonText: String? = null,
    onConfirmButtonClicked: (() -> Unit)? = null,
    dismissButtonText: String? = null,
    onDismissButtonClicked: (() -> Unit)? = null,
    canDismiss: Boolean = true,
    isConfirmButtonEnabled: Boolean = true,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = canDismiss,
            dismissOnClickOutside = canDismiss,
//            usePlatformDefaultWidth = false,
//            decorFitsSystemWindows = true,
        ),
    ) {
        Surface(
            color = Color.Transparent,
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
                    .fillMaxWidth()
                    .clip(
                        RoundedCornerShape(16.dp)
                    )
                    .background(MaterialTheme.colorScheme.background) // ToDo good color ?
                    .padding(
                        top = 32.dp,
                        bottom = if (confirmButtonText != null || dismissButtonText != null) 8.dp else 16.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {

                if (title != null) {
                    Text(
                        text = title,
                        textAlign = TextAlign.Center,
//                        style = MaterialTheme.typography.title3,
//                        color = LocalExtendedColors.current.content.OnKind().default,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                    )
                }

                if (body != null || message != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .verticalScroll(rememberScrollState())
                            .weight(weight = 1f, fill = false),
                    ) {
                        if (body != null) {
                            body()
                        } else if (message != null) {
                            Text(
                                text = message,
//                                style = MaterialTheme.typography.body,
//                                color = LocalExtendedColors.current.content.OnKind().default,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                val confirmButton = @Composable {
                    if (confirmButtonText != null) {
                        TextButton(
                            onClick = onConfirmButtonClicked ?: onDismissRequest,
                            enabled = isConfirmButtonEnabled
                        ) {
                            Text(
                                confirmButtonText,
//                                style = MaterialTheme.typography.button2,
//                                color = LocalExtendedColors.current.content.OnKind().primary,
                            )
                        }
                    }
                }
                val dismissButton = @Composable {
                    if (dismissButtonText != null) {
                        TextButton(
                            onClick = onDismissButtonClicked ?: onDismissRequest,
                        ) {
                            Text(
                                dismissButtonText,
//                                style = MaterialTheme.typography.button2,
//                                color = LocalExtendedColors.current.content.OnKind().primary,
                            )
                        }
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    AlertDialogFlowRow(
                        mainAxisSpacing = 8.dp,
                        crossAxisSpacing = 12.dp
                    ) {
                        dismissButton()
                        confirmButton()
                    }
                }
            }
        }
    }
}

/**
 * Simple clone of FlowRow that arranges its children in a horizontal flow with limited
 * customization.
 */
@Composable
private fun AlertDialogFlowRow(
    mainAxisSpacing: Dp,
    crossAxisSpacing: Dp,
    content: @Composable () -> Unit,
) {
    Layout(content) { measurables, constraints ->
        val sequences = mutableListOf<List<Placeable>>()
        val crossAxisSizes = mutableListOf<Int>()
        val crossAxisPositions = mutableListOf<Int>()

        var mainAxisSpace = 0
        var crossAxisSpace = 0

        val currentSequence = mutableListOf<Placeable>()
        var currentMainAxisSize = 0
        var currentCrossAxisSize = 0

        val childConstraints = Constraints(maxWidth = constraints.maxWidth)

        // Return whether the placeable can be added to the current sequence.
        fun canAddToCurrentSequence(placeable: Placeable) =
            currentSequence.isEmpty() || currentMainAxisSize + mainAxisSpacing.roundToPx() +
                placeable.width <= constraints.maxWidth

        // Store current sequence information and start a new sequence.
        fun startNewSequence() {
            if (sequences.isNotEmpty()) {
                crossAxisSpace += crossAxisSpacing.roundToPx()
            }
            sequences += currentSequence.toList()
            crossAxisSizes += currentCrossAxisSize
            crossAxisPositions += crossAxisSpace

            crossAxisSpace += currentCrossAxisSize
            mainAxisSpace = max(mainAxisSpace, currentMainAxisSize)

            currentSequence.clear()
            currentMainAxisSize = 0
            currentCrossAxisSize = 0
        }

        for (measurable in measurables) {
            // Ask the child for its preferred size.
            val placeable = measurable.measure(childConstraints)

            // Start a new sequence if there is not enough space.
            if (!canAddToCurrentSequence(placeable)) startNewSequence()

            // Add the child to the current sequence.
            if (currentSequence.isNotEmpty()) {
                currentMainAxisSize += mainAxisSpacing.roundToPx()
            }
            currentSequence.add(placeable)
            currentMainAxisSize += placeable.width
            currentCrossAxisSize = max(currentCrossAxisSize, placeable.height)
        }

        if (currentSequence.isNotEmpty()) startNewSequence()

        val layoutWidth = if (constraints.maxWidth != Constraints.Infinity) {
            constraints.maxWidth
        } else {
            max(mainAxisSpace, constraints.minWidth)
        }
        val layoutHeight = max(crossAxisSpace, constraints.minHeight)

        layout(layoutWidth, layoutHeight) {
            sequences.fastForEachIndexed { i, placeables ->
                val childrenMainAxisSizes = IntArray(placeables.size) { j ->
                    placeables[j].width +
                        if (j < placeables.lastIndex) mainAxisSpacing.roundToPx() else 0
                }
                val arrangement = Arrangement.Bottom
                // TODO(soboleva): rtl support
                // Handle vertical direction
                val mainAxisPositions = IntArray(childrenMainAxisSizes.size) { 0 }
                with(arrangement) {
                    arrange(layoutWidth, childrenMainAxisSizes, mainAxisPositions)
                }
                placeables.fastForEachIndexed { j, placeable ->
                    placeable.place(
                        x = mainAxisPositions[j],
                        y = crossAxisPositions[i]
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun DialogComposablePreview() {
    MaterialTheme {
        Dialog(
            title = "Welcome",
            body = {
                Column {
                    Text(
                        text = "Hello description text block",
//                        style = MaterialTheme.typography.bodyCondensed,
//                        color = LocalExtendedColors.current.content.OnKind().default,
                    )
                    Button(
                        onClick = {},
                    ) {
                        Text("OK")
                    }
                }
            },
            onDismissRequest = {},
        )
    }
}

@Preview
@Composable
private fun DialogPreview() {
    MaterialTheme {
        Dialog(
            title = "Warning",
            message = "Be careful !",
            onDismissRequest = {},
        )
    }
}
