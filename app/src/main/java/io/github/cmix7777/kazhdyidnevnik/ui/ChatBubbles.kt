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

internal const val HEART_EMOJI = "❤️"
internal val REACTIONS = listOf(HEART_EMOJI, "😂", "👍", "😮", "😢", "🔥")

/** Цвета пузырей: свои — в палитре владельца, чужие — стекло в палитре второго человека. */
internal class BubbleColors(
    val mineTop: Color,
    val mineBottom: Color,
    val theirTop: Color,
    val theirBottom: Color,
    val theirBorder: Color,
    val theirAccent: Color,
)

@Composable
internal fun ChatTopBar(
    partner: Person,
    status: ChatSync.Status,
    colors: BubbleColors,
    onBack: () -> Unit,
    partnerNow: String?,
    onMenu: (String) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GlassIconButton(icon = R.drawable.ic_arrow_back, contentDescription = "Назад", onClick = onBack)
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(colors.theirBorder, colors.theirAccent.copy(alpha = 0.7f)))),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = partner.shortName.take(1), style = MaterialTheme.typography.titleMedium, color = Color.White)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = partner.shortName, style = MaterialTheme.typography.titleMedium, color = Palette.Text)
            val line = when {
                status.partnerTyping -> "печатает…"
                status.restoring -> "забираю переписку…"
                status.problem != null && status.paired -> "нет связи с облаком"
                status.partnerHere == false -> "увидит сообщения, когда обновит приложение"
                else -> partnerNow
            }
            if (line != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (line == partnerNow && !status.partnerTyping) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(colors.theirBorder),
                        )
                    }
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (status.partnerTyping) Palette.Lavender else Palette.TextFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Box {
            GlassIconButton(icon = R.drawable.ic_more, contentDescription = "Ещё", onClick = { menu = true })
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Поиск по переписке") },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        menu = false
                        onMenu("search")
                    },
                    modifier = Modifier.testTag("menu-search"),
                )
                DropdownMenuItem(
                    text = { Text("Фон переписки") },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_wallpaper), contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        menu = false
                        onMenu("wallpaper")
                    },
                    modifier = Modifier.testTag("menu-wallpaper"),
                )
                DropdownMenuItem(
                    text = { Text("Мой почерк") },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_edit), contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        menu = false
                        onMenu("hand")
                    },
                )
                DropdownMenuItem(
                    text = { Text("Фото, кружочки и файлы") },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_photo), contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        menu = false
                        onMenu("media")
                    },
                )
                DropdownMenuItem(
                    text = { Text("Общий список") },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        menu = false
                        onMenu("list")
                    },
                )
                DropdownMenuItem(
                    text = { Text("Архив переписки") },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_archive), contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        menu = false
                        onMenu("archive")
                    },
                )
            }
        }
    }
}

@Composable
internal fun CenterNote(title: String, text: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 36.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = Palette.Text, textAlign = TextAlign.Center)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
internal fun DayLabel(date: LocalDate, zone: ZoneId) {
    val today = LocalDate.now(zone)
    val title = when {
        date == today -> "Сегодня"
        date == today.minusDays(1) -> "Вчера"
        date.year == today.year -> formatShortDay(date)
        else -> formatDate(date)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = Palette.TextMuted,
            modifier = Modifier
                .clip(CircleShape)
                .background(Palette.Chip)
                .padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MessageBubble(
    row: ChatRow.Message,
    owner: Person,
    colors: BubbleColors,
    maxWidth: Dp,
    failed: Boolean,
    zone: ZoneId,
    animate: Boolean,
    opened: Boolean,
    font: HandFont?,
    writeNow: Boolean,
    onWritten: () -> Unit,
    onReply: () -> Unit,
    onDelete: (ImageBitmap?, Rect) -> Unit,
    onOpenPhoto: (ChatPhoto) -> Unit,
    onRetry: () -> Unit,
    photoContent: @Composable (ChatPhoto, Modifier) -> Unit,
    onReact: (String?) -> Unit,
    onHeart: (Offset) -> Unit,
    onOpenSecret: () -> Unit,
    onEnsureFiles: () -> Unit,
    onSaveSticker: () -> Unit,
    onOpenFile: () -> Unit,
    onSaveFile: () -> Unit,
    canEdit: Boolean = false,
    onEdit: () -> Unit = {},
    onGameMove: (Int) -> Unit = {},
    onGameAgain: () -> Unit = {},
    pinned: Boolean = false,
    onPin: (Boolean) -> Unit = {},
    highlighted: Boolean = false,
) {
    val message = row.message
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    val big = 20.dp
    val small = 6.dp
    val shape = if (row.mine) {
        RoundedCornerShape(topStart = big, topEnd = if (row.first) big else small, bottomEnd = small, bottomStart = big)
    } else {
        RoundedCornerShape(topStart = if (row.first) big else small, topEnd = big, bottomEnd = big, bottomStart = small)
    }
    val textColor = if (row.mine) Color.White else Palette.Text
    val metaColor = if (row.mine) Color.White.copy(alpha = 0.72f) else Palette.TextFaint
    val background = if (row.mine) {
        Brush.linearGradient(listOf(colors.mineTop, colors.mineBottom))
    } else {
        Brush.verticalGradient(listOf(colors.theirTop.copy(alpha = 0.96f), colors.theirBottom.copy(alpha = 0.94f)))
    }
    val interactive = !message.deleted && !row.earlier
    val reactions = listOfNotNull(row.theirReaction, row.myReaction)
    val emojiCount = remember(message.text) {
        if (message.hasMedia || message.secret || message.hand || row.reply != null) 0 else BigEmoji.count(message.text)
    }
    // Без пузыря: стикер, кружочек, крупные смайлики, записка.
    val bare = message.sticker != null || message.circle != null || emojiCount > 0 || message.hand
    val covered = message.secret && !opened

    // Снимок пузыря для «рассыпается в пыль»: записывается, только пока открыто меню.
    val layer = rememberGraphicsLayer()
    var capture by remember { mutableStateOf(false) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    LaunchedEffect(menu) { if (menu && animate) capture = true }
    LaunchedEffect(message.key) { if (message.hasMedia) onEnsureFiles() }

    // Свайп вправо — ответить на сообщение.
    val drag = remember { Animatable(0f) }
    val threshold = with(density) { 64.dp.toPx() }
    val swipe = if (interactive) {
        Modifier.pointerInput(message.key) {
            var armed = false
            detectHorizontalDragGestures(
                onDragStart = { armed = false },
                onDragEnd = {
                    if (drag.value >= threshold) onReply()
                    scope.launch { drag.animateTo(0f, spring()) }
                },
                onDragCancel = { scope.launch { drag.animateTo(0f, spring()) } },
            ) { change, amount ->
                val next = (drag.value + amount).coerceIn(0f, threshold * 1.35f)
                if (!armed && next >= threshold) {
                    armed = true
                    Haptics.tick(context)
                }
                scope.launch { drag.snapTo(next) }
                change.consume()
            }
        }
    } else {
        Modifier
    }

    val clicks = Modifier.combinedClickable(
        enabled = interactive,
        onClick = {
            when {
                failed -> onRetry()
                message.photo != null -> onOpenPhoto(message.photo)
                message.file != null -> onOpenFile()
            }
        },
        onDoubleClick = { onHeart(bounds.center) },
        onLongClick = { menu = true },
    )
    val capturing = if (capture) {
        Modifier.drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
        }
    } else {
        Modifier
    }

    val glow by animateFloatAsState(if (highlighted) 1f else 0f, tween(if (highlighted) 250 else 900), label = "glow")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (row.first) 8.dp else 2.dp)
            .then(if (glow > 0f) Modifier.background(Palette.Lavender.copy(alpha = 0.22f * glow), RoundedCornerShape(12.dp)) else Modifier)
            .alpha(if (row.earlier) 0.6f else 1f)
            .then(swipe),
        contentAlignment = if (row.mine) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        if (drag.value > 1f) {
            Icon(
                painterResource(R.drawable.ic_reply),
                contentDescription = null,
                tint = Palette.Lavender,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 6.dp)
                    .size(20.dp)
                    .alpha((drag.value / threshold).coerceIn(0f, 1f)),
            )
        }
        Column(
            modifier = Modifier.offset { IntOffset(drag.value.roundToInt(), 0) },
            horizontalAlignment = if (row.mine) Alignment.End else Alignment.Start,
        ) {
            if (bare) {
                Column(
                    modifier = Modifier
                        .onGloballyPositioned { bounds = it.boundsInRoot() }
                        .then(capturing)
                        .then(clicks),
                    horizontalAlignment = if (row.mine) Alignment.End else Alignment.Start,
                ) {
                    when {
                        message.sticker != null -> StickerView(message.sticker.id, onReady = onEnsureFiles)
                        message.circle != null -> CircleBubble(message.circle, accent = if (row.mine) colors.mineTop else colors.theirBorder, onReady = onEnsureFiles)
                        message.hand -> HandNote(
                            text = message.text,
                            font = font,
                            seed = message.key.hashCode(),
                            animate = writeNow,
                            maxWidth = maxWidth,
                            onWritten = onWritten,
                        ) { Meta(message, row.tick, failed, Color(0x9926358C), zone) }
                        else -> BigEmojiText(message.text, emojiCount, animate && System.currentTimeMillis() - message.at < 60_000L)
                    }
                    if (!message.hand) {
                        Row(
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.32f))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) { Meta(message, row.tick, failed, Color.White.copy(alpha = 0.85f), zone) }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .widthIn(max = maxWidth)
                        .onGloballyPositioned { bounds = it.boundsInRoot() }
                        .then(capturing)
                        .clip(shape)
                        .background(background)
                        .then(if (row.mine) Modifier else Modifier.border(1.dp, colors.theirBorder.copy(alpha = 0.3f), shape))
                        .then(clicks),
                ) {
                    row.reply?.let { reply ->
                        ReplyQuote(
                            message = reply,
                            author = if (reply.from == owner.id) "Ты" else owner.partner.shortName,
                            mineBubble = row.mine,
                            colors = colors,
                        )
                    }
                    val photo = message.photo
                    if (photo != null && !message.deleted) {
                        val ratio = (photo.width.toFloat() / photo.height.coerceAtLeast(1)).coerceIn(0.75f, 1.8f)
                        Box(
                            modifier = Modifier
                                .width(maxWidth.coerceAtMost(230.dp))
                                .aspectRatio(ratio)
                                .padding(if (row.reply != null) 4.dp else 0.dp)
                                .clip(if (row.reply != null) RoundedCornerShape(16.dp) else RoundedCornerShape(0.dp)),
                        ) {
                            photoContent(photo, Modifier.fillMaxSize())
                            if (message.text.isEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.45f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                ) { Meta(message, row.tick, failed, Color.White, zone) }
                            }
                        }
                    }
                    val file = message.file
                    if (file != null) {
                        Column(horizontalAlignment = Alignment.End) {
                            FileBubble(file, mine = row.mine, accent = colors.theirAccent, onReady = onEnsureFiles)
                            Box(modifier = Modifier.padding(end = 10.dp, bottom = 7.dp)) { Meta(message, row.tick, failed, metaColor, zone) }
                        }
                    }
                    val voice = message.voice
                    if (voice != null) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            VoiceBubble(voice, mine = row.mine, accent = colors.theirAccent, onReady = onEnsureFiles)
                            Box(modifier = Modifier.padding(end = 10.dp, bottom = 8.dp)) { Meta(message, row.tick, failed, metaColor, zone) }
                        }
                        VoiceTranscript(voice.id, color = textColor, faint = metaColor)
                    }
                    val game = message.game
                    if (game != null && !message.deleted) {
                        val (xColor, oColor) = gameColors(row.mine, colors.theirAccent)
                        GameBubble(
                            game = game,
                            me = owner,
                            textColor = textColor,
                            xColor = xColor,
                            oColor = oColor,
                            playable = interactive,
                            fresh = animate && System.currentTimeMillis() - message.at < 60_000L,
                            onMove = onGameMove,
                            onAgain = onGameAgain,
                        ) { Meta(message, row.tick, failed, metaColor, zone) }
                    }
                    if (message.deleted || (message.text.isNotEmpty() && game == null)) {
                        Box {
                            TextWithMeta(
                                text = if (message.deleted) "Сообщение удалено" else message.text,
                                style = MaterialTheme.typography.bodyLarge.let { if (message.deleted) it.copy(fontStyle = FontStyle.Italic) else it },
                                color = if (message.deleted) metaColor else textColor,
                                modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                            ) { Meta(message, row.tick, failed, metaColor, zone) }
                            if (covered) {
                                InkCover(
                                    cover = if (row.mine) colors.mineBottom else colors.theirBottom,
                                    sparkle = if (row.mine) Color.White else colors.theirAccent,
                                    animate = animate,
                                    onOpened = onOpenSecret,
                                    modifier = Modifier.matchParentSize(),
                                )
                            }
                        }
                    }
                }
                if (covered) {
                    Text(
                        text = "проведи пальцем, чтобы прочитать",
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.TextFaint,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            // Реакции висят на нижнем краю пузыря, слева: не залезают в текст и не закрывают время.
            if (reactions.isNotEmpty()) {
                ReactionChip(
                    reactions = reactions,
                    border = if (row.mine) colors.mineTop else colors.theirBorder,
                    modifier = Modifier
                        .align(Alignment.Start)
                        .padding(start = 10.dp)
                        .offset(y = (-9).dp),
                )
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    REACTIONS.forEach { emoji ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (row.myReaction == emoji) Palette.Violet.copy(alpha = 0.3f) else Color.Transparent)
                                .clickable {
                                    menu = false
                                    onReact(emoji)
                                },
                            contentAlignment = Alignment.Center,
                        ) { Text(emoji, fontSize = 22.sp) }
                    }
                }
                DropdownMenuItem(
                    text = { Text("Ответить") },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_reply), contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        menu = false
                        onReply()
                    },
                )
                if (message.text.isNotEmpty() && !covered) {
                    DropdownMenuItem(
                        text = { Text("Копировать") },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_copy), contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            menu = false
                            context.getSystemService(ClipboardManager::class.java)
                                ?.setPrimaryClip(ClipData.newPlainText("Сообщение", message.text))
                        },
                    )
                }
                if (message.file != null) {
                    DropdownMenuItem(
                        text = { Text("Сохранить на телефон") },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_download), contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            menu = false
                            onSaveFile()
                        },
                    )
                }
                if (message.sticker != null && !row.mine) {
                    DropdownMenuItem(
                        text = { Text("Сохранить себе") },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            menu = false
                            onSaveSticker()
                        },
                    )
                }
                if (interactive) {
                    DropdownMenuItem(
                        text = { Text(if (pinned) "Открепить" else "Закрепить") },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_pin), contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            menu = false
                            onPin(!pinned)
                        },
                        modifier = Modifier.testTag("menu-pin"),
                    )
                }
                if (canEdit) {
                    DropdownMenuItem(
                        text = { Text("Изменить") },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_edit), contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            menu = false
                            onEdit()
                        },
                    )
                }
                if (row.mine) {
                    DropdownMenuItem(
                        text = { Text("Удалить у обоих") },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            menu = false
                            val where = bounds
                            if (capture && animate) {
                                scope.launch {
                                    val bitmap = runCatching { layer.toImageBitmap() }.getOrNull()
                                    onDelete(bitmap, where)
                                }
                            } else {
                                onDelete(null, where)
                            }
                        },
                    )
                }
            }
        }
    }
}

/** Крупные смайлики без пузыря: появляются с подскоком, сердечки ещё и бьются. */
@Composable
internal fun BigEmojiText(text: String, count: Int, bounce: Boolean) {
    val appear = remember { Animatable(if (bounce) 0.3f else 1f) }
    LaunchedEffect(Unit) { if (bounce) appear.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 320f)) }
    val heart = BigEmoji.isHeart(text) && !EffectsSettings.reduced
    // Бьются только сердечки: у остальных смайликов бесконечной анимации нет вовсе.
    val beat = if (!heart) null else rememberInfiniteTransition(label = "beat").animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 1400
                1f at 0
                1.14f at 120
                1f at 260
                1.1f at 380
                1f at 560
            },
        ),
        label = "heart",
    )
    Text(
        text = text.trim(),
        fontSize = when (count) {
            1 -> 54.sp
            2 -> 42.sp
            else -> 34.sp
        },
        modifier = Modifier
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .graphicsLayer {
                val s = appear.value * (beat?.value ?: 1f)
                scaleX = s
                scaleY = s
            },
    )
}

/** Реакции под пузырём: смайлики обоих; если оба поставили одинаковый — он один с цифрой 2. */
@Composable
internal fun ReactionChip(reactions: List<String>, border: Color, modifier: Modifier = Modifier) {
    val pop = remember { Animatable(0.4f) }
    LaunchedEffect(reactions) {
        pop.snapTo(0.6f)
        pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 420f))
    }
    val same = reactions.size == 2 && reactions[0] == reactions[1]
    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
            .clip(CircleShape)
            .background(Palette.Background.copy(alpha = 0.96f))
            .border(1.dp, border.copy(alpha = 0.55f), CircleShape)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = if (same) reactions[0] else reactions.joinToString(""), fontSize = 14.sp)
        if (same) Text(text = "2", style = MaterialTheme.typography.labelSmall, color = Palette.TextMuted)
    }
}

/** Время, реакции и отметка «ушло / прочитано» в углу пузыря. */
@Composable
internal fun Meta(message: ChatMessage, tick: ChatTick, failed: Boolean, color: Color, zone: ZoneId, reactions: List<String> = emptyList()) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        if (reactions.isNotEmpty()) {
            Text(text = reactions.joinToString(""), fontSize = 13.sp, modifier = Modifier.padding(end = 2.dp))
        }
        if (message.edited != null) {
            Text(text = "изменено", style = MaterialTheme.typography.labelSmall, color = color)
        }
        Text(
            text = formatTime(Instant.ofEpochMilli(message.at).atZone(zone).toLocalTime()),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
        val icon = when {
            failed -> R.drawable.ic_error
            tick == ChatTick.PENDING -> R.drawable.ic_schedule
            tick == ChatTick.SENT -> R.drawable.ic_check
            tick == ChatTick.READ -> R.drawable.ic_done_all
            else -> null
        }
        if (icon != null) {
            Icon(
                painterResource(icon),
                contentDescription = when (tick) {
                    ChatTick.READ -> "Прочитано"
                    ChatTick.SENT -> "Отправлено"
                    else -> "Отправляется"
                },
                tint = if (failed) Palette.Danger else if (tick == ChatTick.READ) color.copy(alpha = 1f) else color,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/**
 * Текст пузыря и время: если последняя строка короткая, время встаёт рядом с ней, иначе — строкой ниже.
 */
@Composable
internal fun TextWithMeta(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    meta: @Composable () -> Unit,
) {
    val holder = remember { arrayOfNulls<TextLayoutResult>(1) }
    Layout(
        content = {
            Text(text = text, style = style, color = color, onTextLayout = { holder[0] = it })
            meta()
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val gap = 8.dp.roundToPx()
        val textPlaceable = measurables[0].measure(constraints.copy(minWidth = 0, minHeight = 0))
        val metaPlaceable = measurables[1].measure(Constraints())
        val textLayout = holder[0]
        val maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
        val lines = textLayout?.lineCount ?: 1
        val lastLine = textLayout?.let { kotlin.math.ceil(it.getLineRight(lines - 1)).toInt() } ?: textPlaceable.width
        val inline = lastLine + gap + metaPlaceable.width
        val width: Int
        val height: Int
        val metaY: Int
        when {
            lines == 1 && inline <= maxWidth -> {
                width = max(textPlaceable.width, inline)
                height = max(textPlaceable.height, metaPlaceable.height)
                metaY = height - metaPlaceable.height
            }
            inline <= textPlaceable.width -> {
                width = textPlaceable.width
                height = textPlaceable.height
                metaY = height - metaPlaceable.height
            }
            else -> {
                width = max(textPlaceable.width, metaPlaceable.width)
                height = textPlaceable.height + metaPlaceable.height
                metaY = textPlaceable.height
            }
        }
        layout(width, height) {
            textPlaceable.placeRelative(0, 0)
            metaPlaceable.placeRelative(width - metaPlaceable.width, metaY)
        }
    }
}

/** Цитата сообщения, на которое ответ (внутри пузыря). */
@Composable
internal fun ReplyQuote(message: ChatMessage, author: String, mineBubble: Boolean, colors: BubbleColors) {
    val bar = if (mineBubble) Color.White else colors.theirAccent
    val textColor = if (mineBubble) Color.White.copy(alpha = 0.85f) else Palette.TextMuted
    Row(
        modifier = Modifier
            .padding(start = 8.dp, end = 8.dp, top = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (mineBubble) Color.White.copy(alpha = 0.14f) else colors.theirBorder.copy(alpha = 0.12f))
            .height(IntrinsicSize.Min),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(bar),
        )
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
            Text(text = author, style = MaterialTheme.typography.labelMedium, color = bar)
            Text(
                text = ChatLogic.shortText(message),
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Над полем ввода: на что отвечаешь, с крестиком. */
@Composable
internal fun ReplyBar(message: ChatMessage, author: String, colors: BubbleColors, mine: Boolean, onCancel: () -> Unit) {
    val bar = if (mine) colors.mineTop else colors.theirAccent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 10.dp, top = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.Chip)
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(bar),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(text = "Ответ: $author", style = MaterialTheme.typography.labelMedium, color = bar)
            Text(
                text = ChatLogic.shortText(message),
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onCancel),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_close), contentDescription = "Не отвечать", tint = Palette.TextMuted, modifier = Modifier.size(18.dp))
        }
    }
}
