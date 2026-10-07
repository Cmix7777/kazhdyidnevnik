package io.github.cmix7777.kazhdyidnevnik.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

/** Карточка блока учёбы с галочкой «сделал». */
@Composable
fun PlanBlockCard(
    block: PlanBlock,
    done: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var showLinks by rememberSaveable(block.title) { mutableStateOf(false) }
    var showTask by rememberSaveable(block.title) { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (done) colors.surfaceContainerLowest else colors.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Checkbox(checked = done, onCheckedChange = { onToggle() })
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "${formatTime(block.start)}–${formatTime(block.end)} · ${formatMinutes(block.minutes)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.primary,
                    )
                    Text(
                        text = block.title,
                        style = MaterialTheme.typography.titleMedium,
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                        color = if (done) colors.onSurfaceVariant else colors.onSurface,
                    )
                    Text(
                        text = block.detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }

            if (showTask && block.mockTask != null) {
                Text(
                    text = MockExams.fullText(block.mockTask),
                    modifier = Modifier.padding(start = 12.dp, top = 6.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (showLinks) {
                Column(modifier = Modifier.padding(start = 4.dp)) {
                    block.links.forEach { link ->
                        TextButton(onClick = { uriHandler.openUri(link.url) }) { Text(link.title) }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (block.mockTask != null) {
                    TextButton(onClick = { showTask = !showTask }) {
                        Text(if (showTask) "Скрыть ТЗ" else "Показать ТЗ")
                    }
                }
                if (block.links.isNotEmpty()) {
                    TextButton(onClick = { showLinks = !showLinks }) {
                        Text(if (showLinks) "Скрыть материалы" else "Материалы")
                    }
                }
                TextButton(onClick = { copyToClipboard(context, block.prompt) }) {
                    Text("Вопрос для Claude")
                }
            }
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
