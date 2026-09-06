package io.github.geekdex.subout

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.geekdex.subout.presentation.ui.MainScreen
import io.github.geekdex.subout.ui.theme.SuboutTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SuboutTheme {
                MainScreen()
            }
        }
    }
}