package io.github.cmix7777.kazhdyidnevnik.ui

import android.Manifest
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.cmix7777.kazhdyidnevnik.R
import io.github.cmix7777.kazhdyidnevnik.data.Person
import kotlinx.coroutines.launch
import kotlin.math.abs

private enum class Tab(val title: String, @param:DrawableRes val icon: Int) {
    Today("Сегодня", R.drawable.ic_nav_today),
    Week("Неделя", R.drawable.ic_nav_week),
    Progress("Прогресс", R.drawable.ic_nav_progress),
    Deadlines("Дедлайны", R.drawable.ic_nav_deadlines),
}

@Composable
fun AppRoot(vm: ScheduleViewModel = viewModel(), extras: ExtrasViewModel = viewModel()) {
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

    val owner = vm.owner
    if (owner == null) {
        AccentTheme(Accents.blend(Accents.Aizat, Accents.Nastya, 0.5f)) {
            OwnerPicker(onPick = { vm.chooseOwner(it) })
        }
    } else {
        MainPager(vm, extras, owner)
    }
}

/** Две страницы: Айзат слева, Настя справа. Открывается страница владельца телефона. */
@Composable
private fun MainPager(vm: ScheduleViewModel, extras: ExtrasViewModel, owner: Person) {
    var current by rememberSaveable { mutableStateOf(Tab.Today) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val pagerState = rememberPagerState(initialPage = owner.ordinal) { Person.entries.size }
    val scope = rememberCoroutineScope()
    val position by remember {
        derivedStateOf { (pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceIn(0f, 1f) }
    }
    // Рамка (фон, верх и низ) плавно перетекает из палитры Айзата в палитру Насти при свайпе.
    val chrome = if (settingsOpen) Accents.of(owner) else Accents.blend(Accents.Aizat, Accents.Nastya, position)

    AccentTheme(chrome) {
        GlowBackground {
            Scaffold(
                containerColor = Color.Transparent,
                contentColor = Palette.Text,
                topBar = {
                    if (!settingsOpen) {
                        PersonBar(
                            position = position,
                            onSelect = { person -> scope.launch { pagerState.animateScrollToPage(person.ordinal) } },
                            onOpenSettings = { settingsOpen = true },
                        )
                    }
                },
                bottomBar = {
                    NavigationBar(
                        modifier = Modifier.drawBehind {
                            drawLine(
                                color = Color(0x17FFFFFF),
                                start = Offset(0f, 0f),
                                end = Offset(size.width, 0f),
                                strokeWidth = 1.dp.toPx(),
                            )
                        },
                        containerColor = Palette.Background.copy(alpha = 0.92f),
                        tonalElevation = 0.dp,
                    ) {
                        Tab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = current == tab && !settingsOpen,
                                onClick = {
                                    current = tab
                                    settingsOpen = false
                                },
                                icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                                label = { Text(tab.title, style = MaterialTheme.typography.labelMedium) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color.White,
                                    selectedTextColor = Palette.Lavender,
                                    indicatorColor = Palette.Violet.copy(alpha = 0.3f),
                                    unselectedIconColor = Palette.TextFaint,
                                    unselectedTextColor = Palette.TextFaint,
                                ),
                            )
                        }
                    }
                },
            ) { padding ->
                if (settingsOpen) {
                    BackHandler { settingsOpen = false }
                    PersonTheme(owner) {
                        SettingsScreen(vm, extras, onBack = { settingsOpen = false }, modifier = Modifier.padding(padding))
                    }
                } else {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .padding(padding)
                            .fillMaxSize(),
                        beyondViewportPageCount = 1,
                        key = { Person.entries[it].id },
                    ) { page ->
                        val person = Person.entries[page]
                        PersonTheme(person) {
                            when (current) {
                                Tab.Today -> TodayScreen(vm, extras, person)
                                Tab.Week -> WeekScreen(vm, extras, person)
                                Tab.Progress -> ProgressScreen(vm, person)
                                Tab.Deadlines -> DeadlinesScreen(person)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Верхняя строка: переключатель «Айзат / Настя» и кнопка настроек. */
@Composable
private fun PersonBar(position: Float, onSelect: (Person) -> Unit, onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PersonSwitch(position = position, onSelect = onSelect, modifier = Modifier.weight(1f))
        GlassIconButton(icon = R.drawable.ic_settings, contentDescription = "Настройки", onClick = onOpenSettings)
    }
}

/** Таблетка с двумя именами; подсветка едет вместе со свайпом. */
@Composable
private fun PersonSwitch(position: Float, onSelect: (Person) -> Unit, modifier: Modifier = Modifier) {
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
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable(role = Role.Tab) { onSelect(person) },
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
