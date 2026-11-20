package io.github.cmix7777.kazhdyidnevnik.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
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

internal fun homeResident(person: Person, now: LocalDateTime): Resident = when {
    person == Person.AIZAT -> Resident.LIGHT
    now.hour >= 18 || now.hour < 6 -> Resident.MOTH
    now.toLocalDate().toEpochDay() % 3L == 0L -> Resident.JERBOA
    else -> Resident.GECKO
}

private fun wave(t: Float) = sin(t.coerceIn(0f, 1f) * PI).toFloat()

internal enum class Visit { HOME, CHAT, NOTE, WEEK }

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
    LaunchedEffect(resident, place, event, taps, animate) {
        val pressed = taps != tapped
        val changed = seen != event
        seen = event
        tapped = taps
        val greeting = !arrived && place == Visit.HOME && running
        arrived = running
        if (!running || (!pressed && !changed && !greeting)) {
            read = false
            motion.snapTo(1f)
            return@LaunchedEffect
        }
        read = resident == Resident.LIGHT && place == Visit.HOME && (pressed || (greeting && !rain && !fog && !morning))
        if (animate) {
            motion.snapTo(0f)
            motion.animateTo(1f, tween(if (read) 3700 else 1250, easing = LinearEasing))
        } else {
            motion.snapTo(.35f)
            if (running) delay(if (read) 2200 else 750)
            motion.snapTo(1f)
        }
        read = false
    }
    val home = place == Visit.HOME
    val width = if (home) 96.dp else if (resident == Resident.LIGHT) 74.dp else 54.dp
    val height = if (home) 56.dp else 39.dp
    Box(
        modifier.size(width, height).clipToBounds()
            .semantics { contentDescription = resident.label }
            .clickable(onClickLabel = tapLabel ?: "Разбудить персонажа") { taps++; onTap?.invoke() },
    ) {
        val t = motion.value
        val move = if (animate) wave(t) else 0f
        when (resident) {
            Resident.LIGHT -> LighthousePose(place, t, read, rain, fog, morning, animate, height)
            Resident.MARK -> Sprite(resident.art, 25.dp, height - 2.dp, Modifier.align(Alignment.BottomCenter), tilt = -8f * move)
            Resident.INK -> {
                val rise by animateFloatAsState(if (hiddenPose) 0f else 1f, tween(if (animate) 320 else 0), label = "ink-rise")
                Sprite(R.drawable.resident_ink_peek, 43.dp, 7.dp, Modifier.align(Alignment.BottomCenter))
                if (rise > .02f) Sprite(resident.art, 43.dp, (7 + 22 * rise).dp,
                    Modifier.align(Alignment.BottomCenter).graphicsLayer { alpha = rise }, tilt = 6f * move)
            }
            Resident.MOTH -> {
                val awake = t < .82f || (place == Visit.NOTE && !hiddenPose)
                Sprite(if (awake) resident.art else R.drawable.resident_moth_asleep,
                    if (awake) 33.dp else 43.dp, if (awake) height - 4.dp else 24.dp,
                    Modifier.align(Alignment.BottomCenter), tilt = -9f * move)
            }
            Resident.GECKO -> Sprite(resident.art, 44.dp, 43.dp,
                Modifier.align(Alignment.BottomCenter).offset(y = if (hiddenPose) 27.dp else (move * 5).dp),
                tilt = 10f * move)
            Resident.JERBOA -> {
                val jumping = t < .94f
                Sprite(if (jumping) R.drawable.resident_jerboa_jump else resident.art, 40.dp,
                    if (jumping) 28.dp else height - 2.dp,
                    Modifier.align(Alignment.BottomCenter).offset(x = (direction * (t - .5f) * if (jumping) 22f else 0f).dp,
                        y = (-move * 8).dp), flip = if (jumping) direction < 0 else direction > 0)
            }
        }
    }
}

@Composable
private fun Sprite(art: Int, width: Dp, height: Dp, modifier: Modifier, tilt: Float = 0f, flip: Boolean = false) {
    Image(painterResource(art), null, modifier.width(width).height(height).graphicsLayer {
        transformOrigin = TransformOrigin(.5f, 1f)
        rotationZ = tilt
        scaleX = if (flip) -1f else 1f
    }, alignment = Alignment.BottomCenter)
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
    Box(Modifier.size(if (home) 96.dp else 74.dp, height)) {
        Canvas(Modifier.matchParentSize()) {
            val lamp = Offset(size.width * (if (home) .32f else .35f), size.height * .26f)
            val end = Offset(size.width * .97f, if (read) size.height * .82f else size.height * (.25f + if (fog) .4f * pulse else 0f))
            val radius = size.height * if (read || fog) .23f else .13f
            val beam = Path().apply {
                moveTo(lamp.x, lamp.y - 1.dp.toPx())
                lineTo(end.x, end.y - radius); lineTo(end.x, end.y + radius)
                lineTo(lamp.x, lamp.y + 1.dp.toPx()); close()
            }
            val strength = if (read) .75f else .13f + .64f * pulse
            drawPath(beam, Brush.linearGradient(listOf(Color(0xFFFFD180).copy(alpha = strength), Color.Transparent), lamp, end))
        }
        Sprite(Resident.LIGHT.art, if (home) 37.dp else 27.dp, height - 2.dp,
            Modifier.align(Alignment.BottomStart).offset(x = if (home) 7.dp else 4.dp),
            tilt = 8f * bend + 4f * pulse)
        if (read) {
            Sprite(Resident.MARK.art, 15.dp, 27.dp, Modifier.align(Alignment.BottomEnd).offset(x = (-11).dp), tilt = -8f)
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
