package io.github.cmix7777.kazhdyidnevnik.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.cmix7777.kazhdyidnevnik.data.MockExams
import io.github.cmix7777.kazhdyidnevnik.data.PlanBlock
import io.github.cmix7777.kazhdyidnevnik.data.formatMinutes
import io.github.cmix7777.kazhdyidnevnik.formatTime

/** Карточка блока учёбы с круглой отметкой «сделал». */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlanBlockCard(
    block: PlanBlock,
    done: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var showLinks by rememberSaveable(block.title) { mutableStateOf(false) }
    var showTask by rememberSaveable(block.title) { mutableStateOf(false) }

    GlassCard(
        modifier = modifier,
        tone = if (done) CardTone.Dim else CardTone.Normal,
        contentPadding = PaddingValues(start = 6.dp, end = 16.dp, top = 8.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            CheckCircle(checked = done, onToggle = onToggle)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = "${formatTime(block.start)}–${formatTime(block.end)} · ${formatMinutes(block.minutes)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (done) Palette.TextFaint else Palette.Lavender,
                )
                Text(
                    text = block.title,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    color = if (done) Palette.TextMuted else Palette.Text,
                )
                Text(
                    text = block.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (done) Palette.TextFaint else Palette.TextMuted,
                )
            }
        }

        if (showTask && block.mockTask != null) {
            Text(
                text = MockExams.fullText(block.mockTask),
                modifier = Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Text,
            )
        }
        if (showLinks) {
            Column(
                modifier = Modifier.padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                block.links.forEach { link ->
                    Text(
                        text = link.title + "  ›",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { uriHandler.openUri(link.url) }
                            .padding(vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.Lavender,
                    )
                }
            }
        }

        FlowRow(
            modifier = Modifier.padding(start = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (block.mockTask != null) {
                GhostButton(
                    text = if (showTask) "Скрыть ТЗ" else "Показать ТЗ",
                    onClick = { showTask = !showTask },
                    small = true,
                )
            }
            if (block.links.isNotEmpty()) {
                GhostButton(
                    text = if (showLinks) "Скрыть материалы" else "Материалы",
                    onClick = { showLinks = !showLinks },
                    small = true,
                )
            }
            GhostButton(text = "Вопрос для Claude", onClick = { copyToClipboard(context, block.prompt) }, small = true)
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Вопрос для Claude", text))
    // На Android 13+ система сама показывает, что текст скопирован.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "Скопировано. Вставь в чат с Claude.", Toast.LENGTH_SHORT).show()
    }
}
