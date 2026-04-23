package com.wechantloup.gameboykmp

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wechantloup.gameboykmp.ui.GameBoyScreen
import com.wechantloup.gameboykmp.ui.GameBoyViewModel
import javax.swing.JFileChooser
import javax.swing.SwingUtilities.invokeAndWait
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
@Preview
fun MainScreen() {
    val owner = checkNotNull(LocalViewModelStoreOwner.current)
    val viewModel = viewModel<GameBoyViewModel>(
        viewModelStoreOwner = owner,
        factory = GameBoyViewModel.Factory()
    )

    val coroutineScope = rememberCoroutineScope()
    val uiState by viewModel.stateFlow.collectAsState()

    if (uiState.frameBuffer == null) {
        Button(
            onClick = {
                coroutineScope.launch {
                    val rom = pickRom()
                    rom?.let {
                        viewModel.loadRom(it.readBytes())
                    }
                }
            },
        ) {
            Text("Load ROM")
        }
    } else {
        GameBoyScreen(frameBuffer = uiState.frameBuffer!!)
    }
}

private suspend fun pickRom(): File? {
    return withContext(Dispatchers.IO) {
        var rom: File? = null
        invokeAndWait {
            val chooser = JFileChooser().apply {
                fileSelectionMode = JFileChooser.FILES_ONLY
                dialogTitle = "Pick ROM"
                fileFilter = FileNameExtensionFilter("Sprite files (*.gb)", "gb")
            }
            val result = chooser.showOpenDialog(null)

            if (result == JFileChooser.APPROVE_OPTION) {
                rom = chooser.selectedFile.absoluteFile
            }
        }
        rom
    }
}
