package io.github.cmix7777.kazhdyidnevnik.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.cmix7777.kazhdyidnevnik.R
import io.github.cmix7777.kazhdyidnevnik.applyAppChrome
import io.github.cmix7777.kazhdyidnevnik.data.ChatLogic
import io.github.cmix7777.kazhdyidnevnik.data.ChatMessage
import io.github.cmix7777.kazhdyidnevnik.data.ChatPhoto
import io.github.cmix7777.kazhdyidnevnik.data.ChatRow
import io.github.cmix7777.kazhdyidnevnik.data.ChatState
import io.github.cmix7777.kazhdyidnevnik.data.ChatTick
import io.github.cmix7777.kazhdyidnevnik.data.Person
import io.github.cmix7777.kazhdyidnevnik.formatDate
import io.github.cmix7777.kazhdyidnevnik.formatShortDay
import io.github.cmix7777.kazhdyidnevnik.formatTime
import io.github.cmix7777.kazhdyidnevnik.notify.AppSettings
import io.github.cmix7777.kazhdyidnevnik.service.ChatFiles
import io.github.cmix7777.kazhdyidnevnik.service.ChatSync
import io.github.cmix7777.kazhdyidnevnik.ui.fx.EffectsSettings
import io.github.cmix7777.kazhdyidnevnik.ui.fx.Haptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Переписка на двоих. Отдельное окно, а не слой поверх дневника: пока оно открыто, живые эффекты
 * дневника (небо, полоска дня) стоят и не тратят батарею.
 */
class ChatActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = AppSettings(this)
        ThemeSettings.load(settings)
        EffectsSettings.load(settings)
        val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        applyAppChrome(ThemeSettings.mode.isDark(systemDark))
        setContent {
            val dark = ThemeSettings.isDark()
            DisposableEffect(dark) {
                applyAppChrome(dark)
                onDispose { }
            }
            AppTheme {
                ChatRoot(onBack = { finish() })
            }
        }
    }
}

/** Переписка с настоящими данными: сообщения из [ChatSync], фото из галереи. */
@Composable
fun ChatRoot(onBack: () -> Unit) {
    val context = LocalContext.current
    val owner = remember { AppSettings(context).owner }
    if (owner == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val state by ChatSync.state.collectAsState()
    val status by ChatSync.status.collectAsState()
    LifecycleResumeEffect(Unit) {
        ChatSync.open(context)
        onPauseOrDispose { ChatSync.close(context) }
    }
    // Пока экран на виду, всё пришедшее сразу прочитано.
    val newest = state.theirs.lastOrNull()?.seq ?: 0L
    LaunchedEffect(newest) { if (newest > state.readUpTo) ChatSync.markRead(context) }

    var photoReply by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) ChatSync.sendPhoto(context, uri, "", photoReply)
        photoReply = null
    }
    var viewing by remember { mutableStateOf<ChatPhoto?>(null) }

    PersonTheme(owner) {
        GlowBackground {
            ChatScreen(
                owner = owner,
                state = state,
                status = status,
                onBack = onBack,
                onSend = { text, reply ->
                    ChatSync.send(context, text, reply)
                    Haptics.tick(context)
                },
                onPickPhoto = { reply ->
                    photoReply = reply
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onDelete = { ChatSync.delete(context, it) },
                onTyping = { typing -> if (typing) ChatSync.typing(context) else ChatSync.stopTyping(context) },
                onOpenPhoto = { viewing = it },
                onRetry = { ChatSync.retry(context) },
                photoContent = { photo, modifier -> ChatPhotoView(photo, modifier) },
            )
        }
        viewing?.let { photo -> PhotoViewer(photo, onClose = { viewing = null }) }
    }
}

/**
 * Экран переписки без сети и хранилища: всё нужное приходит параметрами (так его проверяют тесты).
 * [photoContent] рисует фото по номеру.
 */
@Composable
fun ChatScreen(
    owner: Person,
    state: ChatState,
    status: ChatSync.Status,
    onBack: () -> Unit,
    onSend: (text: String, replyTo: String?) -> Unit,
    onPickPhoto: (replyTo: String?) -> Unit,
    onDelete: (ChatMessage) -> Unit,
    onTyping: (Boolean) -> Unit,
    onOpenPhoto: (ChatPhoto) -> Unit,
    onRetry: () -> Unit,
    photoContent: @Composable (ChatPhoto, Modifier) -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
    initialText: String = "",
) {
    val partner = owner.partner
    val dark = LocalDark.current
    val colors = BubbleColors(
        mineTop = Accents.of(owner, dark).primary,
        mineBottom = Accents.of(owner, dark).deep,
        theirTop = Accents.of(partner, dark).cardTop,
        theirBottom = Accents.of(partner, dark).cardBottom,
        theirBorder = Accents.of(partner, dark).primary,
        theirAccent = Accents.of(partner, dark).light,
    )
    val rows = remember(state, owner, zone) { ChatLogic.rows(state, owner, zone) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf(initialText) }
    var replyKey by rememberSaveable { mutableStateOf<String?>(null) }
    val replyTo = remember(replyKey, state) { ChatLogic.find(state, replyKey) }
    val maxBubble = (LocalConfiguration.current.screenWidthDp * 0.78f).dp
    val hasPending = state.mine.lastOrNull()?.let { it.seq > state.uploaded } == true

    // Пришло новое сообщение, а человек и так внизу переписки: показываем его.
    val newestKey = rows.firstOrNull { it is ChatRow.Message }?.key
    LaunchedEffect(newestKey) {
        if (newestKey != null && listState.firstVisibleItemIndex <= 2) listState.animateScrollToItem(0)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        ChatTopBar(partner = partner, status = status, colors = colors, onBack = onBack)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when {
                !status.paired && status.loaded -> CenterNote(
                    title = "Телефоны не связаны",
                    text = "Переписка работает через ту же связь, что и обмен делами: Настройки, «Связь с ${partner.instrumental}». " +
                        "Один создаёт код, второй вставляет.",
                )
                rows.isEmpty() && status.loaded && !status.partnerTyping -> CenterNote(
                    title = if (status.restoring) "Забираю переписку…" else "Здесь будет ваша переписка",
                    text = "Только на двоих: сообщения шифруются кодом связи прямо на телефоне, прочитать их можете только вы с ${partner.instrumental}.",
                )
                else -> LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .topFade(18.dp)
                        .testTag("chat-list"),
                ) {
                    if (status.partnerTyping) {
                        item(key = "typing", contentType = "typing") { TypingBubble(colors) }
                    }
                    items(rows, key = { it.key }, contentType = { if (it is ChatRow.Day) "day" else "message" }) { row ->
                        when (row) {
                            is ChatRow.Day -> DayLabel(row.date, zone)
                            is ChatRow.Message -> MessageBubble(
                                row = row,
                                owner = owner,
                                colors = colors,
                                maxWidth = maxBubble,
                                failed = row.tick == ChatTick.PENDING && status.problem != null,
                                zone = zone,
                                onReply = { replyKey = row.message.key },
                                onDelete = { onDelete(row.message) },
                                onOpenPhoto = onOpenPhoto,
                                onRetry = onRetry,
                                photoContent = photoContent,
                            )
                        }
                    }
                }
            }
            val showDown by remember { derivedStateOf { listState.firstVisibleItemIndex > 3 } }
            if (showDown) {
                GlassIconButton(
                    icon = R.drawable.ic_arrow_down,
                    contentDescription = "К новым сообщениям",
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(14.dp)
                        .background(Palette.Background.copy(alpha = 0.85f), CircleShape),
                )
            }
        }
        if (hasPending && status.problem != null) {
            Text(
                text = "Нет связи: неотправленное уйдёт само, когда она появится. Нажми, чтобы попробовать сейчас.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.DangerText,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        replyTo?.let { target ->
            ReplyBar(
                message = target,
                author = if (target.from == owner.id) "Ты" else partner.shortName,
                colors = colors,
                mine = target.from == owner.id,
                onCancel = { replyKey = null },
            )
        }
        InputBar(
            text = text,
            enabled = status.paired,
            onText = {
                text = it
                onTyping(it.isNotBlank())
            },
            onSend = {
                val message = text.trim()
                if (message.isNotEmpty()) {
                    onSend(message, replyKey)
                    text = ""
                    replyKey = null
                    scope.launch { listState.scrollToItem(0) }
                }
            },
            onPhoto = {
                onPickPhoto(replyKey)
                replyKey = null
            },
        )
    }
}

/** Цвета пузырей: свои — в палитре владельца, чужие — стекло в палитре второго человека. */
private class BubbleColors(
    val mineTop: Color,
    val mineBottom: Color,
    val theirTop: Color,
    val theirBottom: Color,
    val theirBorder: Color,
    val theirAccent: Color,
)

@Composable
private fun ChatTopBar(partner: Person, status: ChatSync.Status, colors: BubbleColors, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 14.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
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
                else -> null
            }
            if (line != null) {
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
}

@Composable
private fun CenterNote(title: String, text: String) {
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
private fun DayLabel(date: LocalDate, zone: ZoneId) {
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
private fun MessageBubble(
    row: ChatRow.Message,
    owner: Person,
    colors: BubbleColors,
    maxWidth: Dp,
    failed: Boolean,
    zone: ZoneId,
    onReply: () -> Unit,
    onDelete: () -> Unit,
    onOpenPhoto: (ChatPhoto) -> Unit,
    onRetry: () -> Unit,
    photoContent: @Composable (ChatPhoto, Modifier) -> Unit,
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (row.first) 8.dp else 2.dp)
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
        Box(modifier = Modifier.offset { IntOffset(drag.value.roundToInt(), 0) }) {
            Column(
                modifier = Modifier
                    .widthIn(max = maxWidth)
                    .clip(shape)
                    .background(background)
                    .then(if (row.mine) Modifier else Modifier.border(1.dp, colors.theirBorder.copy(alpha = 0.3f), shape))
                    .combinedClickable(
                        enabled = interactive,
                        onClick = {
                            when {
                                failed -> onRetry()
                                message.photo != null -> onOpenPhoto(message.photo)
                            }
                        },
                        onLongClick = { menu = true },
                    ),
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
                    val ratio = (photo.width.toFloat() / photo.height.coerceAtLeast(1)).coerceIn(0.6f, 1.8f)
                    Box(
                        modifier = Modifier
                            .width(maxWidth.coerceAtMost(280.dp))
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
                if (message.deleted || message.text.isNotEmpty()) {
                    TextWithMeta(
                        text = if (message.deleted) "Сообщение удалено" else message.text,
                        style = MaterialTheme.typography.bodyLarge.let { if (message.deleted) it.copy(fontStyle = FontStyle.Italic) else it },
                        color = if (message.deleted) metaColor else textColor,
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                    ) { Meta(message, row.tick, failed, metaColor, zone) }
                }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Ответить") },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_reply), contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        menu = false
                        onReply()
                    },
                )
                if (message.text.isNotEmpty()) {
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
                if (row.mine) {
                    DropdownMenuItem(
                        text = { Text("Удалить у обоих") },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            menu = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

/** Время и отметка «ушло / прочитано» в углу пузыря. */
@Composable
private fun Meta(message: ChatMessage, tick: ChatTick, failed: Boolean, color: Color, zone: ZoneId) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
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
private fun TextWithMeta(
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
private fun ReplyQuote(message: ChatMessage, author: String, mineBubble: Boolean, colors: BubbleColors) {
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
private fun ReplyBar(message: ChatMessage, author: String, colors: BubbleColors, mine: Boolean, onCancel: () -> Unit) {
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

@Composable
private fun InputBar(text: String, enabled: Boolean, onText: (String) -> Unit, onSend: () -> Unit, onPhoto: () -> Unit) {
    val canSend = enabled && text.isNotBlank()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GlassIconButton(icon = R.drawable.ic_photo, contentDescription = "Отправить фото", onClick = onPhoto, enabled = enabled)
        val shape = RoundedCornerShape(22.dp)
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 44.dp)
                .clip(shape)
                .background(Palette.Chip)
                .border(1.dp, Palette.BorderStrong, shape)
                .padding(horizontal = 16.dp, vertical = 11.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = text,
                onValueChange = onText,
                enabled = enabled,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Palette.Text),
                cursorBrush = SolidColor(Palette.Violet),
                maxLines = 6,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("chat-input"),
                decorationBox = { inner ->
                    if (text.isEmpty()) {
                        Text(text = "Сообщение", style = MaterialTheme.typography.bodyLarge, color = Palette.TextFaint)
                    }
                    inner()
                },
            )
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (canSend) Brush.linearGradient(listOf(Palette.Violet, Palette.VioletDeep)) else SolidColor(Palette.Chip),
                )
                .clickable(enabled = canSend, role = Role.Button, onClick = onSend)
                .testTag("chat-send"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_send),
                contentDescription = "Отправить",
                tint = if (canSend) Color.White else Palette.TextFaint,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** «Печатает…»: пузырь второго человека с тремя точками. */
@Composable
private fun TypingBubble(colors: BubbleColors) {
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 6.dp)
    Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.CenterStart) {
        Row(
            modifier = Modifier
                .clip(shape)
                .background(Brush.verticalGradient(listOf(colors.theirTop.copy(alpha = 0.96f), colors.theirBottom.copy(alpha = 0.94f))))
                .border(1.dp, colors.theirBorder.copy(alpha = 0.3f), shape)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (EffectsSettings.reduced) {
                repeat(3) { Dot(colors.theirAccent, 0.7f) }
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
private fun Dot(color: Color, strength: Float) {
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

/** Фото в пузыре: уменьшенная копия с диска, пока не скачалось — заглушка. */
@Composable
fun ChatPhotoView(photo: ChatPhoto, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version by ChatFiles.version.collectAsState()
    val bitmap by produceState(ChatFiles.cached(photo.id, THUMB_SIDE)?.asImageBitmap(), photo.id, version) {
        if (value == null) value = withContext(Dispatchers.IO) { ChatFiles.decode(context, photo.id, THUMB_SIDE) }?.asImageBitmap()
        if (value == null) ChatSync.ensureFile(context, photo.id)
    }
    PhotoBox(bitmap, modifier)
}

@Composable
private fun PhotoBox(bitmap: ImageBitmap?, modifier: Modifier) {
    Box(modifier = modifier.background(Palette.Chip), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = "Фото", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Palette.Lavender)
        }
    }
}

/** Фото во весь экран: щипок увеличивает, касание закрывает. */
@Composable
private fun PhotoViewer(photo: ChatPhoto, onClose: () -> Unit) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, photo.id) {
        value = withContext(Dispatchers.IO) { ChatFiles.decode(context, photo.id, FULL_SIDE) }?.asImageBitmap()
    }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        var scale by remember { mutableFloatStateOf(1f) }
        var shiftX by remember { mutableFloatStateOf(0f) }
        var shiftY by remember { mutableFloatStateOf(0f) }
        val transform = rememberTransformableState { zoom, pan, _ ->
            scale = (scale * zoom).coerceIn(1f, 5f)
            shiftX = if (scale > 1f) shiftX + pan.x else 0f
            shiftY = if (scale > 1f) shiftY + pan.y else 0f
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) { detectTapGestures(onTap = { onClose() }) }
                .transformable(transform),
            contentAlignment = Alignment.Center,
        ) {
            val image = bitmap
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = "Фото",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = shiftX
                            translationY = shiftY
                        },
                )
            } else {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

/** Верхний край ленты: пузыри уходят под него плавно, без резкой обрезки и тёмной полосы. */
private fun Modifier.topFade(height: Dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = 0f, endY = height.toPx()),
            blendMode = BlendMode.DstIn,
        )
    }

private const val THUMB_SIDE = 900
private const val FULL_SIDE = 2400
