package io.github.cmix7777.kazhdyidnevnik.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Карточка «Вышла новая версия» с кнопкой обновления. Ничего не показывает, если обновлять нечего. */
@Composable
fun UpdateBanner(extras: ExtrasViewModel, modifier: Modifier = Modifier) {
    val state = extras.update
    val release = state.release ?: return
    val colors = MaterialTheme.colorScheme

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Вышла версия ${release.versionName}",
                style = MaterialTheme.typography.titleMedium,
                color = colors.onPrimaryContainer,
            )
            if (release.notes.isNotBlank()) {
                Text(
                    text = release.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onPrimaryContainer,
                )
            }
            when (state) {
                is UpdateState.Available -> {
                    val size = if (release.sizeBytes > 0) " (${(release.sizeBytes + 500_000) / 1_000_000} МБ)" else ""
                    Button(onClick = { extras.downloadAndInstall() }) { Text("Обновить$size") }
                }
                is UpdateState.Downloading -> {
                    if (state.progress < 0f) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Note("Скачиваю…")
                    } else {
                        LinearProgressIndicator(
                            progress = { state.progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Note("Скачиваю… ${(state.progress * 100).toInt()}%")
                    }
                }
                is UpdateState.NeedsPermission -> {
                    Note(
                        "Нужно один раз разрешить Каждыйдневнику устанавливать обновления: " +
                            "открой настройки, включи переключатель, вернись и нажми «Установить».",
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { extras.openInstallPermission() }) { Text("Открыть настройки") }
                        Button(onClick = { extras.installAgain() }) { Text("Установить") }
                    }
                }
                is UpdateState.Ready -> {
                    Note("Файл скачан. Если окно установки не появилось или ты его закрыл, нажми ещё раз.")
                    Button(onClick = { extras.installAgain() }) { Text("Установить") }
                }
                is UpdateState.Failed -> {
                    Text(
                        text = "Не получилось: ${state.message}. Если дома GitHub не открывается, " +
                            "попробуй через мобильный интернет.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.error,
                    )
                    Button(onClick = { extras.downloadAndInstall() }) { Text("Повторить") }
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
    )
}
