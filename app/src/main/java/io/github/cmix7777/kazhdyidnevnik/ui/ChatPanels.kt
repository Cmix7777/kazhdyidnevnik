package io.github.cmix7777.kazhdyidnevnik.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.cmix7777.kazhdyidnevnik.R
import io.github.cmix7777.kazhdyidnevnik.service.ChatArchive
import io.github.cmix7777.kazhdyidnevnik.service.ChatFiles
import io.github.cmix7777.kazhdyidnevnik.service.StickerStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ---------- смайлики и стикеры ----------

private val EMOJI = listOf(
    "❤️", "😂", "🥰", "😘", "😍", "😊", "🤗", "😉", "😁", "🥹", "😭", "😢", "😮", "😳", "😴", "🤔",
    "😎", "🙃", "😏", "😅", "🤭", "😇", "🥺", "😤", "😡", "🤯", "🤒", "🥶", "🤤", "😋", "🫠", "🫶",
    "👍", "👎", "👌", "🙏", "👏", "🤝", "✌️", "🤞", "💪", "👋", "🙌", "🤙", "👀", "💋", "🫂", "💅",
    "💜", "💙", "🩷", "🧡", "💛", "💚", "🤍", "🖤", "💔", "❣️", "💕", "💞", "💓", "💗", "💖", "💘",
    "🔥", "✨", "⭐", "🌙", "☀️", "🌈", "⚡", "❄️", "🌧️", "🌸", "🌹", "🌷", "🍀", "🌿", "🌊", "☕",
    "🍕", "🍔", "🍟", "🍣", "🍰", "🍫", "🍿", "🍓", "🍎", "🍌", "🥐", "🍜", "🍷", "🍺", "🧋", "🍭",
    "🎉", "🎁", "🎂", "🎈", "🎶", "🎮", "📚", "✏️", "💻", "📱", "🚗", "🚌", "🏠", "🛏️", "⏰", "💤",
    "🐶", "🐱", "🐰", "🦊", "🐻", "🐼", "🐨", "🐸", "🐧", "🦋", "🐝", "🐢", "🦄", "🐳", "🐾", "🙈",
    "✅", "❌", "❓", "❗", "💯", "🆗", "🔔", "📌", "📍", "🗓️", "💸", "🎯", "🏆", "🧠", "💡", "🫡",
)

private fun recentEmoji(context: Context): List<String> =
    context.getSharedPreferences("chat-ui", Context.MODE_PRIVATE).getString("recent-emoji", "").orEmpty()
        .split('\u0001').filter { it.isNotEmpty() }

private fun rememberEmoji(context: Context, emoji: String) {
    val list = (listOf(emoji) + recentEmoji(context).filter { it != emoji }).take(16)
    context.getSharedPreferences("chat-ui", Context.MODE_PRIVATE).edit().putString("recent-emoji", list.joinToString("\u0001")).apply()
}

/** Панель под полем ввода: смайлики и свои стикеры. Открывается кнопкой в поле вместо клавиатуры. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun EmojiPanel(onEmoji: (String) -> Unit, onSticker: (File) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var drawing by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    val stickersVersion by StickerStore.version.collectAsState()
    val stickers = remember(stickersVersion) { StickerStore.list(context) }
    val recent = remember(tab) { recentEmoji(context) }
    val photoSticker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) { StickerStore.fromPhoto(context, uri) }
                if (bytes != null) withContext(Dispatchers.IO) { StickerStore.add(context, bytes) }
            }
        }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
            .background(Palette.Background.copy(alpha = 0.94f)),
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Смайлики", "Стикеры").forEachIndexed { i, title ->
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (tab == i) Palette.Text else Palette.TextFaint,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (tab == i) Palette.Chip else Color.Transparent)
                        .clickable { tab = i }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
        if (tab == 0) {
            val all = (recent + EMOJI).distinct()
            LazyVerticalGrid(columns = GridCells.Adaptive(46.dp), modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
                items(all) { emoji ->
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                rememberEmoji(context, emoji)
                                onEmoji(emoji)
                            },
                        contentAlignment = Alignment.Center,
                    ) { Text(emoji, fontSize = 26.sp) }
                }
            }
        } else {
            LazyVerticalGrid(columns = GridCells.Adaptive(88.dp), modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                item {
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .border(1.5.dp, Palette.BorderStrong, RoundedCornerShape(18.dp))
                            .clickable { addMenu = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(R.drawable.ic_add), contentDescription = "Новый стикер", tint = Palette.Lavender, modifier = Modifier.size(28.dp))
                        DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                            DropdownMenuItem(text = { Text("Из фото") }, onClick = {
                                addMenu = false
                                photoSticker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            })
                            DropdownMenuItem(text = { Text("Нарисовать") }, onClick = {
                                addMenu = false
                                drawing = true
                            })
                        }
                    }
                }
                items(stickers, key = { it.name }) { file ->
                    var menu by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .padding(6.dp)
                            .combinedClickable(onClick = { onSticker(file) }, onLongClick = { menu = true }),
                    ) {
                        StickerImage(file, Modifier.fillMaxSize())
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Убрать из моих") }, onClick = {
                                menu = false
                                StickerStore.remove(file)
                            })
                        }
                    }
                }
            }
            if (stickers.isEmpty()) {
                Text(
                    "Сделай стикер из своего фото или нарисуй пальцем. Её стикеры можно сохранить себе долгим нажатием.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                )
            }
        }
    }
    if (drawing) {
        DrawStickerDialog(
            onDone = { bitmap ->
                drawing = false
                scope.launch { withContext(Dispatchers.IO) { StickerStore.add(context, StickerStore.encode(bitmap)) } }
            },
            onCancel = { drawing = false },
        )
    }
}

@Composable
private fun StickerImage(file: File, modifier: Modifier) {
    val image by produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(file)) { d, _, _ -> d.setTargetSize(240, 240) }.asImageBitmap()
            }.getOrNull()
        }
    }
    image?.let { Image(bitmap = it, contentDescription = "Стикер", contentScale = ContentScale.Fit, modifier = modifier) }
}

/** Стикер в ленте: без пузыря. */
@Composable
internal fun StickerView(id: String, onReady: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version by ChatFiles.version.collectAsState()
    val image by produceState(ChatFiles.cached(id, 420)?.asImageBitmap(), id, version) {
        if (value == null) value = withContext(Dispatchers.IO) { ChatFiles.decode(context, id, 420) }?.asImageBitmap()
        if (value == null && !ChatFiles.gone(context, id)) onReady()
    }
    Box(modifier = modifier.size(150.dp), contentAlignment = Alignment.Center) {
        val bitmap = image
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = "Стикер", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        } else if (ChatFiles.gone(context, id)) {
            Text("Стикер стёрт из облака", style = MaterialTheme.typography.labelSmall, color = Palette.TextMuted)
        } else {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Palette.Lavender)
        }
    }
}

private class DrawnStroke(val color: Color, val width: Float, val points: List<Offset>)

/** Нарисовать стикер пальцем: несколько цветов, белая обводка добавляется сама. */
@Composable
private fun DrawStickerDialog(onDone: (Bitmap) -> Unit, onCancel: () -> Unit) {
    val colors = listOf(Color(0xFFF472B6), Color(0xFFA855F7), Color(0xFF4FA3E8), Color(0xFFF5B86B), Color(0xFF7DD3B0), Color(0xFF1E1B2E))
    var color by remember { mutableStateOf(colors[0]) }
    val strokes = remember { mutableStateListOf<DrawnStroke>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var side by remember { mutableStateOf(1f) }
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Palette.Background)
                .border(1.dp, Palette.BorderStrong, RoundedCornerShape(26.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Нарисуй стикер", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF2A2338))
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val down = awaitPointerEvent()
                                val first = down.changes.firstOrNull { it.pressed } ?: continue
                                val pts = ArrayList<Offset>()
                                pts += first.position
                                current = pts.toList()
                                while (true) {
                                    val e = awaitPointerEvent()
                                    val c = e.changes.firstOrNull { it.id == first.id } ?: break
                                    c.consume()
                                    pts += c.position
                                    current = pts.toList()
                                    if (!c.pressed) break
                                }
                                strokes += DrawnStroke(color, 9f * density, pts.toList())
                                current = emptyList()
                            }
                        }
                    },
            ) {
                side = size.width
                fun line(points: List<Offset>, c: Color, w: Float) {
                    if (points.isEmpty()) return
                    val path = Path().apply {
                        moveTo(points[0].x, points[0].y)
                        for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
                    }
                    drawPath(path, c, style = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    if (points.size == 1) drawCircle(c, w / 2f, points[0])
                }
                strokes.forEach { line(it.points, Color.White, it.width + 14f * density) }
                line(current, Color.White, 9f * density + 14f * density)
                strokes.forEach { line(it.points, it.color, it.width) }
                line(current, color, 9f * density)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                colors.forEach { c ->
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(if (c == color) 3.dp else 1.dp, if (c == color) Color.White else Palette.Border, CircleShape)
                            .clickable { color = c },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.End)) {
                GhostButton("Отмена", onClick = onCancel, small = true)
                GhostButton("Стереть", onClick = { strokes.clear() }, small = true)
                PillButton("Готово", onClick = {
                    if (strokes.isNotEmpty()) onDone(renderSticker(strokes.toList(), side))
                })
            }
        }
    }
}

private fun renderSticker(strokes: List<DrawnStroke>, side: Float): Bitmap {
    val size = StickerStore.SIDE
    val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(out)
    val k = size / side.coerceAtLeast(1f)
    fun draw(stroke: DrawnStroke, color: Int, extra: Float) {
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = (stroke.width + extra) * k
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
        }
        val path = android.graphics.Path()
        stroke.points.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x * k, p.y * k) else path.lineTo(p.x * k, p.y * k) }
        if (stroke.points.size == 1) path.lineTo(stroke.points[0].x * k + 0.1f, stroke.points[0].y * k)
        canvas.drawPath(path, paint)
    }
    val outline = strokes.first().width * 1.6f
    strokes.forEach { draw(it, android.graphics.Color.WHITE, outline) }
    strokes.forEach { draw(it, it.color.toArgb(), 0f) }
    return out
}

// ---------- фото перед отправкой ----------

/** Фото с камеры или из галереи перед отправкой: можно подписать или передумать. */
@Composable
internal fun PhotoPreviewDialog(uri: Uri, onSend: (String) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
                    val k = minOf(1f, 1600f / maxOf(info.size.width, info.size.height))
                    if (k < 1f) d.setTargetSize((info.size.width * k).toInt().coerceAtLeast(1), (info.size.height * k).toInt().coerceAtLeast(1))
                }.asImageBitmap()
            }.getOrNull()
        }
    }
    var caption by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            val bitmap = image
            if (bitmap != null) {
                Image(bitmap = bitmap, contentDescription = "Фото", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            } else {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
            }
            GlassIconButton(
                icon = R.drawable.ic_close,
                contentDescription = "Не отправлять",
                onClick = onCancel,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 44.dp, start = 14.dp),
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 34.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicTextField(
                        value = caption,
                        onValueChange = { caption = it },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                        cursorBrush = SolidColor(Color.White),
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (caption.isEmpty()) Text("Подпись", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.5f))
                            inner()
                        },
                    )
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Palette.Violet)
                        .clickable(enabled = image != null) { onSend(caption.trim()) },
                    contentAlignment = Alignment.Center,
                ) { Icon(painterResource(R.drawable.ic_send), contentDescription = "Отправить", tint = Color.White, modifier = Modifier.size(20.dp)) }
            }
        }
    }
}

// ---------- архив ----------

/** Архив переписки: сколько весят вложения, сохранить всё одним файлом или вернуть из файла. */
@Composable
internal fun ArchiveDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var note by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val used by produceState(0L) { value = withContext(Dispatchers.IO) { ChatFiles.usedBytes(context) } }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                val result = withContext(Dispatchers.IO) { ChatArchive.export(context, uri) }
                busy = false
                note = if (result == null) "Не получилось сохранить архив" else "Сохранено: ${result.files} вложений, ${megabytes(result.bytes)}"
            }
        }
    }
    val load = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                val result = withContext(Dispatchers.IO) { ChatArchive.import(context, uri) }
                busy = false
                note = when (result) {
                    null -> "Это не архив переписки"
                    0 -> "Всё из архива уже есть на телефоне"
                    else -> "Вернулось вложений и стикеров: $result"
                }
            }
        }
    }
    Dialog(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(26.dp))
                .background(Palette.Background)
                .border(1.dp, Palette.BorderStrong, RoundedCornerShape(26.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Архив переписки", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
            Text(
                "Фото, голосовые и кружочки на телефоне занимают ${megabytes(used)}. В облаке они лежат только до доставки, " +
                    "потом стираются, чтобы оно не переполнялось. Сохрани архив перед сменой телефона или переустановкой: " +
                    "сообщения вернутся из облака сами, а вложения — из архива.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextMuted,
            )
            note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.Lavender) }
            if (busy) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Palette.Lavender)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                GhostButton("Загрузить", onClick = { load.launch(arrayOf("application/zip", "application/octet-stream")) }, enabled = !busy, small = true)
                PillButton("Сохранить", onClick = { save.launch("Каждыйдневник-переписка-${java.time.LocalDate.now()}.zip") }, enabled = !busy)
            }
        }
    }
}

private fun megabytes(bytes: Long): String = if (bytes < 1_000_000) "${bytes / 1000} КБ" else "%.1f МБ".format(bytes / 1_000_000f)
