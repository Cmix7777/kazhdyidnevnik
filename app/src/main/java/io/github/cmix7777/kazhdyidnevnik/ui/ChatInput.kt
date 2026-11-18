package io.github.cmix7777.kazhdyidnevnik.ui

import android.Manifest
import android.app.ActivityOptions
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.cmix7777.kazhdyidnevnik.R
import io.github.cmix7777.kazhdyidnevnik.applyAppChrome
import io.github.cmix7777.kazhdyidnevnik.data.ChatFile
import io.github.cmix7777.kazhdyidnevnik.data.ChatLogic
import io.github.cmix7777.kazhdyidnevnik.data.ChatGame
import io.github.cmix7777.kazhdyidnevnik.data.Wallpapers
import io.github.cmix7777.kazhdyidnevnik.data.WallpaperChoice
import io.github.cmix7777.kazhdyidnevnik.data.ChatMessage
import io.github.cmix7777.kazhdyidnevnik.data.ChatPhoto
import io.github.cmix7777.kazhdyidnevnik.data.ChatRow
import io.github.cmix7777.kazhdyidnevnik.data.ChatState
import io.github.cmix7777.kazhdyidnevnik.data.ChatTick
import io.github.cmix7777.kazhdyidnevnik.data.PartnerNow
import io.github.cmix7777.kazhdyidnevnik.data.Person
import io.github.cmix7777.kazhdyidnevnik.formatDate
import io.github.cmix7777.kazhdyidnevnik.formatShortDay
import io.github.cmix7777.kazhdyidnevnik.formatTime
import io.github.cmix7777.kazhdyidnevnik.notify.AppSettings
import io.github.cmix7777.kazhdyidnevnik.notify.ReminderScheduler
import io.github.cmix7777.kazhdyidnevnik.service.ChatFiles
import io.github.cmix7777.kazhdyidnevnik.service.ChatSync
import io.github.cmix7777.kazhdyidnevnik.service.StickerStore
import io.github.cmix7777.kazhdyidnevnik.ui.fx.EffectsSettings
import io.github.cmix7777.kazhdyidnevnik.ui.fx.Haptics
import io.github.cmix7777.kazhdyidnevnik.ui.fx.SkyFx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun InputBar(
    text: String,
    enabled: Boolean,
    panel: Boolean,
    circleMode: Boolean,
    recording: Boolean,
    locked: Boolean,
    recordTime: Long,
    recordLevel: Float,
    slide: Float,
    lift: Float = 0f,
    onText: (String) -> Unit,
    onFocus: (Boolean) -> Unit,
    onSend: () -> Unit,
    onSpecial: (String) -> Unit,
    onGallery: () -> Unit,
    onCamera: () -> Unit,
    onFile: () -> Unit,
    onGame: () -> Unit,
    onTogglePanel: () -> Unit,
    onToggleMode: () -> Unit,
    onRecordStart: () -> Boolean,
    onRecordSlide: (Float) -> Unit,
    onRecordLock: () -> Unit,
    onRecordLift: (Float) -> Unit = {},
    onRecordEnd: (cancel: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val lockDistance = with(LocalDensity.current) { 70.dp.toPx() }
    val canSend = enabled && text.isNotBlank()
    var attach by remember { mutableStateOf(false) }
    var effects by remember { mutableStateOf(false) }
    val cancelDistance = with(LocalDensity.current) { 110.dp.toPx() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (panel) Modifier else Modifier.navigationBarsPadding())
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box {
            GlassIconButton(icon = R.drawable.ic_attach, contentDescription = "Вложение: фото, камера или файл", onClick = { attach = true }, enabled = enabled && !recording)
            DropdownMenu(expanded = attach, onDismissRequest = { attach = false }) {
                DropdownMenuItem(
                    text = { Text("Камера") },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_camera), contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        attach = false
                        onCamera()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Галерея") },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_photo), contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        attach = false
                        onGallery()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Файл") },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_file), contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        attach = false
                        onFile()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Крестики-нолики") },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_game), contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        attach = false
                        onGame()
                    },
                    modifier = Modifier.testTag("attach-game"),
                )
            }
        }
        val shape = RoundedCornerShape(22.dp)
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 44.dp)
                .clip(shape)
                .background(Palette.Chip)
                .border(1.dp, if (recording) Palette.Danger.copy(alpha = 0.6f) else Palette.BorderStrong, shape)
                .padding(start = 16.dp, end = 6.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (recording) {
                val blink = rememberInfiniteTransition(label = "rec").animateFloat(
                    initialValue = 1f,
                    targetValue = 0.25f,
                    animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
                    label = "dot",
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .graphicsLayer { alpha = blink.value }
                            .clip(CircleShape)
                            .background(Palette.Danger),
                    )
                    Text(formatDuration(recordTime), style = MaterialTheme.typography.bodyLarge, color = Palette.Text)
                    Box(
                        modifier = Modifier
                            .height((6 + 18 * recordLevel).dp)
                            .width(4.dp)
                            .clip(CircleShape)
                            .background(Palette.Lavender),
                    )
                    if (locked) {
                        Spacer(Modifier.weight(1f))
                        LockHint(progress = 1f, locked = true, color = Palette.Lavender, modifier = Modifier.size(width = 18.dp, height = 22.dp))
                        Icon(
                            painterResource(R.drawable.ic_delete),
                            contentDescription = "Отменить голосовое",
                            tint = Palette.Danger,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable { onRecordEnd(true) }
                                .padding(6.dp)
                                .testTag("record-cancel"),
                        )
                    } else {
                        LockHint(progress = lift, locked = false, color = Palette.Lavender, modifier = Modifier.size(width = 18.dp, height = 22.dp))
                        Text(
                        text = if (slide < -cancelDistance * 0.6f) "отпусти — отмена" else "влево — отмена, вверх — без рук",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextFaint,
                        maxLines = 1,
                        modifier = Modifier.graphicsLayer { translationX = slide.coerceIn(-cancelDistance, 0f) * 0.4f },
                    )
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = text,
                        onValueChange = onText,
                        enabled = enabled,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Palette.Text),
                        cursorBrush = SolidColor(Palette.Violet),
                        maxLines = 6,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 11.dp)
                            .onFocusChanged { onFocus(it.isFocused) }
                            .testTag("chat-input"),
                        decorationBox = { inner ->
                            if (text.isEmpty()) {
                                Text(text = "Сообщение", style = MaterialTheme.typography.bodyLarge, color = Palette.TextFaint)
                            }
                            inner()
                        },
                    )
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable(enabled = enabled, role = Role.Button, onClick = onTogglePanel),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(if (panel) R.drawable.ic_keyboard else R.drawable.ic_smile),
                            contentDescription = if (panel) "Клавиатура" else "Смайлики и стикеры",
                            tint = Palette.TextMuted,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
        if (canSend) {
            Box {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Palette.Violet, Palette.VioletDeep)))
                        .combinedClickable(role = Role.Button, onClick = onSend, onLongClick = {
                            Haptics.tick(context)
                            effects = true
                        })
                        .testTag("chat-send"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_send), contentDescription = "Отправить (удержать — с эффектом)", tint = Color.White, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(expanded = effects, onDismissRequest = { effects = false }) {
                    EFFECT_OPTIONS.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(option.title, style = MaterialTheme.typography.bodyMedium)
                                    Text(option.hint, style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
                                }
                            },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(Brush.linearGradient(listOf(option.from, option.to))),
                                )
                            },
                            onClick = {
                                effects = false
                                onSpecial(option.key)
                            },
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .graphicsLayer {
                        val s = if (recording) 1.18f else 1f
                        scaleX = s
                        scaleY = s
                    }
                    .clip(CircleShape)
                    .background(if (recording) Brush.linearGradient(listOf(Palette.Violet, Palette.VioletDeep)) else SolidColor(Palette.Chip))
                    .pointerInput(enabled, circleMode, locked) {
                        if (!enabled) return@pointerInput
                        if (locked) {
                            // Голосовое пишется без рук: кнопка стала «Отправить».
                            awaitEachGesture {
                                awaitFirstDown().consume()
                                if (waitForUpOrCancellation() != null) onRecordEnd(false)
                            }
                            return@pointerInput
                        }
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            val early = withTimeoutOrNull(260L) { waitForUpOrCancellation() }
                            if (early != null) {
                                onToggleMode()
                                Haptics.tick(context)
                                return@awaitEachGesture
                            }
                            if (!onRecordStart()) return@awaitEachGesture
                            var finished = false
                            while (!finished) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null) {
                                    onRecordEnd(true)
                                    finished = true
                                } else {
                                    change.consume()
                                    val dx = change.position.x - down.position.x
                                    val dy = change.position.y - down.position.y
                                    onRecordSlide(dx)
                                    onRecordLift((-dy / lockDistance).coerceIn(0f, 1f))
                                    when {
                                        !change.pressed -> {
                                            onRecordEnd(dx < -cancelDistance)
                                            finished = true
                                        }
                                        dx < -cancelDistance * 1.6f -> {
                                            onRecordEnd(true)
                                            finished = true
                                        }
                                        // Вверх — закрепить: дальше запись идёт без пальца.
                                        dy < -lockDistance && dx > -cancelDistance * 0.6f -> {
                                            onRecordLock()
                                            finished = true
                                        }
                                    }
                                }
                            }
                        }
                    }
                    .testTag("chat-record"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(if (locked) R.drawable.ic_send else if (circleMode) R.drawable.ic_videocam else R.drawable.ic_mic),
                    contentDescription = if (locked) "Отправить голосовое" else if (circleMode) "Кружочек: удерживай, чтобы записать, нажми — голосовое" else "Голосовое: удерживай, чтобы записать, нажми — кружочек",
                    tint = if (recording) Color.White else Palette.TextMuted,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

internal class EffectOption(val key: String, val title: String, val hint: String, val from: Color, val to: Color)

internal val EFFECT_OPTIONS = listOf(
    EffectOption(WordFxNames.HEARTS, "Сердечки", "сами включаются на «люблю»", Color(0xFFF472B6), Color(0xFFA855F7)),
    EffectOption(WordFxNames.NIGHT, "Звёзды", "на «спокойной ночи»", Color(0xFF3B2A7A), Color(0xFF1B2D55)),
    EffectOption(WordFxNames.SUN, "Рассвет", "на «доброе утро»", Color(0xFFFFB36B), Color(0xFFF472B6)),
    EffectOption(WordFxNames.FIRE, "Салют", "для праздников", Color(0xFFF5B86B), Color(0xFFF472B6)),
    EffectOption("secret", "Невидимые чернила", "прочитает, проведя пальцем", Color(0xFFB08CF0), Color(0xFF4FA3E8)),
    EffectOption("hand", "Запиской", "своим почерком", Color(0xFFF4EFE3), Color(0xFF26358C)),
    EffectOption("later", "Отправить позже", "уйдёт само в выбранное время", Color(0xFF7DD3FC), Color(0xFF6366F1)),
)

/** «Печатает…»: пузырь второго человека, в нём перетекают капли (или три точки, если анимации выключены). */
@Composable
internal fun TypingBubble(colors: BubbleColors, animate: Boolean) {
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 6.dp)
    Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.CenterStart) {
        Row(
            modifier = Modifier
                .clip(shape)
                .background(Brush.verticalGradient(listOf(colors.theirTop.copy(alpha = 0.96f), colors.theirBottom.copy(alpha = 0.94f))))
                .border(1.dp, colors.theirBorder.copy(alpha = 0.3f), shape)
                .padding(horizontal = if (animate) 8.dp else 16.dp, vertical = if (animate) 8.dp else 14.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!animate) {
                repeat(3) { Dot(colors.theirAccent, 0.7f) }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                TypingGoo(colors.theirAccent, colors.theirBorder)
            } else {
                val transition = rememberInfiniteTransition(label = "typing")
                repeat(3) { index ->
                    val phase by transition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(tween(520, delayMillis = index * 160), RepeatMode.Reverse),
                        label = "dot$index",
                    )
                    Dot(colors.theirAccent, phase)
                }
            }
        }
    }
}

@Composable
internal fun Dot(color: Color, strength: Float) {
    Box(
        modifier = Modifier
            .size(7.dp)
            .graphicsLayer {
                alpha = strength
                val scale = 0.8f + strength * 0.25f
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(color),
    )
}
