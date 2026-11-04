package io.github.cmix7777.kazhdyidnevnik.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.cmix7777.kazhdyidnevnik.data.Person
import io.github.cmix7777.kazhdyidnevnik.formatStamp
import io.github.cmix7777.kazhdyidnevnik.notify.PartnerHeard
import io.github.cmix7777.kazhdyidnevnik.notify.AppSettings
import io.github.cmix7777.kazhdyidnevnik.service.ChatPush
import io.github.cmix7777.kazhdyidnevnik.service.ChatSync
import io.github.cmix7777.kazhdyidnevnik.service.PartnerSync
import kotlinx.coroutines.launch

/**
 * Связь с телефоном второго человека (Настройки). Один раз создаётся код и вставляется на втором
 * телефоне, дальше дела обмениваются сами через бесплатное облако.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PartnerLinkCard(owner: Person) {
    val context = LocalContext.current
    val partner = owner.partner
    val info by PartnerSync.info.collectAsState()
    var note by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { PartnerSync.load(context) }

    GlassCard(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!info.paired) {
            if (info.legacy) {
                Text(
                    text = "После обновления связь нужно создать заново: дела теперь шифруются на телефоне, и " +
                        "старый код для этого не годится. Один создаёт новый код, второй вставляет его здесь же.",
                    style = MaterialTheme.typography.titleSmall,
                    color = Palette.Text,
                )
            }
            Text(
                text = "Чтобы дела ${partner.genitive} появлялись у тебя сами, свяжите телефоны один раз: один " +
                    "создаёт код и отправляет его другому, второй вставляет код здесь же, в настройках. " +
                    "Дальше дела обновляются сами: при открытии приложения и примерно раз в три часа. " +
                    "Передаются только дела и смены, без заметок, бюджета и дат. Они шифруются на телефоне, " +
                    "в облаке лежит нечитаемый набор знаков, а открыть его можно только этим кодом.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextMuted,
            )
        } else {
            Text(
                text = "Телефоны связаны: твои дела уходят ${partner.instrumental} сами, а дела " +
                    "${partner.genitive} приходят сюда. По этой же связи работает переписка.",
                style = MaterialTheme.typography.titleSmall,
                color = Palette.Text,
            )
            val status = when {
                info.running -> "Обновляю…"
                info.lastSyncMillis > 0 -> "Последний обмен: ${formatStamp(info.lastSyncMillis)}."
                else -> "Обмена ещё не было."
            }
            Text(text = status, style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint)
            // Что слышно от второго человека: так видно, доходят ли его дела, а не только что облако ответило.
            if (!info.running) {
                PartnerSync.partnerNote(info, partner, ::formatStamp)?.let { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (info.partnerState == PartnerHeard.OK) Palette.TextMuted else Palette.DangerText,
                    )
                }
            }
            info.problem?.let {
                Text(
                    text = "Последняя попытка не удалась: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.DangerText,
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!info.paired) {
                GhostButton(
                    text = "Создать код и отправить",
                    onClick = {
                        val message = PartnerSync.codeMessage(context)
                        if (message != null) {
                            shareText(context, message, "Отправить код связи")
                            note = "Отправь код ${partner.instrumental}. Когда ${partner.shortName} вставит его у себя, дела начнут ходить сами."
                        }
                    },
                )
                GhostButton(
                    text = "Вставить код",
                    onClick = {
                        note = when (PartnerSync.join(context, clipboardText(context))) {
                            PartnerSync.JoinResult.Done -> "Телефоны связаны, обмениваюсь делами."
                            PartnerSync.JoinResult.NoCode -> "В буфере нет кода. Скопируй всё сообщение с кодом целиком."
                            PartnerSync.JoinResult.OldCode ->
                                "Это код старого вида. Попроси создать новый кнопкой «Создать код и отправить» в последней версии."
                            PartnerSync.JoinResult.AlreadyJoined -> "Этот код уже подключён."
                        }
                    },
                )
            } else {
                GhostButton(
                    text = "Обновить сейчас",
                    onClick = { PartnerSync.syncNow(context) },
                    enabled = !info.running,
                )
                GhostButton(
                    text = "Отправить код ещё раз",
                    onClick = {
                        PartnerSync.codeMessage(context)?.let { shareText(context, it, "Отправить код связи") }
                    },
                )
                GhostButton(
                    text = "Вставить другой код",
                    onClick = {
                        note = when (PartnerSync.join(context, clipboardText(context))) {
                            PartnerSync.JoinResult.Done -> "Подключился по новому коду."
                            PartnerSync.JoinResult.NoCode -> "В буфере нет кода. Скопируй всё сообщение с кодом целиком."
                            PartnerSync.JoinResult.OldCode ->
                                "Это код старого вида. Попроси создать новый кнопкой «Создать код и отправить» в последней версии."
                            PartnerSync.JoinResult.AlreadyJoined -> "Этот код уже подключён."
                        }
                    },
                )
                GhostButton(
                    text = "Отвязать",
                    onClick = {
                        PartnerSync.unpair(context)
                        note = "Телефоны больше не связаны. Уже полученные дела остались."
                    },
                )
            }
        }
        note?.let { Text(text = it, style = MaterialTheme.typography.bodySmall, color = Palette.Lavender) }
    }
}

/**
 * Мгновенные уведомления о сообщениях (Настройки → Сообщения). Без них новые сообщения при закрытом
 * приложении проверяются примерно раз в 15 минут; с бесплатной пересылкой на Google Apps Script приходят сразу.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatPushCard(owner: Person) {
    val context = LocalContext.current
    val partner = owner.partner
    val scope = rememberCoroutineScope()
    val settings = remember { AppSettings(context) }
    val info by PartnerSync.info.collectAsState()
    val chat by ChatSync.state.collectAsState()
    var hook by remember { mutableStateOf(settings.pushHook) }
    var note by remember { mutableStateOf<String?>(null) }
    var checked by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }

    GlassCard(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Переписка открывается кнопкой с облачком наверху, рядом с настройками. Сообщения шифруются кодом " +
                "связи на телефоне и лежат в том же бесплатном облаке, что и дела.",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.TextMuted,
        )
        val problem = remember(checked, hook) { settings.pushProblem }
        val status = when {
            !info.paired -> "Сначала свяжите телефоны (раздел выше)."
            hook == null -> "Когда приложение закрыто, новые сообщения проверяются примерно раз в 15 минут. Чтобы " +
                "уведомления приходили сразу, один раз настрой бесплатную пересылку (кнопка «Как настроить») и вставь " +
                "сюда ссылку на неё. Достаточно на одном телефоне, второй подхватит сам."
            problem.isNotEmpty() -> "Мгновенные уведомления пока не работают: $problem"
            settings.pushToken == null -> "Мгновенные уведомления включаются…"
            chat.theirPush == null -> "Мгновенные уведомления включены. У ${partner.genitive} они включатся сами, " +
                "когда ${partner.shortName} откроет новую версию приложения."
            else -> "Мгновенные уведомления включены на обоих телефонах."
        }
        Text(
            text = status,
            style = MaterialTheme.typography.bodySmall,
            color = if (problem.isNotEmpty() && hook != null) Palette.DangerText else Palette.TextFaint,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton(
                text = "Как настроить",
                onClick = {
                    val open = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(PUSH_GUIDE))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(open) }
                },
            )
            GhostButton(
                text = if (hook == null) "Вставить ссылку" else "Вставить другую ссылку",
                enabled = info.paired && !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        note = ChatPush.configure(context, clipboardText(context))
                        hook = settings.pushHook
                        busy = false
                    }
                },
            )
            if (problem.isNotEmpty() && hook != null) {
                GhostButton(
                    text = "Проверить ещё раз",
                    enabled = !busy,
                    onClick = {
                        busy = true
                        ChatPush.retryNow(context)
                        scope.launch {
                            kotlinx.coroutines.delay(4_000)
                            checked++
                            busy = false
                            note = if (settings.pushProblem.isEmpty()) "Получилось: уведомления включены." else "Пока не вышло. Если включён VPN, выключи его на минуту."
                        }
                    },
                )
            }
        }
        note?.let { Text(text = it, style = MaterialTheme.typography.bodySmall, color = Palette.Lavender) }
    }
}

/** Инструкция по мгновенным уведомлениям в README на GitHub. */
private const val PUSH_GUIDE = "https://github.com/Cmix7777/kazhdyidnevnik/blob/main/push/README.md"
