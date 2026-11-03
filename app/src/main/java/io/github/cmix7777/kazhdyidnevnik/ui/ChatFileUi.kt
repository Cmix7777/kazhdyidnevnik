package io.github.cmix7777.kazhdyidnevnik.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import io.github.cmix7777.kazhdyidnevnik.R
import io.github.cmix7777.kazhdyidnevnik.data.ChatFile
import io.github.cmix7777.kazhdyidnevnik.service.ChatFiles
import java.io.File

/** Размер файла по-человечески: 820 КБ, 3,4 МБ. */
internal fun fileSize(bytes: Long): String = when {
    bytes < 1_000 -> "$bytes Б"
    bytes < 1_000_000 -> "${(bytes + 500) / 1_000} КБ"
    else -> "%.1f МБ".format(bytes / 1_000_000f).replace('.', ',')
}

/** Короткая метка типа на значке: PDF, DOCX, ZIP. */
private fun extensionOf(name: String): String =
    name.substringAfterLast('.', "").take(4).uppercase().ifEmpty { "ФАЙЛ" }

/** Файл в ленте: значок с типом, имя, размер. Пока качается — кружок, если стёрт — подсказка про архив. */
@Composable
internal fun FileBubble(file: ChatFile, mine: Boolean, accent: Color, onReady: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version by ChatFiles.version.collectAsState()
    val ready = remember(version, file.id) { ChatFiles.exists(context, file.id) }
    val gone = remember(version, file.id) { ChatFiles.gone(context, file.id) }
    LaunchedEffect(ready, gone) { if (!ready && !gone) onReady() }
    val fg = if (mine) Color.White else Palette.Text
    val sub = if (mine) Color.White.copy(alpha = 0.72f) else Palette.TextFaint
    Row(
        modifier = modifier
            .widthIn(min = 200.dp, max = 280.dp)
            .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (mine) Color.White.copy(alpha = 0.2f) else accent.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                !ready && !gone -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = fg)
                else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(painterResource(R.drawable.ic_file), contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
                    Text(extensionOf(file.name), style = MaterialTheme.typography.labelSmall, color = fg, maxLines = 1)
                }
            }
        }
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(file.name, style = MaterialTheme.typography.bodyMedium, color = fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                text = when {
                    gone -> "стёрт из облака, верни из архива"
                    !ready -> "${fileSize(file.size)} · скачивается"
                    else -> "${fileSize(file.size)} · нажми, чтобы открыть"
                },
                style = MaterialTheme.typography.labelSmall,
                color = sub,
            )
        }
    }
}

/** Копия файла под его настоящим именем: так приложение, которое его откроет, видит правильное имя и тип. */
private fun sharedCopy(context: Context, file: ChatFile): File? {
    val source = ChatFiles.file(context, file.id)
    if (!source.exists()) return null
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    dir.listFiles()?.forEach { if (System.currentTimeMillis() - it.lastModified() > 24 * 3600_000L) it.delete() }
    val safe = file.name.replace(Regex("[\\\\/:*?\"<>|\\n\\r]"), "_").ifBlank { "файл" }
    val target = File(dir, safe)
    return runCatching {
        source.copyTo(target, overwrite = true)
        target
    }.getOrNull()
}

private fun mimeOf(file: ChatFile): String = file.mime.ifEmpty {
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.name.substringAfterLast('.', "").lowercase()) ?: "application/octet-stream"
}

/** Открыть файл в подходящем приложении (просмотр PDF, документы, архиватор). */
internal fun openChatFile(context: Context, file: ChatFile) {
    val copy = sharedCopy(context, file)
    if (copy == null) {
        Toast.makeText(context, "Файл ещё не скачался", Toast.LENGTH_SHORT).show()
        return
    }
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", copy)
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, mimeOf(file))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(Intent.createChooser(intent, file.name).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Нет приложения, которое открывает такой файл. Его можно сохранить на телефон", Toast.LENGTH_LONG).show()
    }
}

/** Сохранить файл туда, куда выбрал человек (Загрузки, Документы). */
internal fun saveChatFile(context: Context, file: ChatFile, target: Uri): Boolean = runCatching {
    val source = ChatFiles.file(context, file.id)
    context.contentResolver.openOutputStream(target)?.use { out -> source.inputStream().use { it.copyTo(out) } } != null
}.getOrDefault(false)

/** Тип файла для окна «сохранить как». */
internal fun saveMime(file: ChatFile): String = mimeOf(file)

/** «Сохранить как» с типом конкретного файла: (имя, тип) → куда сохранить. */
internal class SaveAs : androidx.activity.result.contract.ActivityResultContract<Pair<String, String>, Uri?>() {
    override fun createIntent(context: Context, input: Pair<String, String>): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input.second)
            .putExtra(Intent.EXTRA_TITLE, input.first)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == android.app.Activity.RESULT_OK) intent?.data else null
}
