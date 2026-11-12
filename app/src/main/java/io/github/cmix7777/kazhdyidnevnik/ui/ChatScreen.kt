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
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
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

/**
 * Переписка на двоих. Отдельное окно, а не слой поверх дневника: пока оно открыто, живые эффекты
 * дневника стоят и не тратят батарею. Окно прозрачное, поэтому открывается каплей от кнопки
 * переписки: круг растёт из того места, куда нажали, а при закрытии схлопывается обратно.
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
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        }
        val origin = if (intent.hasExtra(EXTRA_X)) Offset(intent.getFloatExtra(EXTRA_X, 0f), intent.getFloatExtra(EXTRA_Y, 0f)) else null
        val animate = EffectsSettings.animate(this) && savedInstanceState == null
        val owner = settings.owner
        setContent {
            val dark = ThemeSettings.isDark()
            DisposableEffect(dark) {
                applyAppChrome(dark)
                onDispose { }
            }
            AppTheme {
                val scope = rememberCoroutineScope()
                // Капля: сначала растёт из кнопки и заливает экран цветом, потом тает и открывает переписку.
                val ink = remember { Animatable(if (animate) 0f else 1f) }
                val veil = remember { Animatable(if (animate) 1f else 0f) }
                var closing by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    if (animate) {
                        ink.animateTo(1f, tween(460, easing = FastOutSlowInEasing))
                        veil.animateTo(0f, tween(280))
                    }
                    // Переписка закрыла дневник целиком: его небо можно не перерисовывать.
                    SkyFx.covered = true
                }
                DisposableEffect(Unit) { onDispose { SkyFx.covered = false } }
                val close: () -> Unit = {
                    if (!closing) {
                        closing = true
                        SkyFx.covered = false
                        if (!animate) {
                            finish()
                        } else {
                            scope.launch {
                                veil.animateTo(1f, tween(150))
                                ink.animateTo(0f, tween(340, easing = FastOutSlowInEasing))
                                finish()
                            }
                        }
                    }
                }
                BackHandler { close() }
                val accent = owner?.let { Accents.of(it, dark) }
                val inkTop = accent?.primary ?: Palette.Violet
                val inkBottom = accent?.deep ?: Palette.VioletDeep
                val splashes = remember { List(10) { Triple(it / 10f * 6.283f + Random.nextFloat() * 0.4f, 0.94f + Random.nextFloat() * 0.3f, 3f + Random.nextFloat() * 6f) } }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            val p = ink.value
                            val v = veil.value
                            // Пока капля не залила экран, переписка не рисуется вовсе: под каплей виден дневник.
                            if (p >= 1f) drawContent()
                            if (v <= 0f) return@drawWithContent
                            val o = origin ?: Offset(size.width - 80.dp.toPx(), 48.dp.toPx())
                            val maxR = hypot(max(o.x, size.width - o.x), max(o.y, size.height - o.y)) * 1.04f
                            val r = maxR * p
                            if (r > 0.5f) {
                                drawCircle(Brush.radialGradient(listOf(inkTop, inkBottom), center = o, radius = r), radius = r, center = o, alpha = v)
                                val fade = (1f - p) * v
                                if (fade > 0.01f) {
                                    for ((angle, dist, radius) in splashes) {
                                        val c = Offset(o.x + cos(angle) * r * dist, o.y + sin(angle) * r * dist)
                                        drawCircle(inkTop, radius * density * (0.4f + 0.6f * (1f - p)), c, alpha = fade)
                                    }
                                }
                            }
                        },
                ) {
                    ChatRoot(onBack = close)
                }
            }
        }
    }

    override fun finish() {
        super.finish()
        if (Build.VERSION.SDK_INT < 34) {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    companion object {
        const val EXTRA_X = "ox"
        const val EXTRA_Y = "oy"

        /** Открыть переписку каплей из точки [origin] (координаты в окне). */
        fun open(context: Context, origin: Offset? = null) {
            val intent = Intent(context, ChatActivity::class.java)
            if (origin != null) {
                intent.putExtra(EXTRA_X, origin.x)
                intent.putExtra(EXTRA_Y, origin.y)
            }
            val options = ActivityOptions.makeCustomAnimation(context, 0, 0).toBundle()
            context.startActivity(intent, options)
        }
    }
}

/** Куда камера кладёт снимок: файл во временной папке, отдаётся камере через FileProvider. */
private fun cameraTarget(context: Context): Uri {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    return FileProvider.getUriForFile(context, context.packageName + ".files", File(dir, "photo.jpg"))
}

/** Почерк ждёт букв: какие дописать и что потом отправить запиской. */
private class HandRequest(val letters: List<String>, val text: String?, val replyTo: String?)

/** Переписка с настоящими данными: сообщения из [ChatSync], фото с камеры и из галереи. */
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
    val filesVersion by ChatFiles.version.collectAsState()
    LifecycleResumeEffect(Unit) {
        ChatSync.open(context)
        onPauseOrDispose {
            ChatSync.close(context)
            VoicePlayer.stop()
        }
    }
    // Пока экран на виду, всё пришедшее сразу прочитано.
    val newest = state.theirs.lastOrNull()?.seq ?: 0L
    LaunchedEffect(newest) { if (newest > state.readUpTo) ChatSync.markRead(context) }

    // Что второй человек делает сейчас — по его расписанию, раз в минуту.
    val partnerNow by produceState<String?>(null, owner) {
        while (true) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    val items = ReminderScheduler.dayItems(context, owner.partner, LocalDate.now()).orEmpty()
                    PartnerNow.line(owner.partner, items, LocalTime.now())
                }.getOrNull()
            }
            delay(60_000)
        }
    }
    val myFont by produceState<HandFont?>(null, state.myFont, filesVersion) {
        value = withContext(Dispatchers.IO) { HandFonts.load(context, state.myFont) }
    }
    val theirFont by produceState<HandFont?>(null, state.theirFont, filesVersion) {
        value = withContext(Dispatchers.IO) { HandFonts.load(context, state.theirFont) }
    }

    var photoReply by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<Uri?>(null) }
    val cameraUri = remember { cameraTarget(context) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) preview = uri }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) preview = cameraUri }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) camera.launch(cameraUri)
    }
    var viewing by remember { mutableStateOf<ChatPhoto?>(null) }
    var archive by remember { mutableStateOf(false) }
    var media by remember { mutableStateOf(false) }
    var sharedList by remember { mutableStateOf(false) }
    var fontEditor by remember { mutableStateOf(false) }
    val rootScope = rememberCoroutineScope()
    var saving by remember { mutableStateOf<ChatFile?>(null) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val reply = photoReply
        photoReply = null
        if (uri != null) {
            rootScope.launch {
                val problem = ChatSync.sendDocument(context, uri, reply)
                if (problem != null) Toast.makeText(context, problem, Toast.LENGTH_LONG).show()
            }
        }
    }
    val fileSaver = rememberLauncherForActivityResult(SaveAs()) { uri ->
        val file = saving
        saving = null
        if (uri != null && file != null) {
            rootScope.launch {
                val ok = withContext(Dispatchers.IO) { saveChatFile(context, file, uri) }
                Toast.makeText(context, if (ok) "Файл сохранён" else "Не получилось сохранить файл", Toast.LENGTH_SHORT).show()
            }
        }
    }
    var hand by remember { mutableStateOf<HandRequest?>(null) }

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
                partnerNow = partnerNow,
                myFont = myFont,
                theirFont = theirFont,
                animate = EffectsSettings.animate(context),
                onSendRich = { text, reply, effect, secret ->
                    ChatSync.send(context, text, reply, effect = effect, secret = secret)
                    Haptics.tick(context)
                },
                onSendHand = { text, reply ->
                    val missing = HandFonts.missing(text, myFont)
                    if (missing.isEmpty()) {
                        ChatSync.send(context, text, reply, hand = true)
                        Haptics.tick(context)
                    } else {
                        hand = HandRequest(missing, text, reply)
                    }
                },
                onReact = { key, emoji -> ChatSync.react(context, key, emoji) },
                onOpenSecret = { ChatSync.openSecret(it) },
                onPlayed = { ChatSync.markPlayed(it) },
                onCamera = { reply ->
                    photoReply = reply
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        camera.launch(cameraUri)
                    } else {
                        cameraPermission.launch(Manifest.permission.CAMERA)
                    }
                },
                onSticker = { file ->
                    val bytes = runCatching { file.readBytes() }.getOrNull()
                    if (bytes != null) ChatSync.sendSticker(context, bytes)
                },
                onVoice = { result, reply -> ChatSync.sendVoice(context, result.bytes, result.durationMs, result.wave, reply) },
                onCircle = { bytes, duration, reply -> ChatSync.sendCircle(context, bytes, duration, reply) },
                onEnsureFiles = { message, mine -> ChatSync.ensureFiles(context, message, mine) },
                onSaveSticker = { message ->
                    val id = message.sticker?.id
                    val bytes = id?.let { ChatFiles.read(context, it) }
                    if (bytes != null) StickerStore.add(context, bytes)
                },
                onFile = { reply ->
                    photoReply = reply
                    filePicker.launch(arrayOf("*/*"))
                },
                onOpenFile = { message -> message.file?.let { openChatFile(context, it) } },
                onSaveFile = { message ->
                    message.file?.let {
                        saving = it
                        fileSaver.launch(it.name to saveMime(it))
                    }
                },
                onMenu = { item ->
                    when (item) {
                        "hand" -> fontEditor = true
                        "archive" -> archive = true
                        "media" -> media = true
                        "list" -> sharedList = true
                    }
                },
                onEdit = { message, text -> ChatSync.edit(context, message, text) },
                onSchedule = { text, at, reply -> ChatSync.schedule(context, text, at, reply) },
                onCancelScheduled = { id -> ChatSync.cancelScheduled(context, id) },
            )
        }
        viewing?.let { photo -> PhotoViewer(photo, onClose = { viewing = null }) }
        preview?.let { uri ->
            PhotoPreviewDialog(
                uri = uri,
                onSend = { caption ->
                    ChatSync.sendPhoto(context, uri, caption, photoReply)
                    photoReply = null
                    preview = null
                },
                onCancel = {
                    photoReply = null
                    preview = null
                },
            )
        }
        if (archive) ArchiveDialog(onClose = { archive = false })
        if (media) {
            MediaGalleryDialog(
                state = state,
                owner = owner,
                photoContent = { photo, modifier -> ChatPhotoView(photo, modifier) },
                onOpenPhoto = { viewing = it },
                onOpenFile = { file -> openChatFile(context, file) },
                onEnsureFiles = { message, mine -> ChatSync.ensureFiles(context, message, mine) },
                onClose = { media = false },
            )
        }
        if (sharedList) SharedListDialog(owner = owner, onClose = { sharedList = false })
        if (fontEditor) {
            HandFontDialog(
                font = myFont,
                onRedraw = { letters ->
                    fontEditor = false
                    hand = HandRequest(letters, null, null)
                },
                onClose = { fontEditor = false },
            )
        }
        hand?.let { request ->
            HandPadDialog(
                letters = request.letters,
                start = myFont,
                onDone = { font ->
                    ChatSync.saveFont(context, HandFonts.encode(font))
                    request.text?.let { ChatSync.send(context, it, request.replyTo, hand = true) }
                    hand = null
                    // Переписывал буквы из окна «Мой почерк» — возвращаемся туда и видим, что вышло.
                    if (request.text == null) fontEditor = true
                },
                onCancel = {
                    if (request.text == null) fontEditor = true
                    hand = null
                },
            )
        }
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
    partnerNow: String? = null,
    myFont: HandFont? = null,
    theirFont: HandFont? = null,
    animate: Boolean = true,
    onSendRich: (text: String, replyTo: String?, effect: String?, secret: Boolean) -> Unit = { t, r, _, _ -> onSend(t, r) },
    onSendHand: (text: String, replyTo: String?) -> Unit = { t, r -> onSend(t, r) },
    onReact: (key: String, emoji: String?) -> Unit = { _, _ -> },
    onOpenSecret: (String) -> Unit = {},
    onPlayed: (String) -> Unit = {},
    onCamera: (replyTo: String?) -> Unit = {},
    onSticker: (File) -> Unit = {},
    onVoice: (VoiceRecorder.Result, String?) -> Unit = { _, _ -> },
    onCircle: (ByteArray, Long, String?) -> Unit = { _, _, _ -> },
    onEnsureFiles: (ChatMessage, Boolean) -> Unit = { _, _ -> },
    onSaveSticker: (ChatMessage) -> Unit = {},
    onFile: (replyTo: String?) -> Unit = {},
    onOpenFile: (ChatMessage) -> Unit = {},
    onSaveFile: (ChatMessage) -> Unit = {},
    onMenu: (String) -> Unit = {},
    onEdit: (ChatMessage, String) -> Unit = { _, _ -> },
    onSchedule: (text: String, at: Long, replyTo: String?) -> Unit = { _, _, _ -> },
    onCancelScheduled: (String) -> Unit = {},
) {
    val partner = owner.partner
    val dark = LocalDark.current
    val context = LocalContext.current
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
    // Исправление своего сообщения: его текст в поле ввода, «Отправить» сохраняет правку.
    var editKey by rememberSaveable { mutableStateOf<String?>(null) }
    val editing = remember(editKey, state) { state.mine.firstOrNull { it.key == editKey && !it.deleted } }
    var laterOpen by remember { mutableStateOf(false) }
    var scheduledOpen by remember { mutableStateOf(false) }
    val maxBubble = (LocalConfiguration.current.screenWidthDp * 0.78f).dp
    val hasPending = state.mine.lastOrNull()?.let { it.seq > state.uploaded } == true
    val fx = remember { WordFxState() }
    val dust = remember { DustState() }
    var rootOffset by remember { mutableStateOf(Offset.Zero) }
    val prefs = remember { context.getSharedPreferences("chat-ui", Context.MODE_PRIVATE) }
    var zoom by remember { mutableFloatStateOf(prefs.getFloat("zoom", 1f).let { if (it.isFinite()) it.coerceIn(0.7f, 1.7f) else 1f }) }
    var zoomShown by remember { mutableStateOf(false) }
    var panel by rememberSaveable { mutableStateOf(false) }
    val written = remember { HashSet<String>() }
    val keyboard = LocalSoftwareKeyboardController.current
    val densityNow = LocalDensity.current
    val zoomDensity = remember(densityNow, zoom) { Density(densityNow.density * zoom, densityNow.fontScale) }

    // Запись голосового или кружочка.
    var circleMode by rememberSaveable { mutableStateOf(false) }
    var voice by remember { mutableStateOf<VoiceRecorder?>(null) }
    var voiceLevel by remember { mutableFloatStateOf(0f) }
    var voiceTime by remember { mutableLongStateOf(0L) }
    var circle by remember { mutableStateOf<CircleSession?>(null) }
    var slide by remember { mutableFloatStateOf(0f) }
    // Голосовое закреплено (увёл палец вверх): пишется без рук до кнопки «Отправить».
    var voiceLocked by remember { mutableStateOf(false) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    // Пришло новое сообщение, а человек и так внизу переписки: показываем его.
    val newestKey = rows.firstOrNull { it is ChatRow.Message }?.key
    LaunchedEffect(newestKey) {
        if (newestKey != null && listState.firstVisibleItemIndex <= 2) listState.animateScrollToItem(0)
    }
    // Её сообщение с эффектом: эффект играет один раз (и не повторяется при следующем открытии).
    val newestTheirs = state.theirs.lastOrNull { !it.deleted }
    LaunchedEffect(newestTheirs?.key) {
        val message = newestTheirs ?: return@LaunchedEffect
        val effect = message.effect ?: return@LaunchedEffect
        if (message.key in state.played) return@LaunchedEffect
        if (animate && System.currentTimeMillis() - message.at < 10 * 60_000L) {
            delay(250)
            fx.play(effect)
        }
        onPlayed(message.key)
    }
    LaunchedEffect(zoomShown, zoom) {
        if (zoomShown) {
            delay(900)
            zoomShown = false
        }
    }
    LaunchedEffect(voice) {
        val recorder = voice ?: return@LaunchedEffect
        while (voice === recorder) {
            voiceLevel = recorder.level()
            voiceTime = recorder.elapsed
            if (voiceTime >= VoiceRecorder.MAX_MS) {
                voice = null
                voiceLocked = false
                recorder.stop()?.let { onVoice(it, replyKey) }
                replyKey = null
            }
            delay(70)
        }
    }

    fun sendNow(effect: String?, secret: Boolean) {
        val message = text.trim()
        editing?.let { target ->
            onEdit(target, message)
            editKey = null
            text = ""
            return
        }
        if (message.isEmpty()) return
        val chosen = effect ?: if (!secret) ChatLogic.detectEffect(message) else null
        onSendRich(message, replyKey, chosen, secret)
        if (chosen != null && animate) fx.play(chosen)
        text = ""
        replyKey = null
        scope.launch { listState.scrollToItem(0) }
    }

    fun startRecord(): Boolean {
        val needed = if (circleMode) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA) else arrayOf(Manifest.permission.RECORD_AUDIO)
        if (needed.any { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }) {
            permissions.launch(needed)
            return false
        }
        VoicePlayer.stop()
        if (circleMode) {
            circle = CircleSession()
        } else {
            val recorder = VoiceRecorder(context)
            if (!recorder.start()) return false
            voice = recorder
        }
        Haptics.tick(context)
        return true
    }

    /** Палец ушёл вверх: запись продолжается без рук. */
    fun lockRecord() {
        slide = 0f
        if (voice != null) voiceLocked = true
        circle?.locked = true
        Haptics.tick(context)
    }

    fun endRecord(cancel: Boolean) {
        slide = 0f
        voiceLocked = false
        voice?.let { recorder ->
            voice = null
            if (cancel) {
                recorder.cancel()
            } else {
                recorder.stop()?.let { onVoice(it, replyKey) }
                replyKey = null
            }
        }
        circle?.let { session -> if (cancel) session.cancelled = true else session.stopRequested = true }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { rootOffset = it.positionInRoot() },
    ) {
        ChatSmoke(
            mine = colors.mineTop,
            theirs = colors.theirBorder,
            background = Accents.of(owner, dark).background,
            running = animate && circle == null,
            modifier = Modifier.matchParentSize(),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            ChatTopBar(partner = partner, status = status, colors = colors, onBack = onBack, partnerNow = partnerNow, onMenu = onMenu)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pinchZoom(
                        onZoom = { z ->
                            if (z.isFinite() && z > 0f) zoom = (zoom * z).coerceIn(0.7f, 1.7f)
                            zoomShown = true
                        },
                        onEnd = { prefs.edit().putFloat("zoom", zoom).apply() },
                    ),
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
                    else -> CompositionLocalProvider(LocalDensity provides zoomDensity) {
                        LazyColumn(
                            state = listState,
                            reverseLayout = true,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .topFade(18.dp)
                                .testTag("chat-list"),
                        ) {
                            if (status.partnerTyping) {
                                item(key = "typing", contentType = "typing") { TypingBubble(colors, animate) }
                            }
                            items(rows, key = { it.key }, contentType = { if (it is ChatRow.Day) "day" else "message" }) { row ->
                                Box(modifier = Modifier.animateItem()) {
                                    when (row) {
                                        is ChatRow.Day -> DayLabel(row.date, zone)
                                        is ChatRow.Message -> {
                                            val message = row.message
                                            val writeNow = animate && message.hand && message.key !in written &&
                                                System.currentTimeMillis() - message.at < 3 * 60_000L
                                            MessageBubble(
                                                row = row,
                                                owner = owner,
                                                colors = colors,
                                                maxWidth = maxBubble / zoom,
                                                failed = row.tick == ChatTick.PENDING && status.problem != null,
                                                zone = zone,
                                                animate = animate,
                                                opened = message.key in state.opened,
                                                font = if (row.mine) myFont else theirFont,
                                                writeNow = writeNow,
                                                onWritten = { written += message.key },
                                                onReply = { replyKey = message.key },
                                                onDelete = { bitmap, bounds ->
                                                    if (bitmap != null) dust.add(bitmap, bounds.topLeft - rootOffset, densityNow.density)
                                                    onDelete(message)
                                                },
                                                onOpenPhoto = onOpenPhoto,
                                                onRetry = onRetry,
                                                photoContent = photoContent,
                                                onReact = { emoji ->
                                                    onReact(message.key, emoji)
                                                },
                                                onHeart = { center ->
                                                    if (row.myReaction != HEART_EMOJI && animate) fx.play(WordFxNames.POP, center - rootOffset)
                                                    onReact(message.key, HEART_EMOJI)
                                                    Haptics.tick(context)
                                                },
                                                onOpenSecret = { onOpenSecret(message.key) },
                                                onEnsureFiles = { onEnsureFiles(message, row.mine) },
                                                onSaveSticker = { onSaveSticker(message) },
                                                onOpenFile = { onOpenFile(message) },
                                                onSaveFile = { onSaveFile(message) },
                                                canEdit = row.mine && !row.earlier && ChatLogic.canEdit(state, message),
                                                onEdit = {
                                                    editKey = message.key
                                                    replyKey = null
                                                    text = message.text
                                                },
                                            )
                                        }
                                    }
                                }
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
                if (zoomShown) {
                    Text(
                        text = "${(zoom * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = Palette.Text,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                            .clip(CircleShape)
                            .background(Palette.Background.copy(alpha = 0.9f))
                            .border(1.dp, Palette.BorderStrong, CircleShape)
                            .padding(horizontal = 12.dp, vertical = 5.dp),
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
            if (state.scheduled.isNotEmpty()) {
                ScheduledStrip(count = state.scheduled.size, next = state.scheduled.first().at, zone = zone, onClick = { scheduledOpen = true })
            }
            editing?.let { target ->
                ReplyBar(
                    message = target,
                    author = "Исправить сообщение",
                    colors = colors,
                    mine = true,
                    onCancel = {
                        editKey = null
                        text = ""
                    },
                )
            }
            replyTo?.takeIf { editing == null }?.let { target ->
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
                panel = panel,
                circleMode = circleMode,
                recording = voice != null,
                locked = voiceLocked,
                recordTime = voiceTime,
                recordLevel = voiceLevel,
                slide = slide,
                onText = {
                    text = it
                    onTyping(it.isNotBlank())
                },
                onFocus = { if (it) panel = false },
                onSend = { sendNow(null, false) },
                onSpecial = { option ->
                    when (option) {
                        "secret" -> sendNow(null, true)
                        "later" -> if (text.isNotBlank() && editing == null) laterOpen = true
                        "hand" -> {
                            val message = text.trim()
                            if (message.isNotEmpty()) {
                                onSendHand(message, replyKey)
                                text = ""
                                replyKey = null
                            }
                        }
                        else -> sendNow(option, false)
                    }
                },
                onGallery = {
                    onPickPhoto(replyKey)
                    replyKey = null
                },
                onCamera = {
                    onCamera(replyKey)
                    replyKey = null
                },
                onFile = {
                    onFile(replyKey)
                    replyKey = null
                },
                onTogglePanel = {
                    if (panel) {
                        panel = false
                    } else {
                        keyboard?.hide()
                        panel = true
                    }
                },
                onToggleMode = { circleMode = !circleMode },
                onRecordStart = { startRecord() },
                onRecordSlide = { slide = it },
                onRecordLock = { lockRecord() },
                onRecordEnd = { cancel -> endRecord(cancel) },
            )
            if (panel) {
                EmojiPanel(
                    onEmoji = { emoji ->
                        text += emoji
                        onTyping(true)
                    },
                    onSticker = { file ->
                        onSticker(file)
                        panel = false
                        scope.launch { listState.scrollToItem(0) }
                    },
                    modifier = Modifier.navigationBarsPadding(),
                )
            }
        }
        WordFxLayer(fx)
        DustLayer(dust)
        circle?.let { session ->
            CircleRecorderOverlay(session = session, accent = colors.mineTop, onResult = { bytes, duration ->
                circle = null
                if (bytes != null) {
                    onCircle(bytes, duration, replyKey)
                    replyKey = null
                }
            })
        }
        if (laterOpen) {
            SendLaterDialog(
                zone = zone,
                onPick = { at ->
                    laterOpen = false
                    onSchedule(text.trim(), at, replyKey)
                    text = ""
                    replyKey = null
                },
                onDismiss = { laterOpen = false },
            )
        }
        if (scheduledOpen) {
            ScheduledDialog(
                items = state.scheduled,
                zone = zone,
                onCancelItem = onCancelScheduled,
                onDismiss = { scheduledOpen = false },
            )
        }
    }
}

private const val HEART_EMOJI = "❤️"
private val REACTIONS = listOf(HEART_EMOJI, "😂", "👍", "😮", "😢", "🔥")

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
private fun ChatTopBar(
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
                    }
                    if (message.deleted || message.text.isNotEmpty()) {
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
private fun BigEmojiText(text: String, count: Int, bounce: Boolean) {
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
private fun ReactionChip(reactions: List<String>, border: Color, modifier: Modifier = Modifier) {
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
private fun Meta(message: ChatMessage, tick: ChatTick, failed: Boolean, color: Color, zone: ZoneId, reactions: List<String> = emptyList()) {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InputBar(
    text: String,
    enabled: Boolean,
    panel: Boolean,
    circleMode: Boolean,
    recording: Boolean,
    locked: Boolean,
    recordTime: Long,
    recordLevel: Float,
    slide: Float,
    onText: (String) -> Unit,
    onFocus: (Boolean) -> Unit,
    onSend: () -> Unit,
    onSpecial: (String) -> Unit,
    onGallery: () -> Unit,
    onCamera: () -> Unit,
    onFile: () -> Unit,
    onTogglePanel: () -> Unit,
    onToggleMode: () -> Unit,
    onRecordStart: () -> Boolean,
    onRecordSlide: (Float) -> Unit,
    onRecordLock: () -> Unit,
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
                    } else Text(
                        text = if (slide < -cancelDistance * 0.6f) "отпусти — отмена" else "влево — отмена, вверх — без рук",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextFaint,
                        maxLines = 1,
                        modifier = Modifier.graphicsLayer { translationX = slide.coerceIn(-cancelDistance, 0f) * 0.4f },
                    )
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

private class EffectOption(val key: String, val title: String, val hint: String, val from: Color, val to: Color)

private val EFFECT_OPTIONS = listOf(
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
private fun TypingBubble(colors: BubbleColors, animate: Boolean) {
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
    PhotoBox(bitmap, modifier, gone = bitmap == null && ChatFiles.gone(context, photo.id))
}

@Composable
private fun PhotoBox(bitmap: ImageBitmap?, modifier: Modifier, gone: Boolean = false) {
    Box(modifier = modifier.background(Palette.Chip), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = "Фото", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else if (gone) {
            Text(
                text = "Фото стёрто из облака после доставки. Его можно вернуть из архива переписки.",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(14.dp),
            )
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
