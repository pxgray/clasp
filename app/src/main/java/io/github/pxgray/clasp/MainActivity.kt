package io.github.pxgray.clasp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import io.github.pxgray.clasp.ui.components.LocalItemFiles
import io.github.pxgray.clasp.ui.navigation.ClaspNavHost
import io.github.pxgray.clasp.ui.theme.ClaspTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as ClaspApplication).container
        setContent {
            ClaspTheme {
                CompositionLocalProvider(LocalItemFiles provides container.itemFiles) {
                    ClaspNavHost()
                }
            }
        }
    }
}
