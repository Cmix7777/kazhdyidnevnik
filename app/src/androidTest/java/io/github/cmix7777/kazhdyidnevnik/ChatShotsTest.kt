package io.github.cmix7777.kazhdyidnevnik

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.cmix7777.kazhdyidnevnik.data.ChatLogic
import io.github.cmix7777.kazhdyidnevnik.data.ChatMessage
import io.github.cmix7777.kazhdyidnevnik.data.ChatPhoto
import io.github.cmix7777.kazhdyidnevnik.data.ChatVoice
import io.github.cmix7777.kazhdyidnevnik.data.ChatState
import io.github.cmix7777.kazhdyidnevnik.data.Person
import io.github.cmix7777.kazhdyidnevnik.service.ChatSync
import io.github.cmix7777.kazhdyidnevnik.ui.AppTheme
import io.github.cmix7777.kazhdyidnevnik.ui.ChatScreen
import io.github.cmix7777.kazhdyidnevnik.ui.GlowBackground
import io.github.cmix7777.kazhdyidnevnik.ui.LocalDark
import io.github.cmix7777.kazhdyidnevnik.ui.PersonTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.ZoneId

/** Кадры переписки: обе палитры, тёмная и светлая тема, «печатает», ответ, фото, пустая и без связи. */
@RunWith(AndroidJUnit4::class)
class ChatShotsTest {

    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.systemDefault()

    private fun save(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.getExternalFilesDir(null), "shots").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun at(daysAgo: Long, hour: Int, minute: Int): Long =
        LocalDate.now(zone).minusDays(daysAgo).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    /** Переписка с точки зрения [owner]: вчера пара сообщений, сегодня ответы, фото, удалённое и ещё не ушедшее. */
    private fun sample(owner: Person): ChatState {
        val me = owner.id
        val other = owner.partner.id
        val mine = listOf(
            ChatMessage(me, 1, at(1, 21, 40), "Ты уже дома?"),
            ChatMessage(me, 2, at(1, 21, 41), "Напиши, как доберёшься"),
            ChatMessage(me, 3, at(0, 8, 5), "Доброе утро! Сегодня после пар заеду за тобой, хорошо?", replyTo = "$other:2"),
            ChatMessage(me, 4, at(0, 8, 6), "", photo = ChatPhoto("p1", 1600, 1200)),
            ChatMessage(me, 5, at(0, 9, 30), "").asDeleted(),
            ChatMessage(me, 6, at(0, 12, 2), "Купить что-нибудь к ужину? Могу зайти в магазин у остановки, там как раз рядом"),
        )
        val theirs = listOf(
            ChatMessage(other, 1, at(1, 22, 15), "Да, только зашла"),
            ChatMessage(other, 2, at(1, 22, 16), "Спокойной ночи"),
            ChatMessage(other, 3, at(0, 8, 20), "Хорошо, я до 14:10", replyTo = "$me:3"),
            ChatMessage(other, 4, at(0, 8, 21), "Какое небо красивое"),
        )
        return ChatState(pair = "x", mine = mine, theirs = theirs, restored = true, uploaded = 5, theirRead = 4, readUpTo = 4)
    }

    @Composable
    private fun Screen(
        owner: Person,
        dark: Boolean,
        state: ChatState,
        status: ChatSync.Status = ChatSync.Status(loaded = true, paired = true, partnerHere = true),
        onSend: (String, String?) -> Unit = { _, _ -> },
        initialText: String = "",
    ) {
        AppTheme {
            CompositionLocalProvider(LocalDark provides dark) {
                PersonTheme(owner) {
                    GlowBackground {
                        ChatScreen(
                            owner = owner,
                            state = state,
                            status = status,
                            onBack = {},
                            onSend = onSend,
                            onPickPhoto = {},
                            onDelete = {},
                            onTyping = {},
                            onOpenPhoto = {},
                            onRetry = {},
                            photoContent = { _, modifier ->
                                Box(modifier.background(Brush.linearGradient(listOf(Color(0xFF2B5B8C), Color(0xFFE8A56A)))))
                            },
                            zone = zone,
                            initialText = initialText,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun aizatDark() {
        compose.setContent { Screen(Person.AIZAT, dark = true, state = sample(Person.AIZAT)) }
        compose.waitForIdle()
        save("chat_1_aizat_dark")
    }

    @Test
    fun nastyaDarkTyping() {
        compose.setContent {
            Screen(
                Person.NASTYA,
                dark = true,
                state = sample(Person.NASTYA),
                status = ChatSync.Status(loaded = true, paired = true, partnerHere = true, partnerTyping = true),
            )
        }
        compose.mainClock.advanceTimeBy(400)
        save("chat_2_nastya_dark_typing")
    }

    @Test
    fun aizatLightWithText() {
        compose.setContent { Screen(Person.AIZAT, dark = false, state = sample(Person.AIZAT), initialText = "Сейчас выхожу") }
        compose.waitForIdle()
        save("chat_3_aizat_light")
    }

    @Test
    fun nastyaLight() {
        compose.setContent { Screen(Person.NASTYA, dark = false, state = sample(Person.NASTYA)) }
        compose.waitForIdle()
        save("chat_4_nastya_light")
    }

    @Test
    fun empty() {
        compose.setContent { Screen(Person.AIZAT, dark = true, state = ChatState()) }
        compose.waitForIdle()
        save("chat_5_empty")
    }

    @Test
    fun notPaired() {
        compose.setContent {
            Screen(Person.NASTYA, dark = true, state = ChatState(), status = ChatSync.Status(loaded = true, paired = false))
        }
        compose.waitForIdle()
        save("chat_6_not_paired")
    }

    @Test
    fun offlineMessageIsMarked() {
        val state = ChatLogic.append(sample(Person.AIZAT), Person.AIZAT, "Это без сети", System.currentTimeMillis())
        compose.setContent {
            Screen(
                Person.AIZAT,
                dark = true,
                state = state,
                status = ChatSync.Status(loaded = true, paired = true, problem = "Нет связи"),
            )
        }
        compose.waitForIdle()
        save("chat_7_offline")
    }

    @Test
    fun typingAndReplyingSendsTheRightThing() {
        val sent = mutableListOf<Pair<String, String?>>()
        compose.setContent { Screen(Person.AIZAT, dark = true, state = sample(Person.AIZAT), onSend = { text, reply -> sent += text to reply }) }
        compose.onNodeWithTag("chat-input").performTextInput("  Привет  ")
        compose.onNodeWithTag("chat-send").performClick()
        compose.waitForIdle()
        assertEquals(listOf("Привет" to null), sent)

        // Долгое нажатие на сообщение → «Ответить» → ответ уходит со ссылкой на него.
        compose.onNodeWithText("Какое небо красивое").performTouchInput { longClick() }
        compose.onNodeWithText("Ответить").performClick()
        compose.waitForIdle()
        save("chat_8_replying")
        compose.onNodeWithTag("chat-input").performTextInput("Очень")
        compose.onNodeWithTag("chat-send").performClick()
        compose.waitForIdle()
        assertEquals("Очень" to "${Person.NASTYA.id}:4", sent.last())
    }

    /** Новое в переписке: секрет под чернилами, крупные смайлики, записка, голосовое и реакции. */
    private fun rich(owner: Person): ChatState {
        val me = owner.id
        val other = owner.partner.id
        val mine = listOf(
            ChatMessage(me, 1, at(0, 9, 0), "Это секрет", secret = true),
            ChatMessage(me, 2, at(0, 9, 1), "\u2764\uFE0F"),
            ChatMessage(me, 3, at(0, 9, 2), "Скучаю по тебе", hand = true),
            ChatMessage(me, 4, at(0, 9, 3), "", voice = ChatVoice("v-test", 12_000, 1, "135798642135798642135798642135798642135798642135")),
        )
        val theirs = listOf(
            ChatMessage(other, 1, at(0, 9, 10), "Билеты в кино на субботу", secret = true),
            ChatMessage(other, 2, at(0, 9, 11), "\uD83D\uDE02\uD83D\uDD25"),
            ChatMessage(other, 3, at(0, 9, 12), "Ты лучший"),
        )
        return ChatState(
            pair = "x", mine = mine, theirs = theirs, restored = true, uploaded = 4, theirRead = 3, readUpTo = 3,
            myReactions = mapOf("$other:3" to "\u2764\uFE0F"),
            theirReactions = mapOf("$me:2" to "\uD83D\uDE02"),
        )
    }

    @Test
    fun richMessages() {
        compose.setContent { Screen(Person.AIZAT, dark = true, state = rich(Person.AIZAT)) }
        compose.mainClock.advanceTimeBy(600)
        save("chat_9_rich")
    }

    @Test
    fun heartsEffect() {
        compose.setContent { Screen(Person.AIZAT, dark = true, state = sample(Person.AIZAT), initialText = "Люблю тебя") }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("chat-send").performClick()
        compose.mainClock.advanceTimeBy(1600)
        save("chat_10_hearts")
    }

    @Test
    fun sunriseEffect() {
        compose.setContent { Screen(Person.NASTYA, dark = true, state = sample(Person.NASTYA), initialText = "Доброе утро") }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("chat-send").performClick()
        compose.mainClock.advanceTimeBy(2600)
        save("chat_11_sunrise")
    }
}
