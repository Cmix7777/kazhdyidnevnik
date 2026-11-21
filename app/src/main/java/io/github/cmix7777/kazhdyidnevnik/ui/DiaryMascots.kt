package io.github.cmix7777.kazhdyidnevnik.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.cmix7777.kazhdyidnevnik.R
import io.github.cmix7777.kazhdyidnevnik.data.Person
import io.github.cmix7777.kazhdyidnevnik.ui.fx.EffectsSettings
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import kotlin.math.PI
import kotlin.math.sin

internal enum class Resident(val label: String, val art: Int) {
    LIGHT("Маячок", R.drawable.resident_lighthouse),
    MARK("Закладка", R.drawable.resident_bookmark),
    INK("Клякса", R.drawable.resident_ink),
    MOTH("Мотылёк", R.drawable.resident_moth),
    GECKO("Геккон", R.drawable.resident_gecko),
    JERBOA("Тушканчик", R.drawable.resident_jerboa),
}

private fun wave(t: Float) = sin(t.coerceIn(0f, 1f) * PI).toFloat()

private fun beat(t: Float, start: Float, end: Float) =
    if (t <= start || t >= end) 0f else wave((t - start) / (end - start))

internal enum class Visit { HOME, CHAT, NOTE, WEEK }

internal fun residentFor(person: Person, now: LocalDateTime, place: Visit): Resident {
    if (person == Person.AIZAT) return if (place == Visit.NOTE) Resident.INK else Resident.LIGHT
    if (place == Visit.WEEK) return Resident.JERBOA
    val evening = if (now.hour < 6) now.toLocalDate().minusDays(1) else now.toLocalDate()
    val day = Math.floorMod(evening.toEpochDay(), 3L).toInt()
    val roost = when (day) { 0 -> Visit.HOME; 1 -> Visit.CHAT; else -> Visit.NOTE }
    if (now.hour !in 6..17 && place == roost) return Resident.MOTH
    return if ((day + place.ordinal) % 3 == 0) Resident.JERBOA else Resident.GECKO
}

internal fun homeResidents(person: Person, now: LocalDateTime): List<Resident> {
    if (person == Person.AIZAT) return listOf(Resident.INK, Resident.LIGHT)
    val visitor = residentFor(person, now, Visit.HOME)
    return listOf(Resident.GECKO, if (visitor == Resident.GECKO) Resident.JERBOA else visitor)
}

@Composable
internal fun MascotVisit(
    resident: Resident,
    place: Visit,
    modifier: Modifier = Modifier,
    event: Any? = null,
    hiddenPose: Boolean = false,
    direction: Int = 1,
    rain: Boolean = false,
    fog: Boolean = false,
    morning: Boolean = false,
    running: Boolean = true,
    onTap: (() -> Unit)? = null,
    tapLabel: String? = null,
) {
    var taps by remember(resident, place) { mutableIntStateOf(0) }
    val animate = running && EffectsSettings.animate(LocalContext.current)
    val motion = remember(resident, place) { Animatable(1f) }
    var seen by remember(resident, place) { mutableStateOf(event) }
    var read by remember(resident, place) { mutableStateOf(false) }
    var tapped by remember(resident, place) { mutableIntStateOf(0) }
    var arrived by remember(resident, place) { mutableStateOf(false) }
    var gesture by remember(resident, place) { mutableIntStateOf(0) }
    LaunchedEffect(resident, place, event, taps, running, animate, hiddenPose) {
        val pressed = taps != tapped
        val changed = seen != event
        seen = event
        tapped = taps
        val greeting = !arrived && place == Visit.HOME && running
        arrived = running
        read = false
        motion.snapTo(1f)
        if (!running) return@LaunchedEffect
        suspend fun play() {
            motion.snapTo(0f)
            motion.animateTo(1f, tween(if (read) 3200 else 1950, easing = LinearEasing))
        }
        if (pressed || changed || greeting) {
            gesture++
            read = resident == Resident.LIGHT && place == Visit.HOME && (pressed || (greeting && !rain && !fog && !morning))
            if (animate) play() else if (pressed || changed) {
                motion.snapTo(.4f)
                delay(700)
                motion.snapTo(1f)
            }
            read = false
        }
        if (!animate || hiddenPose) return@LaunchedEffect
        while (true) {
            withInfiniteAnimationFrameNanos { }
            delay(3200L + resident.ordinal * 430L + (gesture % 3) * 250L)
            gesture++
            read = resident == Resident.LIGHT && place == Visit.HOME && gesture % 3 == 0
            play()
            read = false
        }
    }
    val home = place == Visit.HOME
    val width = if (home && resident == Resident.LIGHT) 104.dp else if (home) 90.dp else if (resident == Resident.LIGHT) 78.dp else 60.dp
    val height = if (home) 92.dp else 49.dp
    Box(
        modifier.size(width, height).clipToBounds().testTag("resident-${place.name}-${resident.name}")
            .semantics { contentDescription = resident.label }
            .clickable(onClickLabel = tapLabel ?: "Разбудить персонажа") { taps++; onTap?.invoke() },
    ) {
        val t = motion.value
        val phase = if (animate) t else 1f
        val move = wave(phase)
        val side = if (gesture % 2 == 0) -1f else 1f
        when (resident) {
            Resident.LIGHT -> LighthousePose(place, t, read, rain, fog, morning, animate, height)
            Resident.MARK -> {
                val turn = beat(phase, .18f, .74f)
                Sprite(resident.art, width * .6f, height * .9f,
                    Modifier.align(Alignment.BottomCenter).offset(y = -height * .09f * move),
                    tilt = side * (14f * move - 20f * beat(phase, .66f, 1f)), stretchX = 1f - .65f * turn)
            }
            Resident.INK -> {
                val rise by animateFloatAsState(if (hiddenPose) 0f else 1f, tween(if (animate) 320 else 0), label = "ink-rise")
                val hiding = gesture % 2 == 0
                val peek = if (hiding) beat(phase, .02f, .63f) else 0f
                val spring = if (hiding) beat(phase, .58f, .94f) else beat(phase, .19f, .74f)
                val squash = if (hiding) 0f else beat(phase, 0f, .25f) + beat(phase, .71f, .94f)
                Sprite(R.drawable.resident_ink_peek, width * .85f, height * .13f, Modifier.align(Alignment.BottomCenter))
                if (rise > .02f) Sprite(resident.art, width * .85f, height * (.12f + .51f * rise),
                    Modifier.align(Alignment.BottomCenter).offset(x = width * .07f * side * spring, y = -height * .23f * spring)
                        .graphicsLayer { alpha = rise }, tilt = side * 9f * spring,
                    stretchX = 1f + .16f * squash + .11f * peek - .1f * spring,
                    stretchY = 1f - .32f * squash - .85f * peek + .12f * spring,
                    rim = true)
                if (spring > 0f && rise > .5f) Canvas(Modifier.matchParentSize()) {
                    for (i in 0..2) {
                        val x = size.width * (.29f + .19f * i + (i - 1) * .11f * spring)
                        val y = size.height * (.48f - (.31f + .07f * (i % 2)) * spring)
                        val r = (1.7f + i * .38f).dp.toPx() * spring
                        drawCircle(Color(0xffb6aad9), r * 1.18f, Offset(x, y))
                        drawCircle(Color(0xff393455), r, Offset(x, y))
                    }
                }
            }
            Resident.MOTH -> {
                val awake = t < .82f || (place == Visit.NOTE && !hiddenPose)
                val wings = kotlin.math.abs(sin(phase * PI * 5).toFloat()) * move
                Sprite(if (awake) resident.art else R.drawable.resident_moth_asleep,
                    width * .83f, if (awake) height * .69f else height * .43f,
                    Modifier.align(Alignment.BottomCenter).offset(x = width * .09f * side * move, y = -height * .23f * move),
                    tilt = -11f * side * move, stretchX = 1f - .37f * wings)
            }
            Resident.GECKO -> {
                val climb = beat(phase, .05f, .89f)
                val step = sin(phase * PI * 6).toFloat() * climb
                val hunting = gesture % 2 == 1
                val turn = if (hunting) 0f else beat(phase, .12f, .90f)
                Sprite(resident.art, width * .78f, height * .76f,
                    Modifier.align(Alignment.BottomCenter).offset(x = width * .08f * side * climb,
                        y = if (hiddenPose) height * .42f else -height * (if (hunting) .12f else .24f) * climb),
                    tilt = (if (hunting) -9f else 17f) * climb + 5f * step,
                    stretchX = 1f - 2f * turn, stretchY = 1f + .05f * step)
                val fly = beat(phase, 0f, .61f)
                if (hunting && fly > 0f && !hiddenPose) Canvas(Modifier.matchParentSize()) {
                    val target = Offset(size.width * (.86f + .045f * step), size.height * (.19f - .08f * fly))
                    drawCircle(Color(0xffffd491).copy(alpha = .21f * fly), 4.dp.toPx(), target)
                    drawCircle(Color(0xffffdf91).copy(alpha = fly), 1.8.dp.toPx(), target)
                    val reach = beat(phase, .42f, .58f)
                    if (reach > 0f) {
                        val nose = Offset(size.width * .75f, size.height * .31f)
                        drawLine(Color(0xffe99778), nose, nose + (target - nose) * reach, 1.3.dp.toPx())
                    }
                }
            }
            Resident.JERBOA -> {
                val hop = beat(phase, .1f, .54f) + .66f * beat(phase, .55f, .91f)
                val jumping = hop > .06f
                val facing = if (place == Visit.WEEK) direction.toFloat() else side
                Sprite(if (jumping) R.drawable.resident_jerboa_jump else resident.art, width * .79f,
                    if (jumping) height * .49f else height * .77f,
                    Modifier.align(Alignment.BottomCenter).offset(x = width * .09f * facing * move, y = -height * .36f * hop),
                    tilt = -facing * 8f * hop, flip = if (jumping) facing < 0 else facing > 0)
            }
        }
    }
}

@Composable
private fun Sprite(art: Int, width: Dp, height: Dp, modifier: Modifier, tilt: Float = 0f, flip: Boolean = false,
                   stretchX: Float = 1f, stretchY: Float = 1f, rim: Boolean = false) {
    val painter = painterResource(art)
    Box(modifier.width(width).height(height).graphicsLayer {
        transformOrigin = TransformOrigin(.5f, 1f)
        rotationZ = tilt
        scaleX = if (flip) -stretchX else stretchX
        scaleY = stretchY
    }) {
        if (rim) Image(painter, null, Modifier.matchParentSize().offset(y = (-1).dp).graphicsLayer {
            scaleX = 1.025f
            scaleY = 1.02f
        }, alignment = Alignment.BottomCenter, colorFilter = ColorFilter.tint(Color(0xffbeb2dc)))
        Image(painter, null, Modifier.matchParentSize(), alignment = Alignment.BottomCenter)
    }
}

@Composable
internal fun InkAndLighthouse(typing: Boolean) {
    val animate = EffectsSettings.animate(LocalContext.current)
    val travel = remember { Animatable(0f) }
    var signal by remember { mutableIntStateOf(0) }
    LaunchedEffect(typing, signal, animate) {
        travel.snapTo(0f)
        if (typing || !animate) return@LaunchedEffect
        delay(350)
        travel.animateTo(1f, tween(1800, easing = LinearEasing))
    }
    val turn = wave(travel.value) * 2f - 1f
    Box(Modifier.size(126.dp, 50.dp).clipToBounds().semantics {
        contentDescription = "Маячок ищет Кляксу"
    }.clickable(onClickLabel = "Поиграть в прятки") { signal++ }) {
        Sprite(
            if (typing) R.drawable.resident_ink_peek else Resident.INK.art, 42.dp,
            if (typing) 7.dp else 28.dp,
            Modifier.align(Alignment.BottomCenter).offset(x = (-turn * 38).dp), rim = !typing,
        )
        Box(Modifier.align(Alignment.BottomCenter).offset(x = (turn * 16).dp)
            .graphicsLayer { scaleX = if (turn >= 0) 1f else -1f }) {
            LighthousePose(Visit.NOTE, travel.value, read = false, rain = false, fog = false,
                morning = false, animate = animate, height = 46.dp)
        }
    }
}

@Composable
private fun LighthousePose(place: Visit, t: Float, read: Boolean, rain: Boolean, fog: Boolean, morning: Boolean, animate: Boolean, height: Dp) {
    val home = place == Visit.HOME
    val pulse = if (t >= 1f) 0f else if (!animate) .9f else when {
        t < .40f -> wave(t / .40f)
        t >= .48f && t <= .93f -> wave((t - .48f) / .45f)
        else -> 0f
    }
    val bend = if (read) (if (animate) wave(t).coerceAtLeast(.45f) else .75f) else 0f
    Box(Modifier.size(if (home) 104.dp else 78.dp, height)) {
        Canvas(Modifier.matchParentSize()) {
            val lamp = Offset(size.width * (if (home) .39f else .29f), size.height * .29f)
            val sweep = if (animate) beat(t, .15f, .93f) else 0f
            val end = Offset(size.width * .99f, size.height * (if (read) .83f else .29f + (if (fog) .54f else .35f) * sweep))
            val radius = size.height * if (read || fog) .23f else .13f
            val beam = Path().apply {
                moveTo(lamp.x, lamp.y - 1.dp.toPx())
                lineTo(end.x, end.y - radius); lineTo(end.x, end.y + radius)
                lineTo(lamp.x, lamp.y + 1.dp.toPx()); close()
            }
            val strength = if (read) .75f else .13f + .64f * pulse
            drawPath(beam, Brush.linearGradient(listOf(Color(0xFFFFD180).copy(alpha = strength), Color.Transparent), lamp, end))
        }
        Sprite(Resident.LIGHT.art, if (home) 62.dp else 34.dp, height - 2.dp,
            Modifier.align(Alignment.BottomStart).offset(x = if (home) 7.dp else 4.dp),
            tilt = 8f * bend + 4f * pulse)
        if (read) {
            Sprite(Resident.MARK.art, if (home) 23.dp else 15.dp, height * .53f,
                Modifier.align(Alignment.BottomEnd).offset(x = (-9).dp), tilt = -8f + 8f * pulse)
        }
        if (read || (home && (rain || (morning && t < 1f)))) Canvas(Modifier.matchParentSize()) {
            if (read) {
                val x = size.width * .80f; val y = size.height * .86f; val u = 1.dp.toPx()
                val book = Path().apply {
                    moveTo(x - 8*u,y - 4*u); lineTo(x,y - u); lineTo(x + 7*u,y - 6*u)
                    lineTo(x + 6*u,y + u); lineTo(x,y + 4*u); lineTo(x - 8*u,y + 2*u); close()
                }
                drawPath(book, Color(0xFFEFDCB5))
                drawLine(Color(0xFF6C7565), Offset(x,y+3*u), Offset(x+6*u*wave(t),y-4*u), u)
            } else {
                val x = size.width * .30f; val y = size.height * .28f; val u = 1.dp.toPx()
                if (rain) {
                    drawCircle(Color(0xFFBCE6EF),1.5f*u,Offset(x+3*u,y-1.5f*u))
                    drawLine(Color(0xFF635852),Offset(x-4*u,y+3*u),Offset(x+(pulse*9-4)*u,y-2*u),u)
                } else {
                    drawCircle(Color(0xFFB5D8C6),2.7f*u,Offset(x+(pulse*6-3)*u,y+u))
                }
            }
        }
    }
}
