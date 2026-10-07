package io.github.cmix7777.kazhdyidnevnik.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Яркий фиолетовый баннер «Вышла новая версия». Ничего не показывает, если обновлять нечего. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UpdateBanner(extras: ExtrasViewModel, modifier: Modifier = Modifier) {
    val state = extras.update
    val release = state.release ?: return
    val shape = RoundedCornerShape(24.dp)
    val white = Color.White
    val soft = Color.White.copy(alpha = 0.78f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFFA855F7), Color(0xFF7C3AED), Color(0xFF4C1D95))))
            .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Вышла версия ${release.versionName}",
            style = MaterialTheme.typography.titleLarge,
            color = white,
        )
        if (release.notes.isNotBlank()) {
            Text(text = release.notes, style = MaterialTheme.typography.bodyMedium, color = soft)
        }
        when (state) {
            is UpdateState.Available -> {
                val size = if (release.sizeBytes > 0) " · ${(release.sizeBytes + 500_000) / 1_000_000} МБ" else ""
                PillButton(text = "Обновить$size", onClick = { extras.downloadAndInstall() })
            }
            is UpdateState.Downloading -> {
                if (state.progress < 0f) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = white,
                        trackColor = white.copy(alpha = 0.25f),
                    )
                    Text("Скачиваю…", style = MaterialTheme.typography.bodyMedium, color = soft)
                } else {
                    LinearProgressIndicator(
                        progress = { state.progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = white,
                        trackColor = white.copy(alpha = 0.25f),
                    )
                    Text(
                        "Скачиваю… ${(state.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = soft,
                    )
                }
            }
            is UpdateState.NeedsPermission -> {
                Text(
                    text = "Нужно один раз разрешить Каждыйдневнику устанавливать обновления: " +
                        "открой настройки, включи переключатель, вернись и нажми «Установить».",
                    style = MaterialTheme.typography.bodyMedium,
                    color = soft,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GhostButton(text = "Открыть настройки", onClick = { extras.openInstallPermission() })
                    PillButton(text = "Установить", onClick = { extras.installAgain() })
                }
            }
            is UpdateState.Ready -> {
                Text(
                    text = "Файл скачан. Если окно установки не появилось или ты его закрыл, нажми ещё раз.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = soft,
                )
                PillButton(text = "Установить", onClick = { extras.installAgain() })
            }
            is UpdateState.Failed -> {
                Text(
                    text = "Не получилось: ${state.message}. Если дома GitHub не открывается, " +
                        "попробуй через мобильный интернет.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = white,
                )
                PillButton(text = "Повторить", onClick = { extras.downloadAndInstall() })
            }
            else -> Unit
        }
    }
}
