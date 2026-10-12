package io.github.cmix7777.kazhdyidnevnik

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import io.github.cmix7777.kazhdyidnevnik.notify.AppSettings
import io.github.cmix7777.kazhdyidnevnik.ui.AppRoot
import io.github.cmix7777.kazhdyidnevnik.ui.AppTheme
import io.github.cmix7777.kazhdyidnevnik.ui.ThemeSettings
import io.github.cmix7777.kazhdyidnevnik.ui.fx.EffectsSettings

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeSettings.load(AppSettings(this))
        EffectsSettings.load(AppSettings(this))
        val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        applyChrome(ThemeSettings.mode.isDark(systemDark))
        setContent {
            // Значки в строке состояния и цвет окна следуют за выбранной темой.
            val dark = ThemeSettings.isDark()
            DisposableEffect(dark) {
                applyChrome(dark)
                onDispose { }
            }
            AppTheme {
                AppRoot()
            }
        }
    }

    /** Строка состояния и навигации: светлые значки на тёмной теме, тёмные на светлой; фон окна под цвет экрана. */
    private fun applyChrome(dark: Boolean) {
        enableEdgeToEdge(
            statusBarStyle = if (dark) {
                SystemBarStyle.dark(Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            },
            navigationBarStyle = if (dark) {
                SystemBarStyle.dark(Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            },
        )
        window.setBackgroundDrawable(ColorDrawable(if (dark) 0xFF0A0610.toInt() else 0xFFF6F2FC.toInt()))
    }
}
