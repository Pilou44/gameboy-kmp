package com.wechantloup.gameboykmp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import com.wechantloup.gameboykmp.cartridge.SaveManager
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        SaveManager.init(this)
        FileKit.init(this)

        setContent {
            MaterialTheme {
                MainScreen()
            }
        }
    }
}
