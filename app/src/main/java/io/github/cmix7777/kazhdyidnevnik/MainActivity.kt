package io.github.cmix7777.kazhdyidnevnik

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.cmix7777.kazhdyidnevnik.ui.AppRoot
import io.github.cmix7777.kazhdyidnevnik.ui.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Приложение всегда тёмное: светлые значки в строке состояния и навигации.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            AppTheme {
                AppRoot()
            }
        }
    }
}
