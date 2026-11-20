package io.github.cmix7777.kazhdyidnevnik.ui

import io.github.cmix7777.kazhdyidnevnik.ui.fx.Holidays
import io.github.cmix7777.kazhdyidnevnik.ui.fx.HolidaySnow
import io.github.cmix7777.kazhdyidnevnik.ui.fx.HolidayGarland
import io.github.cmix7777.kazhdyidnevnik.ui.fx.HolidaySaluteHost
import io.github.cmix7777.kazhdyidnevnik.ui.fx.rememberHoliday
import io.github.cmix7777.kazhdyidnevnik.ui.fx.rememberHolidayIntro
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

/**
 * Переписка на двоих. Отдельное окно, а не слой поверх дневника: пока оно открыто, живые эффекты
 * дневника стоят и не тратят батарею. Окно прозрачное, поэтому открывается каплей от кнопки
 * переписки: круг растёт из того места, куда нажали, а при закрытии схлопывается обратно.
 */
class ChatActivity : ComponentActivity() {
    private var locked by mutableStateOf(false)
    private var prompting = false

    override fun onResume() {
        super.onResume()
        val settings = AppSettings(this)
        val secure = getSystemService(android.app.KeyguardManager::class.java)?.isDeviceSecure == true
        if (!settings.chatLock || !secure) {
            locked = false
            return
        }
        if (!locked && System.currentTimeMillis() - settings.chatSeenMillis > LOCK_AFTER_MS) locked = true
        if (locked && !prompting) unlock()
    }

    override fun onPause() {
        super.onPause()
        if (!locked && !prompting) AppSettings(this).chatSeenMillis = System.currentTimeMillis()
    }

    private fun unlock() {
        prompting = true
        val builder = android.hardware.biometrics.BiometricPrompt.Builder(this)
            .setTitle("Переписка")
            .setSubtitle("Подтверди, что это ты")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(
                android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        }
        val callback = object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult?) {
                prompting = false
                locked = false
                AppSettings(this@ChatActivity).chatSeenMillis = System.currentTimeMillis()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                prompting = false
            }
        }
        runCatching { builder.build().authenticate(android.os.CancellationSignal(), mainExecutor, callback) }
            .onFailure {
                prompting = false
                locked = false
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = AppSettings(this)
        ThemeSettings.load(settings)
        EffectsSettings.load(settings)
        EffectsSettings.watchPower(this)
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
                    if (locked) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Palette.Background)
                                .pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume() } },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                        ) {
                            Text("Переписка закрыта", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                            PillButton(text = "Открыть", onClick = { if (!prompting) unlock() })
                            GhostButton("Назад", onClick = close, small = true)
                        }
                    }
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
        private const val LOCK_AFTER_MS = 60 * 60 * 1000L
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
internal fun cameraTarget(context: Context): Uri {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    return FileProvider.getUriForFile(context, context.packageName + ".files", File(dir, "photo.jpg"))
}

/** Почерк ждёт букв: какие дописать и что потом отправить запиской. */
internal class HandRequest(val letters: List<String>, val text: String?, val replyTo: String?)

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
    var wallpaperOpen by remember { mutableStateOf(false) }
    remember { WallpaperStore.load(context) }
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
                        "wallpaper" -> wallpaperOpen = true
                    }
                },
                onEdit = { message, text -> ChatSync.edit(context, message, text) },
                onSchedule = { text, at, reply -> ChatSync.schedule(context, text, at, reply) },
                onCancelScheduled = { id -> ChatSync.cancelScheduled(context, id) },
                onGame = { reply -> ChatSync.startGame(context, reply) },
                onGameMove = { game, cell -> ChatSync.playGame(context, game, cell) },
                onPin = { message, on -> ChatSync.pin(context, message, on) },
                wallpaper = WallpaperStore.choice,
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
        if (wallpaperOpen) {
            WallpaperDialog(
                choice = WallpaperStore.choice,
                owner = owner,
                onChange = { value, final -> WallpaperStore.set(context, value, persist = final) },
                onClose = {
                    WallpaperStore.set(context, WallpaperStore.choice)
                    wallpaperOpen = false
                },
            )
        }
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
    onGame: (replyTo: String?) -> Unit = {},
    onGameMove: (ChatGame, Int) -> Unit = { _, _ -> },
    onPin: (ChatMessage, Boolean) -> Unit = { _, _ -> },
    wallpaper: WallpaperChoice = WallpaperChoice(),
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
    // Фон отзывается на касания, новые сообщения и наклон телефона.
    val wallLive = remember { WallpaperLive() }
    val wallShader = EffectsSettings.shadersSupported && Wallpapers.live(wallpaper.id)
    val screenOpened = remember { SystemClock.uptimeMillis() }
    WallpaperTilt(wallLive, enabled = wallShader && wallpaper.tilt && animate)
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
    var searchOpen by remember { mutableStateOf(false) }
    // Закреплённые: полоска под шапкой показывает одно, нажатие листает к нему и переключает на следующее.
    val pins = remember(state) { ChatLogic.pinned(state) }
    var pinIndex by remember { mutableIntStateOf(0) }
    // Подсветка сообщения, к которому перешли из поиска или закреплённых.
    var highlight by remember { mutableStateOf<String?>(null) }
    fun jumpTo(message: ChatMessage, earlier: Boolean = false) {
        val index = rows.indexOfFirst { it is ChatRow.Message && it.earlier == earlier && it.message.key == message.key && it.message.at == message.at }
        if (index < 0) return
        val key = rows[index].key
        scope.launch {
            listState.animateScrollToItem(index)
            highlight = key
            delay(1_800)
            if (highlight == key) highlight = null
        }
    }
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
    var lift by remember { mutableFloatStateOf(0f) }
    val holiday = rememberHoliday()
    val holidayIntro = rememberHolidayIntro(holiday)
    // Голосовое закреплено (увёл палец вверх): пишется без рук до кнопки «Отправить».
    var voiceLocked by remember { mutableStateOf(false) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    // Пришло новое сообщение, а человек и так внизу переписки: показываем его.
    val newestKey = rows.firstOrNull { it is ChatRow.Message }?.key
    LaunchedEffect(newestKey) {
        // Новое сообщение (не те, что были при открытии): по фону проходит волна.
        if (newestKey != null && SystemClock.uptimeMillis() - screenOpened > 1_500L) wallLive.pulse()
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
        Holidays.markDay(context)
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
        lift = 0f
        if (voice != null) voiceLocked = true
        circle?.locked = true
        Haptics.tick(context)
    }

    fun endRecord(cancel: Boolean) {
        slide = 0f
        lift = 0f
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
            .onGloballyPositioned { rootOffset = it.positionInRoot() }
            .wallpaperTouches(wallLive, wallShader && wallpaper.touch && animate),
    ) {
        ChatWallpaper(
            choice = wallpaper,
            owner = owner,
            running = animate && circle == null,
            live = wallLive,
            modifier = Modifier.matchParentSize(),
        )
        if (holiday != null) {
            HolidaySnow(Modifier.matchParentSize(), count = 30, alpha = 0.45f, running = circle == null)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            ChatTopBar(
                partner = partner,
                status = status,
                colors = colors,
                onBack = onBack,
                partnerNow = partnerNow,
                onMenu = { item -> if (item == "search") searchOpen = true else onMenu(item) },
            )
            if (holiday != null) {
                HolidayGarland(
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    intro = holidayIntro,
                    running = circle == null,
                )
            }
            if (pins.isNotEmpty() && status.paired) {
                val position = pinIndex.coerceIn(0, pins.lastIndex)
                PinnedBar(
                    message = pins[position],
                    position = position,
                    count = pins.size,
                    onClick = {
                        jumpTo(pins[position])
                        pinIndex = (position + 1) % pins.size
                    },
                )
            }
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
                                                onGameMove = { cell -> message.game?.let { onGameMove(it, cell) } },
                                                onGameAgain = { onGame(null) },
                                                pinned = pins.any { it.key == message.key && it.at == message.at },
                                                onPin = { on -> onPin(message, on) },
                                                highlighted = highlight == row.key,
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
                lift = lift,
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
                onGame = {
                    onGame(replyKey)
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
                onRecordLift = {
                    lift = it
                    circle?.lift = it
                },
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
        if (holiday != null) HolidaySaluteHost(holiday, 0, Modifier.matchParentSize())
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
        if (searchOpen) {
            ChatSearchDialog(
                state = state,
                owner = owner,
                zone = zone,
                onPick = { hit ->
                    searchOpen = false
                    jumpTo(hit.message, hit.earlier)
                },
                onClose = { searchOpen = false },
            )
        }
    }
}
