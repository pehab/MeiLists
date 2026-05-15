package de.haberland.meilists

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.haberland.meilists.ui.screens.MainScreen
import de.haberland.meilists.ui.theme.MeiListsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MeiListsTheme {
                MainScreen()
            }
        }
    }
}