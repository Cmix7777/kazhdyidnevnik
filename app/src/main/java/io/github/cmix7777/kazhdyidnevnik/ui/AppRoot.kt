package io.github.cmix7777.kazhdyidnevnik.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import io.github.cmix7777.kazhdyidnevnik.data.ChatMessage
import kotlinx.coroutines.delay
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.cmix7777.kazhdyidnevnik.R
import io.github.cmix7777.kazhdyidnevnik.data.ChatLogic
import io.github.cmix7777.kazhdyidnevnik.data.Person
import io.github.cmix7777.kazhdyidnevnik.service.ChatSync
import io.github.cmix7777.kazhdyidnevnik.service.PartnerSync
import io.github.cmix7777.kazhdyidnevnik.ui.fx.InkOrigin
import io.github.cmix7777.kazhdyidnevnik.ui.fx.inkPage
import io.github.cmix7777.kazhdyidnevnik.ui.fx.inkTouchOrigin
import io.github.cmix7777.kazhdyidnevnik.ui.fx.rememberSky
import kotlinx.coroutines.launch
import kotlin.math.abs

private enum class Tab(val title: String, @param:DrawableRes val icon: Int) {
    Today("Сегодня", R.drawable.ic_nav_today),
    Week("Неделя", R.drawable.ic_nav_week),
    Budget("Бюджет", R.drawable.ic_nav_budget),
    Deadlines("Даты", R.drawable.ic_nav_deadlines),
    Progress("Прогресс", R.drawable.ic_nav_progress),
}

@Composable
fun AppRoot(
    vm: ScheduleViewModel = viewModel(),
    extras: ExtrasViewModel = viewModel(),
    diary: DiaryViewModel = viewModel(),
) {
    // При первом запуске один раз спрашиваем разрешение на уведомления (Android 13+).
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val needsAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsAsk && vm.shouldAskNotifications()) {
            vm.markNotificationsAsked()
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val dark = LocalDark.current
    val owner = vm.owner
    // Смены владельца из профиля один раз переносятся в его дела, дальше он меняет их сам.
    LaunchedEffect(owner) { owner?.let { diary.seedFor(it) } }
    // Каждый раз, когда приложение открывают, подтягиваем дела второго человека (не чаще раза в минуту).
    LifecycleResumeEffect(owner) {
        if (owner != null) diary.syncPartner(force = false)
        onPauseOrDispose { }
    }
    if (owner == null) {
        AccentTheme(Accents.blend(Accents.of(Person.AIZAT, dark), Accents.of(Person.NASTYA, dark), 0.5f)) {
            OwnerPicker(onPick = { vm.chooseOwner(it) })
        }
    } else {
        MainPager(vm, extras, diary, owner)
    }
}

/** Две страницы: Айзат слева, Настя справа. Открывается страница владельца телефона. */
@Composable
private fun MainPager(vm: ScheduleViewModel, extras: ExtrasViewModel, diary: DiaryViewModel, owner: Person) {
    val dark = LocalDark.current
    var current by rememberSaveable { mutableStateOf(Tab.Today) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    // Совместная неделя открыта: номер недели относительно текущей, null — закрыта.
    var together by rememberSaveable { mutableStateOf<Int?>(null) }
    // Насколько совместная неделя закрыла страницы (0 — её нет): страницы под ней гаснут.
    var togetherCover by remember { mutableFloatStateOf(0f) }
    val pagerState = rememberPagerState(initialPage = owner.ordinal) { Person.entries.size }
    val scope = rememberCoroutineScope()
    val position by remember {
        derivedStateOf { (pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceIn(0f, 1f) }
    }
    // Рамка (фон, верх и низ) плавно перетекает из палитры Айзата в палитру Насти при свайпе.
    val chrome = if (settingsOpen) {
        Accents.of(owner, dark)
    } else {
        Accents.blend(Accents.of(Person.AIZAT, dark), Accents.of(Person.NASTYA, dark), position)
    }

    // Живое небо в фоне: солнце, луна и звёзды по времени, облака по прогнозу.
    val sky = rememberSky(extras.forecast)
    val ink = remember { InkOrigin() }
    // Капля привета: зажать имя второго человека, чтобы вырастить и бросить каплю.
    val greeting = rememberGreetingDrop()
    val sync by PartnerSync.info.collectAsState()
    // Переписка: значок непрочитанного наверху; пока приложение открыто, новые сообщения проверяются раз в 20 секунд.
    val context = LocalContext.current
    val chat by ChatSync.state.collectAsState()
    val unread = remember(chat) { ChatLogic.unread(chat) }
    // Последнее её непрочитанное: выезжает таблеткой из кнопки переписки.
    val latest = remember(chat) { chat.theirs.lastOrNull { !it.deleted && it.seq > chat.readUpTo } }
    var navHidden by remember { mutableStateOf(false) }
    val navScroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (consumed.y < -6f) navHidden = true else if (consumed.y > 6f) navHidden = false
                return Offset.Zero
            }
        }
    }
    LifecycleResumeEffect(owner) {
        val stop = ChatSync.watchMain(context)
        onPauseOrDispose { stop() }
    }

    AccentTheme(chrome) {
      CompositionLocalProvider(LocalNavInset provides 88.dp) {
        GlowBackground(sky = sky) {
            Scaffold(
                containerColor = Color.Transparent,
                contentColor = Palette.Text,
                topBar = {
                    if (!settingsOpen) {
                        PersonBar(
                            position = position,
                            owner = owner,
                            greeting = greeting,
                            canGreet = sync.paired && !sync.legacy,
                            greetHint = if (sync.legacy) {
                                "Чтобы капли ходили, после обновления связь нужно создать заново: Настройки, «Связь с ${owner.partner.instrumental}»."
                            } else {
                                "Капли ходят, когда телефоны связаны: Настройки, «Связь с ${owner.partner.instrumental}»."
                            },
                            onSelect = { person ->
                                // Чернила потекут сверху, от нажатого имени.
                                ink.tappedName(if (person.ordinal == 0) 0.27f else 0.68f)
                                scope.launch { pagerState.animateScrollToPage(person.ordinal) }
                            },
                            onOpenSettings = { settingsOpen = true },
                            unread = unread,
                            onOpenChat = { origin -> ChatActivity.open(context, origin) },
                            peek = latest,
                        )
                    }
                },
            ) { padding ->
                if (settingsOpen) {
                    BackHandler { settingsOpen = false }
                    PersonTheme(owner) {
                        SettingsScreen(vm, extras, onBack = { settingsOpen = false }, modifier = Modifier.padding(padding))
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .padding(padding)
                            .fillMaxSize()
                            .nestedScroll(navScroll),
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = 1f - togetherCover }
                                .inkTouchOrigin(ink),
                            beyondViewportPageCount = 1,
                            key = { Person.entries[it].id },
                        ) { page ->
                            val person = Person.entries[page]
                            // Перелив при свайпе: вторая страница проступает чернилами от пальца.
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .inkPage(
                                        pager = pagerState,
                                        page = page,
                                        origin = ink,
                                        first = Accents.of(Person.AIZAT, dark).primary,
                                        second = Accents.of(Person.NASTYA, dark).primary,
                                    ),
                            ) {
                                PersonTheme(person) {
                                    val isOwner = person == owner
                                    when (current) {
                                        Tab.Today -> TodayScreen(vm, extras, diary, person, active = pagerState.settledPage == page)
                                        Tab.Week -> WeekScreen(vm, extras, diary, person, onTogether = { together = it })
                                        Tab.Budget -> BudgetScreen(diary, person, isOwner)
                                        Tab.Deadlines -> DeadlinesScreen(diary, person, isOwner)
                                        Tab.Progress -> ProgressScreen(vm, person)
                                    }
                                }
                            }
                        }
                        together?.let { offset ->
                            TogetherWeekOverlay(vm, diary, offset, onClose = { together = null }, onCover = { togetherCover = it })
                        }
                    }
                }
            }
            // Плавающая нижняя панель: прячется, когда листаешь вниз, и возвращается, когда листаешь вверх.
            FloatingNav(
                current = current,
                settingsOpen = settingsOpen,
                hidden = navHidden,
                onSelect = { tab ->
                    current = tab
                    settingsOpen = false
                    together = null
                    navHidden = false
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            // Особый день (годовщина, день рождения, Новый год): анимация один раз за день поверх всего.
            SpecialDayHost(diary)
            // Капля привета: своя в полёте и чужая, если второй человек думал о тебе.
            GreetingDropLayer(greeting, owner)
            GreetingArrivalHost(diary, owner)
        }
      }
    }
}

/** Верхняя строка: переключатель «Айзат / Настя» и кнопка настроек. */
@Composable
private fun PersonBar(
    position: Float,
    owner: Person,
    greeting: GreetingDropState,
    canGreet: Boolean,
    onSelect: (Person) -> Unit,
    onOpenSettings: () -> Unit,
    greetHint: String = "",
    unread: Int = 0,
    onOpenChat: (Offset?) -> Unit = {},
    peek: ChatMessage? = null,
) {
    var chatCenter by remember { mutableStateOf<Offset?>(null) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 2.dp),
    ) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PersonSwitch(
            position = position,
            owner = owner,
            greeting = greeting,
            canGreet = canGreet,
            onSelect = onSelect,
            modifier = Modifier.weight(1f),
            greetHint = greetHint,
        )
        ChatButton(
            unread = unread,
            onClick = { onOpenChat(chatCenter) },
            modifier = Modifier.onGloballyPositioned { chatCenter = it.boundsInWindow().center },
        )
        GlassIconButton(icon = R.drawable.ic_settings, contentDescription = "Настройки", onClick = onOpenSettings)
    }
    PeekPill(
        message = peek,
        partner = owner.partner,
        onClick = { onOpenChat(chatCenter) },
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .padding(end = 56.dp),
    )
    }
}

/**
 * Живая кнопка переписки: когда приходит её сообщение, из кнопки выезжает таблетка с началом текста,
 * а через несколько секунд втягивается обратно. Нажатие открывает переписку.
 */
@Composable
private fun PeekPill(message: ChatMessage?, partner: Person, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var shownKey by rememberSaveable { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf(false) }
    val key = message?.key
    LaunchedEffect(key) {
        if (key == null || key == shownKey) {
            open = false
            return@LaunchedEffect
        }
        shownKey = key
        delay(350)
        open = true
        delay(6_000)
        open = false
    }
    val width by animateDpAsState(if (open) 240.dp else 44.dp, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow), label = "peek")
    val textAlpha by animateFloatAsState(if (open) 1f else 0f, tween(if (open) 260 else 120, delayMillis = if (open) 180 else 0), label = "peekText")
    if (width <= 44.5.dp && !open) return
    val accent = Accents.of(partner, LocalDark.current)
    Row(
        modifier = modifier
            .width(width)
            .height(44.dp)
            .shadow(14.dp, CircleShape, ambientColor = accent.primary, spotColor = accent.primary)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(accent.primary, accent.light)))
            .clickable(role = Role.Button) {
                open = false
                onClick()
            }
            .padding(start = 5.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) { Text(partner.shortName.take(1), style = MaterialTheme.typography.titleSmall, color = Color.White) }
        Text(
            text = message?.let { "${partner.shortName}: ${ChatLogic.shortText(it)}" }.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.graphicsLayer { alpha = textAlpha },
        )
    }
}

/** Сколько места снизу занимает плавающая панель: экраны добавляют это к нижнему отступу списков. */
val LocalNavInset = staticCompositionLocalOf { 0.dp }

/**
 * Плавающая нижняя панель: стеклянная таблетка над краем экрана, подсветка вкладки перетекает
 * с пружинкой. Когда листаешь вниз, уезжает за край.
 */
@Composable
private fun FloatingNav(current: Tab, settingsOpen: Boolean, hidden: Boolean, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val shift by animateFloatAsState(if (hidden) 1f else 0f, tween(380), label = "navShift")
    val index = Tab.entries.indexOf(current)
    val blob by animateFloatAsState(index.toFloat(), spring(dampingRatio = 0.62f, stiffness = 380f), label = "navBlob")
    val shape = RoundedCornerShape(32.dp)
    val glow = Palette.Violet
    val glowDeep = Palette.VioletDeep
    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .fillMaxWidth()
            .height(64.dp)
            .graphicsLayer { translationY = shift * (size.height + 40.dp.toPx()) }
            .shadow(18.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(Palette.Background.copy(alpha = 0.93f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape),
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            if (settingsOpen) return@Canvas
            val pad = 8.dp.toPx()
            val w = (size.width - pad * 2) / Tab.entries.size
            val cx = pad + w * (blob + 0.5f)
            // Пока подсветка едет, она чуть вытягивается, как капля.
            val stretch = 1f + kotlin.math.min(0.35f, abs(blob - index) * 0.6f)
            val bw = w * stretch
            drawRoundRect(
                brush = Brush.radialGradient(
                    listOf(glow.copy(alpha = 0.6f), glowDeep.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(cx, size.height * 0.42f),
                    radius = bw * 0.75f,
                ),
                topLeft = Offset(cx - bw / 2f, pad),
                size = Size(bw, size.height - pad * 2),
                cornerRadius = CornerRadius(24.dp.toPx()),
            )
        }
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
            Tab.entries.forEach { tab ->
                val selected = tab == current && !settingsOpen
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(24.dp))
                        .clickable(role = Role.Tab) { onSelect(tab) },
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        painterResource(tab.icon),
                        contentDescription = null,
                        tint = if (selected) Palette.NavSelected else Palette.TextFaint,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        tab.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) Palette.Lavender else Palette.TextFaint,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Кнопка переписки: облачко, а если есть непрочитанное — число в кружке. */
@Composable
private fun ChatButton(unread: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        GlassIconButton(
            icon = R.drawable.ic_chat,
            contentDescription = if (unread > 0) "Переписка, новых: $unread" else "Переписка",
            onClick = onClick,
        )
        if (unread > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 3.dp, y = (-3).dp)
                    .size(19.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Palette.Violet, Palette.VioletDeep)))
                    .border(1.5.dp, Palette.Background, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (unread > 9) "9+" else unread.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
        }
    }
}

/** Таблетка с двумя именами; подсветка едет вместе со свайпом. */
@Composable
private fun PersonSwitch(
    position: Float,
    owner: Person,
    greeting: GreetingDropState,
    canGreet: Boolean,
    onSelect: (Person) -> Unit,
    modifier: Modifier = Modifier,
    greetHint: String = "",
) {
    val accent = LocalAccent.current
    BoxWithConstraints(
        modifier = modifier
            .height(44.dp)
            .clip(CircleShape)
            .background(Palette.Chip)
            .border(1.dp, Palette.BorderStrong, CircleShape)
            .padding(4.dp),
    ) {
        val half = maxWidth / 2
        Box(
            modifier = Modifier
                .offset(x = half * position)
                .width(half)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(accent.primary, accent.deep))),
        )
        Row(modifier = Modifier.fillMaxSize()) {
            Person.entries.forEach { person ->
                val selected = abs(position - person.ordinal) < 0.5f
                val tap = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(CircleShape)
                Box(
                    // Имя второго человека: нажатие листает, долгое удержание выращивает каплю привета.
                    modifier = if (person == owner.partner) {
                        tap.greetingDropSource(
                            greeting,
                            enabled = canGreet,
                            onTap = { onSelect(person) },
                            disabledHint = greetHint,
                        )
                    } else {
                        tap.clickable(role = Role.Tab) { onSelect(person) }
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = person.shortName,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) Color.White else Palette.TextMuted,
                    )
                }
            }
        }
    }
}

/** Первый запуск: чей это телефон. От ответа зависят свои уведомления и первая страница. */
@Composable
private fun OwnerPicker(onPick: (Person) -> Unit) {
    GlowBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Pill(text = "Каждыйдневник", accent = true)
                    TwoToneTitle(first = "Чей это", second = "телефон?")
                    Text(
                        text = "Уведомления будут приходить про того, чей телефон. Дневник второго человека " +
                            "тоже будет рядом, свайпом. Поменять можно в настройках.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextMuted,
                    )
                }
            }
            items(Person.entries.size) { index ->
                val person = Person.entries[index]
                PersonTheme(person) {
                    GlassCard(
                        modifier = Modifier
                            .clip(RoundedCornerShape(22.dp))
                            .clickable { onPick(person) },
                        tone = CardTone.Highlight,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(Palette.Violet, Palette.VioletDeep))),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = person.shortName.take(1),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                )
                            }
                            Column {
                                Text(
                                    text = person.shortName,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Palette.Text,
                                )
                                Text(
                                    text = if (person == Person.AIZAT) "МВЕК, ДИС-234/21Б" else "Колледж УдГУ, торговое дело",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Palette.Text.copy(alpha = 0.75f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
