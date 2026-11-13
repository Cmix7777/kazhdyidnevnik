package io.github.cmix7777.kazhdyidnevnik.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.cmix7777.kazhdyidnevnik.R
import io.github.cmix7777.kazhdyidnevnik.data.ChatFile
import io.github.cmix7777.kazhdyidnevnik.data.ChatMessage
import io.github.cmix7777.kazhdyidnevnik.data.ChatPhoto
import io.github.cmix7777.kazhdyidnevnik.data.ChatState
import io.github.cmix7777.kazhdyidnevnik.data.Person
import io.github.cmix7777.kazhdyidnevnik.data.ScheduledMessage
import io.github.cmix7777.kazhdyidnevnik.data.SharedList
import io.github.cmix7777.kazhdyidnevnik.data.SharedListStore
import io.github.cmix7777.kazhdyidnevnik.service.ChatFiles
import io.github.cmix7777.kazhdyidnevnik.service.PartnerSync
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import io.github.cmix7777.kazhdyidnevnik.data.ChatLogic
import io.github.cmix7777.kazhdyidnevnik.service.VoiceText
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

// ---------- сообщения «в будущее» ----------

/** «сегодня в 21:00», «завтра в 9:00», «14 ноября в 9:00». */
internal fun laterText(at: Long, zone: ZoneId, today: LocalDate = LocalDate.now(zone)): String {
    val time = Instant.ofEpochMilli(at).atZone(zone).toLocalDateTime()
    val clock = "%d:%02d".format(time.hour, time.minute)
    val day = when (time.toLocalDate()) {
        today -> "сегодня"
        today.plusDays(1) -> "завтра"
        else -> formatDayMonth(time.toLocalDate())
    }
    return "$day в $clock"
}

private fun formatDayMonth(date: LocalDate): String {
    val months = listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")
    return "${date.dayOfMonth} ${months[date.monthValue - 1]}"
}

/** Тонкая полоска над полем ввода: сколько сообщений ждёт своего времени. */
@Composable
internal fun ScheduledStrip(count: Int, next: Long, zone: ZoneId, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 10.dp, top = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.Chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("scheduled-strip"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(painterResource(R.drawable.ic_schedule), contentDescription = null, tint = Palette.Lavender, modifier = Modifier.size(16.dp))
        Text(
            text = (if (count == 1) "Запланировано сообщение" else "Запланировано сообщений: $count") + " · ближайшее ${laterText(next, zone)}",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Когда отправить: быстрые варианты или свои дата и время. */
@Composable
internal fun SendLaterDialog(zone: ZoneId, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    var pickDate by remember { mutableStateOf(false) }
    var date by remember { mutableStateOf<LocalDate?>(null) }
    val now = LocalDateTime.now(zone)
    fun millis(at: LocalDateTime) = at.atZone(zone).toInstant().toEpochMilli()
    val options = buildList {
        add("Через час" to millis(now.plusHours(1)))
        if (now.toLocalTime().isBefore(LocalTime.of(20, 30))) add("Сегодня в 21:00" to millis(now.toLocalDate().atTime(21, 0)))
        add("Завтра в 9:00" to millis(now.toLocalDate().plusDays(1).atTime(9, 0)))
    }
    when {
        date != null -> TimeDialog(
            title = "Во сколько",
            initial = LocalTime.of(9, 0),
            onDismiss = { date = null },
            onConfirm = { time ->
                val at = date!!.atTime(time)
                if (at.isAfter(now.plusMinutes(1))) onPick(millis(at)) else onPick(millis(now.plusMinutes(1)))
            },
        )
        pickDate -> DateDialog(
            initial = now.toLocalDate().plusDays(1),
            onDismiss = { pickDate = false },
            onConfirm = {
                pickDate = false
                date = it
            },
        )
        else -> Dialog(onDismissRequest = onDismiss) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(26.dp))
                    .background(Palette.Background)
                    .border(1.dp, Palette.BorderStrong, RoundedCornerShape(26.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Отправить позже", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                Text(
                    "Сообщение полежит на твоём телефоне и уйдёт само. Пока время не пришло, его видишь только ты.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextMuted,
                )
                options.forEach { (title, at) ->
                    GhostButton(title, onClick = { onPick(at) }, modifier = Modifier.fillMaxWidth())
                }
                GhostButton("Выбрать день и время", onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** Что ждёт отправки: время, текст и отмена. */
@Composable
internal fun ScheduledDialog(items: List<ScheduledMessage>, zone: ZoneId, onCancelItem: (String) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(26.dp))
                .background(Palette.Background)
                .border(1.dp, Palette.BorderStrong, RoundedCornerShape(26.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Запланированные сообщения", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
            if (items.isEmpty()) Text("Ничего не ждёт отправки.", style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items, key = { it.id }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Palette.Chip)
                            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(laterText(item.at, zone), style = MaterialTheme.typography.labelMedium, color = Palette.Lavender)
                            Text(item.text, style = MaterialTheme.typography.bodyMedium, color = Palette.Text, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(
                            painterResource(R.drawable.ic_delete),
                            contentDescription = "Не отправлять",
                            tint = Palette.TextMuted,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable { onCancelItem(item.id) }
                                .padding(10.dp),
                        )
                    }
                }
            }
        }
    }
}

// ---------- фото, кружочки и файлы ----------

/** Все фото, кружочки и файлы переписки в одном месте, новые сверху. */
@Composable
internal fun MediaGalleryDialog(
    state: ChatState,
    owner: Person,
    photoContent: @Composable (ChatPhoto, Modifier) -> Unit,
    onOpenPhoto: (ChatPhoto) -> Unit,
    onOpenFile: (ChatFile) -> Unit,
    onEnsureFiles: (ChatMessage, Boolean) -> Unit,
    onClose: () -> Unit,
) {
    // Одно сообщение может оказаться в списке дважды (после переустановки или смены владельца телефона):
    // ключи плиток обязаны быть разными, иначе список падает.
    val all = remember(state) {
        (state.mine + state.theirs).filter { !it.deleted }.distinctBy { it.key to it.at }.sortedByDescending { it.at }
    }
    val photos = remember(all) { all.filter { it.photo != null } }
    val circles = remember(all) { all.filter { it.circle != null } }
    val files = remember(all) { all.filter { it.file != null } }
    var tab by remember { mutableStateOf(0) }
    val accent = Accents.of(owner, LocalDark.current).primary
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Palette.Background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassIconButton(icon = R.drawable.ic_arrow_back, contentDescription = "Назад", onClick = onClose)
                Text("Фото, кружочки и файлы", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Фото ${photos.size}", "Кружочки ${circles.size}", "Файлы ${files.size}").forEachIndexed { index, title ->
                    SelectPill(text = title, selected = tab == index, onClick = { tab = index })
                }
            }
            val shown = when (tab) {
                0 -> photos
                1 -> circles
                else -> files
            }
            if (shown.isEmpty()) {
                Text("Здесь пока пусто.", style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
            }
            when (tab) {
                0 -> LazyVerticalGrid(columns = GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(photos, key = { "${it.key}-${it.at}" }) { message ->
                        val photo = message.photo!!
                        LaunchedEffect(message.key) { onEnsureFiles(message, message.from == owner.id) }
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Palette.Chip)
                                .clickable { onOpenPhoto(photo) },
                        ) { photoContent(photo, Modifier.fillMaxSize()) }
                    }
                }
                1 -> LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(circles, key = { "${it.key}-${it.at}" }) { message ->
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                            CircleBubble(
                                circle = message.circle!!,
                                accent = accent,
                                onReady = { onEnsureFiles(message, message.from == owner.id) },
                                modifier = Modifier.size(150.dp),
                            )
                        }
                    }
                }
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(files, key = { "${it.key}-${it.at}" }) { message ->
                        val file = message.file!!
                        LaunchedEffect(message.key) { onEnsureFiles(message, message.from == owner.id) }
                        val context = LocalContext.current
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Palette.Chip)
                                .clickable { if (ChatFiles.exists(context, file.id)) onOpenFile(file) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(painterResource(R.drawable.ic_file), contentDescription = null, tint = Palette.Lavender, modifier = Modifier.size(24.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(file.name, style = MaterialTheme.typography.bodyMedium, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(fileSize(file.size), style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- общий список ----------

/** Общий список: дела или покупки, видно обоим. Ненавязчиво живёт в меню переписки. */
@Composable
internal fun SharedListDialog(owner: Person, onClose: () -> Unit) {
    val context = LocalContext.current
    val items by SharedListStore.state.collectAsState()
    LaunchedEffect(Unit) {
        SharedListStore.init(context.filesDir)
        PartnerSync.syncNow(context, force = true)
    }
    var input by remember { mutableStateOf("") }
    fun change(edit: (List<io.github.cmix7777.kazhdyidnevnik.data.ListItem>) -> List<io.github.cmix7777.kazhdyidnevnik.data.ListItem>) {
        SharedListStore.update(context.filesDir, edit)
        PartnerSync.syncSoon(context)
    }
    fun add() {
        val text = input.trim()
        if (text.isEmpty()) return
        change { SharedList.add(it, ChatFiles.newId(), text, owner, System.currentTimeMillis()) }
        input = ""
    }
    val shown = SharedList.visible(items)
    Dialog(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(26.dp))
                .background(Palette.Background)
                .border(1.dp, Palette.BorderStrong, RoundedCornerShape(26.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Общий список", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
            Text(
                "Дела и покупки на двоих: видно обоим, отметить может любой. Это проба — если не пригодится, уберём.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextMuted,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Palette.Chip)
                    .padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = input,
                    onValueChange = { input = it.take(SharedList.MAX_TEXT) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Palette.Text),
                    cursorBrush = SolidColor(Palette.Violet),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 12.dp)
                        .testTag("list-input"),
                    decorationBox = { inner ->
                        if (input.isEmpty()) Text("Что добавить", style = MaterialTheme.typography.bodyLarge, color = Palette.TextFaint)
                        inner()
                    },
                )
                Icon(
                    painterResource(R.drawable.ic_add),
                    contentDescription = "Добавить",
                    tint = Palette.Lavender,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable { add() }
                        .padding(10.dp)
                        .testTag("list-add"),
                )
            }
            if (shown.isEmpty()) Text("Пока пусто.", style = MaterialTheme.typography.bodyMedium, color = Palette.TextFaint)
            LazyColumn(modifier = Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(shown, key = { it.id }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { change { SharedList.toggle(it, item.id, System.currentTimeMillis()) } }
                            .padding(start = 6.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(if (item.done) Palette.Violet else Color.Transparent)
                                .border(1.5.dp, if (item.done) Palette.Violet else Palette.BorderStrong, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (item.done) Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                        Text(
                            text = item.text,
                            style = MaterialTheme.typography.bodyLarge.copy(textDecoration = if (item.done) TextDecoration.LineThrough else null),
                            color = if (item.done) Palette.TextFaint else Palette.Text,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = Person.entries.firstOrNull { it.id == item.by }?.shortName?.take(1).orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Palette.TextFaint,
                        )
                        Icon(
                            painterResource(R.drawable.ic_close),
                            contentDescription = "Убрать",
                            tint = Palette.TextFaint,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable { change { SharedList.remove(it, item.id, System.currentTimeMillis()) } }
                                .padding(9.dp),
                        )
                    }
                }
            }
            if (shown.any { it.done }) {
                GhostButton("Убрать сделанное", onClick = { change { SharedList.clearDone(it, System.currentTimeMillis()) } }, small = true)
            }
        }
    }
}

// ---------- закреплённые и поиск ----------

/** Полоска под шапкой переписки: закреплённое сообщение. Нажал — переписка листается к нему, дальше к следующему. */
@Composable
internal fun PinnedBar(message: ChatMessage, position: Int, count: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.Chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
            .testTag("pinned-bar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(painterResource(R.drawable.ic_pin), contentDescription = null, tint = Palette.Lavender, modifier = Modifier.size(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (count > 1) "Закреплено · ${position + 1} из $count" else "Закреплено",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.Lavender,
            )
            Text(
                text = ChatLogic.shortText(message).ifBlank { "Сообщение" },
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Кусок текста вокруг первого совпадения, совпадения подсвечены. */
internal fun searchSnippet(text: String, query: String, highlight: Color, width: Int = 90): AnnotatedString {
    val fold = { t: String -> t.lowercase().replace('ё', 'е') }
    val words = fold(query).split(Regex("\\s+")).filter { it.isNotEmpty() }
    val flat = text.replace('\n', ' ')
    val folded = fold(flat)
    val first = words.mapNotNull { w -> folded.indexOf(w).takeIf { it >= 0 } }.minOrNull() ?: 0
    val start = (first - width / 3).coerceIn(0, flat.length)
    val end = (start + width).coerceAtMost(flat.length)
    val piece = (if (start > 0) "…" else "") + flat.substring(start, end) + (if (end < flat.length) "…" else "")
    val shift = if (start > 0) 1 - start else -start
    return buildAnnotatedString {
        append(piece)
        for (w in words) {
            var from = folded.indexOf(w, start)
            while (from in start until end) {
                val a = from + shift
                val b = (minOf(from + w.length, end)) + shift
                if (a >= 0 && b <= piece.length && a < b) addStyle(SpanStyle(background = highlight, fontWeight = FontWeight.SemiBold), a, b)
                from = folded.indexOf(w, from + w.length)
            }
        }
    }
}

/** Поиск по переписке: пишешь слово — видно все сообщения с ним; нажал — переписка листается к сообщению. */
@Composable
internal fun ChatSearchDialog(
    state: ChatState,
    owner: Person,
    zone: ZoneId,
    onPick: (ChatLogic.SearchHit) -> Unit,
    onClose: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val voiceStates by VoiceText.state.collectAsState()
    val voice = remember(voiceStates) { voiceStates.mapNotNull { (id, s) -> (s as? VoiceText.State.Done)?.let { id to it.text } }.toMap() }
    val hits = remember(state, query, voice) { if (query.trim().length < 2) emptyList() else ChatLogic.search(state, query, voice) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val today = LocalDate.now(zone)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Palette.Background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassIconButton(icon = R.drawable.ic_arrow_back, contentDescription = "Назад", onClick = onClose)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Palette.Chip)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = Palette.TextFaint, modifier = Modifier.size(18.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it.take(100) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Palette.Text),
                        cursorBrush = SolidColor(Palette.Violet),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 12.dp)
                            .focusRequester(focus)
                            .testTag("search-input"),
                        decorationBox = { inner ->
                            if (query.isEmpty()) Text("Слово или фраза", style = MaterialTheme.typography.bodyLarge, color = Palette.TextFaint)
                            inner()
                        },
                    )
                }
            }
            when {
                query.trim().length < 2 -> Text(
                    "Ищет по сообщениям, подписям к фото, именам файлов и расшифрованным голосовым.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextMuted,
                )
                hits.isEmpty() -> Text("Ничего не нашлось.", style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
                else -> Text(
                    "Нашлось: ${hits.size}" + if (hits.size >= 300) " (показаны последние)" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.TextMuted,
                )
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(hits, key = { (if (it.earlier) "old-" else "") + it.message.key + "-" + it.message.at }) { hit ->
                    val author = if (hit.message.from == owner.id) "Ты" else owner.partner.shortName
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Palette.Chip)
                            .clickable { onPick(hit) }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .testTag("search-hit"),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(author, style = MaterialTheme.typography.labelMedium, color = Palette.Lavender)
                            Text(
                                laterText(hit.message.at, zone, today) + if (hit.earlier) " · прежняя переписка" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = Palette.TextFaint,
                            )
                        }
                        Text(
                            searchSnippet(hit.text, query, Palette.Violet.copy(alpha = 0.35f)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.Text,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
