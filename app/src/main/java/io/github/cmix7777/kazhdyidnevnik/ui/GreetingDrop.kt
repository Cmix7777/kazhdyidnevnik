package io.github.cmix7777.kazhdyidnevnik.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.cmix7777.kazhdyidnevnik.data.GreetingDrops
import io.github.cmix7777.kazhdyidnevnik.data.Person
import io.github.cmix7777.kazhdyidnevnik.notify.AppSettings
import io.github.cmix7777.kazhdyidnevnik.service.PartnerSync
import io.github.cmix7777.kazhdyidnevnik.ui.fx.EffectsSettings
import io.github.cmix7777.kazhdyidnevnik.ui.fx.Haptics
import io.github.cmix7777.kazhdyidnevnik.ui.fx.rememberEffectTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

/**
 * Капля привета, отправка: зажимаешь имя второго человека, под пальцем вырастает капля (около секунды),
 * отпускаешь — капля улетает в его имя. Потом три секунды можно отменить, и только после этого она уходит.
 * Случайное касание ничего не отправит: капля должна дорасти до конца.
 */
@Stable
class GreetingDropState(
    private val scope: CoroutineScope,
    /** Поставить каплю в очередь: она уйдёт через три секунды, если её не отменить. */
    private val send: (Long) -> Unit,
    /** Отменить каплю, которая ещё ждёт отправки. */
    private val cancelSend: () -> Unit,
) {
    enum class Phase { Idle, Holding, Flying, Waiting }

    var phase by mutableStateOf(Phase.Idle)
        private set

    /** Палец (в координатах всего экрана). */
    var finger by mutableStateOf(Offset.Zero)
        private set

    /** Куда летит капля: середина имени. */
    var target by mutableStateOf(Offset.Zero)
        private set

    /** Откуда капля полетела. */
    var from by mutableStateOf(Offset.Zero)
        private set

    val grow = Animatable(0f)
    val fly = Animatable(0f)
    val ripple = Animatable(0f)

    /** Когда начались три секунды на отмену. */
    var waitingSince by mutableLongStateOf(0L)
        private set

    private var job: Job? = null

    fun start(position: Offset, name: Offset, context: Context) {
        if (phase != Phase.Idle) return
        phase = Phase.Holding
        finger = position
        target = name
        job?.cancel()
        job = scope.launch {
            grow.snapTo(0f)
            var step = 0
            grow.animateTo(1f, tween(GROW_MS, easing = LinearEasing)) {
                val now = (value * 4f).toInt()
                if (now > step) {
                    step = now
                    Haptics.tension(context, 0.25f + value * 0.55f)
                }
            }
        }
    }

    fun move(position: Offset) {
        if (phase == Phase.Holding) finger = position
    }

    /** Палец отпущен: если капля доросла — бросок, если нет — капля тихо сжимается. */
    fun release(context: Context, dropOffset: Float) {
        if (phase != Phase.Holding) return
        job?.cancel()
        if (grow.value < 0.999f) {
            shrink()
            return
        }
        from = finger + Offset(0f, dropOffset)
        phase = Phase.Flying
        Haptics.dropThrow(context)
        job = scope.launch {
            fly.snapTo(0f)
            fly.animateTo(1f, tween(FLY_MS, easing = FastOutSlowInEasing))
            phase = Phase.Waiting
            waitingSince = System.currentTimeMillis()
            send(waitingSince)
            launch {
                ripple.snapTo(0f)
                ripple.animateTo(1f, tween(500))
            }
            delay(GreetingDrops.UNDO_MS)
            phase = Phase.Idle
            grow.snapTo(0f)
        }
    }

    fun cancelHold() {
        if (phase != Phase.Holding) return
        job?.cancel()
        shrink()
    }

    /** Отменить уже брошенную каплю: она никуда не уйдёт. */
    fun undo() {
        if (phase != Phase.Waiting && phase != Phase.Flying) return
        cancelSend()
        job?.cancel()
        job = null
        phase = Phase.Idle
        scope.launch {
            grow.snapTo(0f)
            fly.snapTo(0f)
        }
    }

    private fun shrink() {
        job = scope.launch {
            grow.animateTo(0f, tween(180))
            phase = Phase.Idle
        }
    }

    companion object {
        const val GROW_MS = 650
        const val FLY_MS = 550
    }
}

/** Состояние отправки: брошенная капля ждёт три секунды в [DropOutbox] и уходит второму телефону. */
@Composable
fun rememberGreetingDrop(): GreetingDropState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember {
        GreetingDropState(scope, send = { at -> DropOutbox.schedule(context, at) }, cancelSend = DropOutbox::cancel)
    }
}

/**
 * Очередь капли на отправку. Живёт дольше экрана: если во время трёх секунд на отмену повернуть
 * телефон или сменится тема, капля всё равно уйдёт.
 */
object DropOutbox {
    private val scope = MainScope()
    private var job: Job? = null

    fun schedule(context: Context, at: Long) {
        val app = context.applicationContext
        job?.cancel()
        job = scope.launch {
            delay(GreetingDrops.UNDO_MS)
            val settings = AppSettings(app)
            settings.sentDrops = GreetingDrops.withSent(settings.sentDrops, at)
            PartnerSync.syncNow(app, force = true)
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }
}

/** Насколько ниже пальца висит капля, чтобы её не закрывал палец. */
private val HangBelow = 44.dp

/**
 * Имя второго человека в переключателе: обычное нажатие листает страницу, а долгое удержание
 * выращивает каплю привета. [enabled] = false (телефоны не связаны) — только листание.
 */
@Composable
fun Modifier.greetingDropSource(state: GreetingDropState, enabled: Boolean, onTap: () -> Unit): Modifier {
    val context = LocalContext.current
    val holder = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val hang = with(LocalDensity.current) { HangBelow.toPx() }
    if (!enabled) return this.clickable(role = Role.Tab, onClick = onTap)
    fun root(local: Offset): Offset = holder[0]?.takeIf { it.isAttached }?.localToRoot(local) ?: local
    return this
        .onGloballyPositioned { holder[0] = it }
        .semantics(mergeDescendants = true) {
            role = Role.Tab
            onClick {
                onTap()
                true
            }
        }
        .pointerInput(state) {
            detectTapGestures(onLongPress = { }, onTap = { onTap() })
        }
        .pointerInput(state) {
            detectDragGesturesAfterLongPress(
                onDragStart = { local ->
                    val size = holder[0]?.size
                    val center = if (size != null) root(Offset(size.width / 2f, size.height / 2f)) else root(local)
                    state.start(root(local), center, context)
                },
                onDrag = { change, _ -> state.move(root(change.position)) },
                onDragEnd = { state.release(context, hang) },
                onDragCancel = { state.cancelHold() },
            )
        }
}

/** Рисует каплю под пальцем и в полёте, и плашку «капля полетела … · отменить». */
@Composable
fun GreetingDropLayer(state: GreetingDropState, owner: Person) {
    val top = TogetherColors.top(owner)
    val bottom = TogetherColors.bottom(owner)
    val animate = EffectsSettings.animate(LocalContext.current)
    val time = rememberEffectTime(fps = 60, running = animate && state.phase == GreetingDropState.Phase.Holding)
    Box(modifier = Modifier.fillMaxSize()) {
        if (state.phase != GreetingDropState.Phase.Idle) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val dp = density
                when (state.phase) {
                    GreetingDropState.Phase.Holding -> {
                        val center = state.finger + Offset(0f, HangBelow.toPx())
                        val r = (5f + 13f * state.grow.value) * dp
                        // Тонкая ниточка от пальца к капле, пока она растёт.
                        drawLine(
                            brush = Brush.verticalGradient(listOf(top.copy(alpha = 0f), top.copy(alpha = 0.7f)), startY = state.finger.y, endY = center.y),
                            start = state.finger,
                            end = center - Offset(0f, r * 0.8f),
                            strokeWidth = 2.5f * dp * state.grow.value,
                        )
                        drawBlob(center, r, r, time() * 6f, 1.2f * dp, top, bottom, 1f)
                    }
                    GreetingDropState.Phase.Flying -> {
                        val f = state.fly.value
                        val r = 18f * dp * (1f - 0.6f * f)
                        for (k in 7 downTo 1) {
                            val fk = f - k * 0.035f
                            if (fk <= 0f) continue
                            val p = arc(state.from, state.target, fk, 90f * dp)
                            drawCircle(lerpColor(top, bottom, fk).copy(alpha = 0.55f * (1f - k / 8f)), r * 0.45f * (1f - k / 8f), p)
                        }
                        drawBlob(arc(state.from, state.target, f, 90f * dp), r, r, f * 10f, 0.6f * dp, top, bottom, 1f)
                    }
                    GreetingDropState.Phase.Waiting -> {
                        val w = state.ripple.value
                        if (w < 1f) {
                            drawCircle(
                                color = top.copy(alpha = 0.8f * (1f - w)),
                                radius = (6f + 30f * w) * dp,
                                center = state.target,
                                style = Stroke(width = 2f * dp),
                            )
                        }
                    }
                    GreetingDropState.Phase.Idle -> Unit
                }
            }
        }
        AnimatedVisibility(
            visible = state.phase == GreetingDropState.Phase.Waiting,
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 64.dp),
        ) {
            UndoPill(text = GreetingDrops.sentText(owner.partner), since = state.waitingSince, color = top, onUndo = state::undo)
        }
    }
}

@Composable
private fun UndoPill(text: String, since: Long, color: Color, onUndo: () -> Unit) {
    // Полоска внизу плашки тает за три секунды: столько осталось на отмену.
    val left = remember(since) { Animatable(1f) }
    LaunchedEffect(since) { left.animateTo(0f, tween(GreetingDrops.UNDO_MS.toInt(), easing = LinearEasing)) }
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Palette.Background.copy(alpha = 0.94f))
            .border(1.dp, Palette.BorderStrong, CircleShape)
            .drawBehind {
                val h = 2.dp.toPx()
                drawRect(color.copy(alpha = 0.8f), Offset(size.height / 2, size.height - h * 2), Size((size.width - size.height) * left.value, h))
            }
            .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = Palette.Text)
        Text(
            text = "Отменить",
            style = MaterialTheme.typography.labelLarge,
            color = Palette.Lavender,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onUndo)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

/** Точка на дуге броска: капля сначала поднимается, потом входит в имя. */
private fun arc(a: Offset, b: Offset, f: Float, lift: Float): Offset {
    val c = Offset(a.x + (b.x - a.x) * 0.2f, a.y + (b.y - a.y) * 0.5f - lift)
    val u = 1f - f
    return a * (u * u) + c * (2f * u * f) + b * (f * f)
}

private fun lerpColor(a: Color, b: Color, f: Float): Color = lerp(a, b, f.coerceIn(0f, 1f))

/** Живая капля: край чуть колышется, внутри градиент цветов человека и блик. */
private fun DrawScope.drawBlob(
    center: Offset,
    rx: Float,
    ry: Float,
    phase: Float,
    wobble: Float,
    top: Color,
    bottom: Color,
    alpha: Float,
    noise: Float = 0f,
    seed: Float = 0f,
) {
    if (rx <= 0.5f || ry <= 0.5f || alpha <= 0.01f) return
    val path = Path()
    val points = 64
    for (i in 0..points) {
        val a = i * 2f * PI.toFloat() / points
        val w = sin(a * 3f + phase) * wobble + sin(a * 5f - phase * 0.7f) * wobble * 0.66f +
            (sin(a * 3f + 1.3f + seed) * 0.5f + sin(a * 5f + 0.7f + seed * 1.7f) * 0.3f + sin(a * 7f + 2.1f + seed * 0.6f) * 0.2f) * noise
        val p = Offset(center.x + cos(a) * (rx + w), center.y + sin(a) * (ry + w))
        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    path.close()
    val glow = maxOf(rx, ry) * 1.9f
    drawCircle(
        brush = Brush.radialGradient(listOf(top.copy(alpha = 0.35f * alpha), Color.Transparent), center = center, radius = glow),
        radius = glow,
        center = center,
    )
    drawPath(
        path,
        brush = Brush.verticalGradient(
            listOf(top.copy(alpha = 0.95f * alpha), bottom.copy(alpha = 0.95f * alpha)),
            startY = center.y - ry,
            endY = center.y + ry,
        ),
    )
    val spot = Offset(center.x - rx * 0.35f, center.y - ry * 0.45f)
    drawCircle(
        brush = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f * alpha), Color.Transparent), center = spot, radius = minOf(rx, ry) * 0.5f),
        radius = minOf(rx, ry) * 0.5f,
        center = spot,
    )
}

/**
 * Капля привета, получение: когда второй человек открывает приложение, капля падает сверху,
 * разбивается в пятно и оставляет короткую запись. Несколько капель — одна капля с числом.
 */
@Composable
fun GreetingArrivalHost(diary: DiaryViewModel, owner: Person) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    var seenUntil by remember { mutableLongStateOf(settings.dropsSeenUntil) }
    val drops = diary.partnerShare?.takeIf { it.from == owner.partner }?.drops.orEmpty()
    val unseen = remember(drops, seenUntil) { GreetingDrops.unseen(drops, seenUntil, System.currentTimeMillis()) }
    val markSeen = {
        val mark = GreetingDrops.seenMark(drops, seenUntil)
        settings.dropsSeenUntil = mark
        seenUntil = mark
    }
    // Капли старше суток не показываем, а тихо отмечаем.
    LaunchedEffect(drops, seenUntil) {
        if (unseen.isEmpty() && drops.any { it > seenUntil }) markSeen()
    }
    if (unseen.isNotEmpty() && !OverlayGate.special) {
        key(unseen.first()) {
            GreetingArrival(owner.partner, unseen, LocalDate.now(), onDone = markSeen)
        }
    }
}

private const val ARRIVAL_TOTAL = 4.6f
private const val IMPACT = 0.7f

/** Кадр, который показывается без анимации: пятно уже растеклось, брызг нет, текст виден. */
private const val STILL_FRAME = 2.0f

@Composable
fun GreetingArrival(from: Person, drops: List<Long>, today: LocalDate, onDone: () -> Unit) {
    val context = LocalContext.current
    // «Меньше анимаций»: без падения и брызг, сразу спокойная карточка на несколько секунд.
    val animate = remember { EffectsSettings.animate(context) }
    val done by rememberUpdatedState(onDone)
    val progress = remember { Animatable(if (animate) 0f else STILL_FRAME) }
    var skip by remember { mutableStateOf(false) }
    val top = TogetherColors.top(from)
    val bottom = TogetherColors.bottom(from)
    val measurer = rememberTextMeasurer()
    val count = drops.size
    val title = GreetingDrops.title(from, count)
    val subtitle = GreetingDrops.subtitle(drops, today)
    LaunchedEffect(skip) {
        if (!animate) {
            if (!skip) delay(4_000)
            done()
            return@LaunchedEffect
        }
        // Нажатие не обрывает анимацию, а перематывает её к исчезновению: запись тает и закрывается.
        if (skip && progress.value < 3.6f) progress.snapTo(3.6f)
        val left = ARRIVAL_TOTAL - progress.value
        progress.animateTo(ARRIVAL_TOTAL, tween((left * 1000).toInt().coerceAtLeast(1), easing = LinearEasing))
        done()
    }
    LaunchedEffect(Unit) {
        if (!animate) return@LaunchedEffect
        snapshotFlow { progress.value >= IMPACT }.collect { hit -> if (hit) Haptics.dropLand(context) }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Нажатие сворачивает запись раньше времени.
                detectTapGestures { skip = true }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val t = progress.value
            val dp = density
            val c = Offset(size.width / 2f, size.height * 0.46f)
            val dim = smooth(0f, 0.3f, t) * (1f - smooth(3.6f, 4.5f, t))
            drawRect(Color.Black.copy(alpha = 0.38f * dim))
            if (t < IMPACT) {
                val f = t / IMPACT
                val pos = Offset(c.x, -30f * dp + (c.y + 30f * dp) * f * f)
                drawBlob(pos, 14f * dp, 14f * dp / 0.78f, 0f, 0f, top, bottom, 1f)
                if (count > 1) {
                    val text = measurer.measure(count.toString(), TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                    drawText(text, topLeft = pos - Offset(text.size.width / 2f, text.size.height / 2f))
                }
            } else {
                val e = 1f - (1f - ((t - IMPACT) / 0.35f).coerceIn(0f, 1f)).pow(3)
                val ev = ((t - 3.6f) / 0.9f).coerceIn(0f, 1f)
                val rx = (14f + 112f * e) * dp * (1f - ev * 0.95f)
                val ry = (14f + 40f * e) * dp * (1f - ev * 0.95f)
                drawBlob(c, rx, ry, t * 4f, 6f * dp * ev, top, bottom, 0.94f * (1f - ev), noise = 7f * dp * e, seed = (drops.first() % 1000) / 100f)
                // Брызги: разлетаются веером вверх и падают.
                val tau = (t - IMPACT).coerceIn(0f, 0.9f)
                for (i in 0 until 12) {
                    val a = -0.2f - hash(i, 1) * 2.74f
                    val v = (120f + 150f * hash(i, 2)) * dp
                    val p = c + Offset(cos(a) * v * tau, sin(a) * v * tau + 0.5f * 600f * dp * tau * tau)
                    val r = (2.5f + 2.5f * hash(i, 3)) * dp * (1f - tau / 0.9f)
                    if (r > 0.3f) drawCircle(lerpColor(top, bottom, hash(i, 4)).copy(alpha = 0.9f), r, p)
                }
            }
        }
        Column(
            modifier = Modifier
                .align(BiasAlignment(0f, -0.08f))
                .widthIn(max = 230.dp)
                .graphicsLayer { alpha = smooth(1.1f, 1.35f, progress.value) * (1f - smooth(3.35f, 3.6f, progress.value)) },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White, textAlign = TextAlign.Center)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center)
        }
    }
}

private fun smooth(from: Float, to: Float, x: Float): Float {
    val k = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return k * k * (3f - 2f * k)
}

private fun hash(i: Int, salt: Int): Float {
    val v = sin(i * 12.9898f + salt * 78.233f) * 43758.547f
    return v - floor(v)
}
