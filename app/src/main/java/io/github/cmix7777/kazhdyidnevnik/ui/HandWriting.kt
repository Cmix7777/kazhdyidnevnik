package io.github.cmix7777.kazhdyidnevnik.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.cmix7777.kazhdyidnevnik.service.ChatFiles
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Буква почерка: штрихи подряд, у каждой точки x, y (в высотах строчной буквы), толщина и время. */
@Serializable
data class HandGlyph(
    @SerialName("w") val width: Float,
    @SerialName("d") val duration: Int,
    @SerialName("s") val strokes: List<List<Float>>,
)

/** Почерк человека: буквы, нарисованные пальцем в прописях. */
@Serializable
data class HandFont(
    @SerialName("v") val format: Int = 1,
    @SerialName("g") val glyphs: Map<String, HandGlyph> = emptyMap(),
)

internal object HandFonts {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }
    private val cache = HashMap<String, HandFont>()

    fun encode(font: HandFont): ByteArray = json.encodeToString(HandFont.serializer(), font).toByteArray(Charsets.UTF_8)

    /** Почерк из файла переписки. null — файла ещё нет на телефоне. */
    fun load(context: Context, id: String?): HandFont? {
        if (id == null) return null
        synchronized(cache) { cache[id]?.let { return it } }
        val bytes = ChatFiles.read(context, id) ?: return null
        val font = runCatching { json.decodeFromString(HandFont.serializer(), bytes.toString(Charsets.UTF_8)) }.getOrNull() ?: return null
        synchronized(cache) { cache[id] = font }
        return font
    }

    fun isLetter(ch: Char): Boolean = ch.isLetterOrDigit()

    fun glyph(font: HandFont?, ch: Char): HandGlyph? {
        if (font == null) return null
        val key = ch.lowercaseChar().toString()
        return font.glyphs[key] ?: if (key == "ё") font.glyphs["е"] else null
    }

    /** Каких букв текста ещё нет в почерке. */
    fun missing(text: String, font: HandFont?): List<String> {
        val out = LinkedHashSet<String>()
        for (ch in text) {
            if (!isLetter(ch)) continue
            if (glyph(font, ch) == null) out += ch.lowercaseChar().toString()
        }
        return out.toList()
    }

    /** Самые нужные буквы, если человек хочет дописать почерк заранее. */
    const val COMMON = "оеаинтсрвлкмдпуяыьгзбчйхжшюцщэфъ"

    /** Весь алфавит и цифры: так они показаны в окне «Мой почерк». */
    const val ALPHABET = "абвгдеёжзийклмнопрстуфхцчшщъыьэюя0123456789"
}

private val INK = Color(0xFF26358C)
private val PAPER = Color(0xFFF4EFE3)
private val GRID = Color(0x2B4A6EC8)

/** Пропись: линейки и наклонные, как в школьной тетради. */
private fun DrawScope.ruling(xh: Float, base: Float) {
    val h = size.height
    val w = size.width
    var x = -h
    while (x < w + h) {
        drawLine(Color(0x1A4A5694), Offset(x, h), Offset(x + h * 0.36f, 0f), strokeWidth = 1f)
        x += 24.dp.toPx()
    }
    drawLine(Color(0x294A5694), Offset(0f, base - 2 * xh), Offset(w, base - 2 * xh), 1f)
    drawLine(Color(0x574A5694), Offset(0f, base - xh), Offset(w, base - xh), 1f)
    drawLine(Color(0x8C4A5694), Offset(0f, base), Offset(w, base), 1.4f * density)
    drawLine(Color(0x294A5694), Offset(0f, base + 0.9f * xh), Offset(w, base + 0.9f * xh), 1f)
    drawLine(Color(0x59DC505A), Offset(w - 30.dp.toPx(), 0f), Offset(w - 30.dp.toPx(), h), 1f)
}

private class Pt(val x: Float, val y: Float, val t: Long, val w: Float)

/** Кусок штриха, который заканчивается точкой [k]: кривая через середины соседних отрезков. */
private fun DrawScope.inkPiece(path: Path, xs: (Int) -> Float, ys: (Int) -> Float, ws: (Int) -> Float, count: Int, k: Int, base: Float, color: Color) {
    if (k == 0) {
        if (count == 1) drawCircle(color, base * 0.62f, Offset(xs(0), ys(0)))
        return
    }
    val ax = xs(k - 1)
    val ay = ys(k - 1)
    val bx = xs(k)
    val by = ys(k)
    val sx = if (k >= 2) (xs(k - 2) + ax) / 2f else ax
    val sy = if (k >= 2) (ys(k - 2) + ay) / 2f else ay
    val ex = (ax + bx) / 2f
    val ey = (ay + by) / 2f
    path.reset()
    path.moveTo(sx, sy)
    path.quadraticTo(ax, ay, ex, ey)
    if (k == count - 1) path.lineTo(bx, by)
    drawPath(path, color, style = Stroke(width = base * ws(k), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/**
 * Прописи: человек пишет пальцем буквы из [letters] по одной, из них собирается почерк.
 * Буква принимается сама через секунду после того, как палец поднят, или кнопкой «Дальше».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HandPadDialog(letters: List<String>, start: HandFont?, onDone: (HandFont) -> Unit, onCancel: () -> Unit) {
    val density = LocalDensity.current
    val xh = with(density) { 56.dp.toPx() }
    val base = with(density) { 150.dp.toPx() }
    val inkWidth = with(density) { 5.5.dp.toPx() }
    val glyphs = remember { HashMap(start?.glyphs.orEmpty()) }
    var index by remember { mutableIntStateOf(0) }
    val strokes = remember { mutableStateListOf<List<Pt>>() }
    var current by remember { mutableStateOf<List<Pt>?>(null) }
    var lifted by remember { mutableIntStateOf(0) }
    val queue = letters.ifEmpty { listOf("а") }

    fun accept() {
        if (strokes.isEmpty()) return
        val all = strokes.flatten()
        val minX = all.minOf { it.x }
        val maxX = all.maxOf { it.x }
        var t = 0f
        val flat = strokes.mapIndexed { si, s ->
            if (si > 0) t += 90f
            val out = ArrayList<Float>(s.size * 4)
            s.forEachIndexed { k, p ->
                if (k > 0) t += hypot(p.x - s[k - 1].x, p.y - s[k - 1].y) / xh / 9f * 1000f * (0.6f + 0.4f * p.w)
                out += round3((p.x - minX) / xh)
                out += round3((base - p.y) / xh)
                out += round3(p.w)
                out += t.roundToInt().toFloat()
            }
            out.toList()
        }
        glyphs[queue[index]] = HandGlyph(width = round3(max(0.18f, (maxX - minX) / xh)), duration = t.roundToInt() + 40, strokes = flat)
        strokes.clear()
        if (index + 1 >= queue.size) onDone(HandFont(glyphs = HashMap(glyphs))) else index++
    }

    LaunchedEffect(lifted) {
        if (lifted == 0 || strokes.isEmpty()) return@LaunchedEffect
        delay(1200)
        if (current == null) accept()
    }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Palette.Background)
                .border(1.dp, Palette.BorderStrong, RoundedCornerShape(26.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Твой почерк", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
            Text(
                "Буква ${index + 1} из ${queue.size}: «${queue[index]}». Пиши как обычно, по линейкам. Букву можно не дописывать: через секунду она примется сама.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextMuted,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(PAPER),
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                        .pointerInput(index) {
                            awaitPointerEventScope {
                                while (true) {
                                    val down = awaitPointerEvent()
                                    val first = down.changes.firstOrNull { it.pressed } ?: continue
                                    first.consume()
                                    val stroke = ArrayList<Pt>()
                                    stroke += Pt(first.position.x, first.position.y, first.uptimeMillis, 1f)
                                    current = stroke.toList()
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == first.id } ?: break
                                        change.consume()
                                        val last = stroke.last()
                                        val p = change.position
                                        val dist = hypot(p.x - last.x, p.y - last.y)
                                        if (dist >= 1.5f * density.density) {
                                            val v = dist / density.density / max(1L, change.uptimeMillis - last.t)
                                            val w = last.w * 0.55f + (1.3f - v * 0.45f).coerceIn(0.5f, 1.3f) * 0.45f
                                            stroke += Pt(p.x, p.y, change.uptimeMillis, w)
                                            current = stroke.toList()
                                        }
                                        if (!change.pressed) break
                                    }
                                    strokes += stroke.toList()
                                    current = null
                                    lifted++
                                }
                            }
                        },
                ) {
                    ruling(xh, base)
                    val path = Path()
                    val all = strokes + listOfNotNull(current)
                    for (s in all) {
                        for (k in s.indices) inkPiece(path, { s[it].x }, { s[it].y }, { s[it].w }, s.size, k, inkWidth, INK)
                    }
                }
                Text(
                    text = queue[index],
                    style = TextStyle(fontFamily = FontFamily.Cursive, fontSize = MaterialTheme.typography.titleLarge.fontSize, color = Color(0xFF4A5694)),
                    modifier = Modifier
                        .padding(10.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color(0x1A4A5694))
                        .padding(horizontal = 9.dp, vertical = 2.dp),
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                queue.forEachIndexed { i, letter ->
                    val done = i < index
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (done) PAPER else Palette.Chip)
                            .border(1.dp, if (i == index) Palette.Violet else Palette.Border, RoundedCornerShape(9.dp))
                            .clickable {
                                // Нажал на букву в очереди — пишешь её заново, остальные не трогаются.
                                if (i != index) {
                                    strokes.clear()
                                    index = i
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        val glyph = glyphs[letter]
                        if (i != index && glyph != null) {
                            GlyphThumb(glyph, Modifier.size(28.dp))
                        } else {
                            Text(letter, style = TextStyle(fontFamily = FontFamily.Cursive, fontSize = MaterialTheme.typography.titleMedium.fontSize), color = if (i == index) Palette.Text else Palette.TextFaint)
                        }
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.End),
            ) {
                GhostButton("Отмена", onClick = onCancel, small = true)
                GhostButton("Стереть", onClick = { strokes.clear() }, small = true)
                PillButton(if (index + 1 >= queue.size) "Готово" else "Дальше", onClick = { accept() })
            }
        }
    }
}

private fun round3(v: Float): Float = (v * 1000f).roundToInt() / 1000f

@Composable
private fun GlyphThumb(glyph: HandGlyph, modifier: Modifier) {
    Canvas(modifier) {
        val pts = glyph.strokes.flatMap { s -> s.chunked(4) }
        if (pts.isEmpty()) return@Canvas
        val minY = pts.minOf { it[1] }
        val maxY = pts.maxOf { it[1] }
        val h = max(0.3f, maxY - minY)
        val w = max(0.3f, glyph.width)
        val k = (size.minDimension - 4f) / max(h, w)
        val ox = (size.width - w * k) / 2f
        val oy = (size.height - h * k) / 2f
        val path = Path()
        for (s in glyph.strokes) {
            val n = s.size / 4
            for (i in 0 until n) {
                inkPiece(path, { ox + s[it * 4] * k }, { oy + (maxY - s[it * 4 + 1]) * k }, { s[it * 4 + 2] }, n, i, max(1.2f, k * 0.1f), INK)
            }
        }
    }
}

/** Буква или запасной знак в записке: где стоит и как чуть повёрнута (каждый раз немного иначе). */
private class NoteItem(
    val ch: String,
    val glyph: HandGlyph?,
    val x: Float,
    val line: Int,
    val scale: Float,
    val rotation: Float,
    val dy: Float,
    val wobble: Float,
    val t0: Float,
)

/**
 * Записка на листке в клетку, написанная почерком отправителя. Пока [animate], буквы пишутся
 * одна за другой в темпе руки, потом записка остаётся как есть. Буквы, которых нет в почерке,
 * пишутся рукописным шрифтом телефона.
 */
@Composable
internal fun HandNote(
    text: String,
    font: HandFont?,
    seed: Int,
    animate: Boolean,
    maxWidth: Dp,
    onWritten: () -> Unit,
    modifier: Modifier = Modifier,
    meta: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val xh = with(density) { 14.dp.toPx() }
    val lineH = xh * 2.75f
    val fallbackStyle = remember(xh) { TextStyle(fontFamily = FontFamily.Cursive, fontSize = with(density) { (xh * 2.1f).toSp() }, color = INK) }
    val maxW = with(density) { (maxWidth - 28.dp).toPx() }.coerceAtLeast(xh * 6)
    val layout = remember(text, font, seed, maxW) {
        val random = Random(seed)
        val gap = 0.2f * xh
        fun glyphOf(token: String): HandGlyph? = if (token.length == 1) HandFonts.glyph(font, token[0]) else null
        fun upper(token: String): Boolean = token.length == 1 && token[0].isUpperCase()
        fun advance(token: String): Float {
            val g = glyphOf(token)
            if (g != null) return g.width * (if (upper(token)) 1.3f else 1f) * xh + gap
            return measurer.measure(AnnotatedString(token), fallbackStyle).size.width + 0.06f * xh
        }
        // Текст делится на настоящие знаки (смайлик из нескольких кодов — один знак), а не на половинки символов.
        fun tokens(word: String): List<String> {
            val out = ArrayList<String>()
            val it = android.icu.text.BreakIterator.getCharacterInstance()
            it.setText(word)
            var start = it.first()
            var end = it.next()
            while (end != android.icu.text.BreakIterator.DONE) {
                out += word.substring(start, end)
                start = end
                end = it.next()
            }
            return out
        }
        val items = ArrayList<NoteItem>()
        var x = 0f
        var line = 0
        var maxX = 0f
        var t = 0f
        for (word in text.split(Regex("(?<=\\s)|(?=\\s)"))) {
            if (word.isEmpty()) continue
            if (word.isBlank()) {
                if (word.contains('\n')) {
                    line++
                    x = 0f
                } else if (x > 0f) {
                    x += 0.62f * xh
                }
                t += 110f
                continue
            }
            val parts = tokens(word)
            val width = parts.sumOf { advance(it).toDouble() }.toFloat()
            if (x > 0f && x + width > maxW) {
                line++
                x = 0f
            }
            for (ch in parts) {
                val g = glyphOf(ch)
                val scale = (if (upper(ch) && g != null) 1.3f else 1f) * (0.94f + random.nextFloat() * 0.12f)
                items += NoteItem(ch, g, x, line, scale, (random.nextFloat() - 0.5f) * 0.1f, (random.nextFloat() - 0.5f) * 0.1f * xh, random.nextFloat() * 6.28f, t)
                x += advance(ch)
                maxX = max(maxX, x)
                t += (g?.duration ?: 150) + 40f
            }
        }
        Triple(items, Size(max(maxX + 2f, xh * 3), (line + 1) * lineH + 0.35f * xh), t)
    }
    val (items, canvasSize, total) = layout
    var elapsed by remember(text, seed) { mutableFloatStateOf(if (animate) 0f else Float.MAX_VALUE) }
    LaunchedEffect(text, seed, animate) {
        if (!animate) {
            elapsed = Float.MAX_VALUE
            return@LaunchedEffect
        }
        val start = withFrameNanos { it }
        while (elapsed < total) {
            val now = withFrameNanos { it }
            elapsed = (now - start) / 1_000_000f
        }
        elapsed = Float.MAX_VALUE
        onWritten()
    }
    val tilt = remember(seed) { (Random(seed).nextFloat() - 0.5f) * 1.6f }
    Box(
        modifier = modifier
            .padding(top = 8.dp, bottom = 2.dp)
            .rotate(tilt)
            .drawWithContent {
                drawContent()
                // Полоска скотча сверху, поверх листка.
                rotate(-4f, pivot = Offset(size.width / 2f, 0f)) {
                    drawRect(Color(0x99FFE8A0), Offset(size.width / 2f - 25.dp.toPx(), -8.dp.toPx()), Size(50.dp.toPx(), 16.dp.toPx()))
                }
            }
            .clip(RoundedCornerShape(4.dp))
            .background(PAPER)
            .drawBehind {
                val step = 13.dp.toPx()
                var gx = step
                while (gx < size.width) {
                    drawLine(GRID, Offset(gx, 0f), Offset(gx, size.height), 1f)
                    gx += step
                }
                var gy = step
                while (gy < size.height) {
                    drawLine(GRID, Offset(0f, gy), Offset(size.width, gy), 1f)
                    gy += step
                }
            }
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 4.dp),
    ) {
        Column(horizontalAlignment = Alignment.End) {
            Canvas(modifier = Modifier.size(with(density) { canvasSize.width.toDp() }, with(density) { canvasSize.height.toDp() })) {
                val el = elapsed
                val path = Path()
                val ink = xh * 0.115f
                for (item in items) {
                    val local = el - item.t0
                    if (local < 0f) break
                    val oy = item.line * lineH + 2.05f * xh + item.dy
                    val g = item.glyph
                    if (g == null) {
                        translate(item.x + 1f, oy) {
                            rotate(item.rotation * 34f, pivot = Offset.Zero) {
                                val measured = measurer.measure(AnnotatedString(item.ch), fallbackStyle)
                                drawText(measured, topLeft = Offset(0f, -measured.firstBaseline))
                            }
                        }
                        continue
                    }
                    val cs = cos(item.rotation)
                    val sn = sin(item.rotation)
                    val s = item.scale
                    fun mapX(gx: Float, gy: Float): Float {
                        val px = gx * xh * s + sin(gy * 2.1f + item.wobble) * 0.03f
                        val py = -gy * xh * s
                        return item.x + 1f + px * cs - py * sn
                    }
                    fun mapY(gx: Float, gy: Float): Float {
                        val px = gx * xh * s
                        val py = -gy * xh * s
                        return oy + px * sn + py * cs
                    }
                    for (st in g.strokes) {
                        val n = st.size / 4
                        for (k in 0 until n) {
                            if (st[k * 4 + 3] > local) break
                            inkPiece(
                                path,
                                { mapX(st[it * 4], st[it * 4 + 1]) },
                                { mapY(st[it * 4], st[it * 4 + 1]) },
                                { st[it * 4 + 2] },
                                n,
                                k,
                                ink,
                                INK,
                            )
                        }
                    }
                }
            }
            meta()
        }
    }
}

/**
 * Мой почерк: все буквы клетками. Нажал на букву — переписываешь только её, остальные остаются как были.
 * Пустые клетки — букв ещё нет, их можно дописать разом.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HandFontDialog(font: HandFont?, onRedraw: (List<String>) -> Unit, onClose: () -> Unit) {
    val letters = HandFonts.ALPHABET.map { it.toString() }
    val missing = letters.filter { it != "ё" && font?.glyphs?.get(it) == null }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Palette.Background)
                .border(1.dp, Palette.BorderStrong, RoundedCornerShape(26.dp))
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Мой почерк", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
            Text(
                "Нажми на букву, чтобы переписать только её. Пустые клетки — буквы, которых ещё нет: вместо них в записке будет обычный рукописный шрифт.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextMuted,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                letters.forEach { letter ->
                    val glyph = font?.glyphs?.get(letter)
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (glyph != null) PAPER else Palette.Chip)
                            .border(1.dp, Palette.Border, RoundedCornerShape(10.dp))
                            .clickable { onRedraw(listOf(letter)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (glyph != null) {
                            GlyphThumb(glyph, Modifier.size(34.dp))
                        } else {
                            Text(letter, style = TextStyle(fontFamily = FontFamily.Cursive, fontSize = MaterialTheme.typography.titleMedium.fontSize), color = Palette.TextFaint)
                        }
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.End),
            ) {
                GhostButton("Закрыть", onClick = onClose, small = true)
                if (missing.isNotEmpty()) PillButton("Дописать недостающие: ${missing.size}", onClick = { onRedraw(missing) })
            }
        }
    }
}
