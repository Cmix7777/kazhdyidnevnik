package io.github.cmix7777.kazhdyidnevnik

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.cmix7777.kazhdyidnevnik.data.ChatLogic
import io.github.cmix7777.kazhdyidnevnik.data.ChatMessage
import io.github.cmix7777.kazhdyidnevnik.data.ChatPhoto
import io.github.cmix7777.kazhdyidnevnik.data.ChatState
import io.github.cmix7777.kazhdyidnevnik.data.Person
import io.github.cmix7777.kazhdyidnevnik.service.ChatSync
import io.github.cmix7777.kazhdyidnevnik.ui.AppTheme
import io.github.cmix7777.kazhdyidnevnik.ui.ChatScreen
import io.github.cmix7777.kazhdyidnevnik.ui.GlowBackground
import io.github.cmix7777.kazhdyidnevnik.ui.LocalDark
import io.github.cmix7777.kazhdyidnevnik.ui.MediaGalleryDialog
import io.github.cmix7777.kazhdyidnevnik.ui.PersonTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneId

/** Закреплённые сообщения, поиск по переписке и галерея с повторяющимися сообщениями (на ней приложение падало). */
@RunWith(AndroidJUnit4::class)
class ChatPinsSearchUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.of("Europe/Samara")
    private val start = 1_763_000_000_000L

    /** Длинная переписка: старые сообщения далеко за краем экрана. */
    private fun longChat(): ChatState {
        var state = ChatState(restored = true)
        for (i in 0 until 60) {
            val text = when (i) {
                2 -> "код от подъезда 4512"
                5 -> "купить капусту и морковку"
                else -> "сообщение номер $i"
            }
            state = ChatLogic.append(state, Person.AIZAT, text, start + i * 60_000L)
        }
        return state
    }

    private fun show(state: ChatState, onPin: (ChatMessage, Boolean) -> Unit = { _, _ -> }) {
        compose.setContent {
            AppTheme {
                CompositionLocalProvider(LocalDark provides true) {
                    PersonTheme(Person.AIZAT) {
                        GlowBackground {
                            ChatScreen(
                                owner = Person.AIZAT,
                                state = state,
                                status = ChatSync.Status(loaded = true, paired = true, partnerHere = true),
                                onBack = {},
                                onSend = { _, _ -> },
                                onPickPhoto = {},
                                onDelete = {},
                                onTyping = {},
                                onOpenPhoto = {},
                                onRetry = {},
                                photoContent = { _, modifier -> Box(modifier.background(Color.Gray)) },
                                zone = zone,
                                animate = false,
                                onPin = onPin,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun waitShown(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun pinnedMessageIsOnTopAndTapScrollsToIt() {
        var state = longChat()
        state = ChatLogic.pin(state, state.mine[2], true, start + 100 * 60_000L)
        show(state)
        compose.onNodeWithTag("pinned-bar").assertExists()
        // Само сообщение далеко вверху: на экране пока только полоска.
        assertEquals(1, compose.onAllNodes(hasText("код от подъезда 4512")).fetchSemanticsNodes().size)
        compose.onNodeWithTag("pinned-bar").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("код от подъезда 4512")).fetchSemanticsNodes().size >= 2 }
    }

    @Test
    fun longPressPinsMessage() {
        var pinned: Pair<String, Boolean>? = null
        show(longChat()) { message, on -> pinned = message.text to on }
        waitShown("сообщение номер 59")
        compose.onNodeWithText("сообщение номер 59", substring = true).performTouchInput { longClick() }
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("menu-pin")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("menu-pin").performClick()
        compose.waitUntil(5_000) { pinned != null }
        assertEquals("сообщение номер 59" to true, pinned)
    }

    @Test
    fun searchFindsOldMessageAndJumpsToIt() {
        show(longChat())
        compose.onNode(hasContentDescription("Ещё")).performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("menu-search")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("menu-search").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("search-input")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("search-input").performTextInput("капуст")
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("search-hit")).fetchSemanticsNodes().size == 1 }
        compose.onAllNodes(hasTestTag("search-hit")).onFirst().performClick()
        // Окно поиска закрылось, переписка долистала до сообщения.
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("search-input")).fetchSemanticsNodes().isEmpty() &&
                compose.onAllNodes(hasText("купить капусту и морковку")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun galleryWithTheSameMessageTwiceDoesNotCrash() {
        val photo = ChatMessage(from = "aizat", seq = 92, at = start, photo = ChatPhoto("p1", 100, 100))
        val again = photo.copy(text = "")
        val renamed = ChatMessage(from = "aizat", seq = 92, at = start + 5_000, photo = ChatPhoto("p2", 100, 100))
        val state = ChatState(restored = true, mine = listOf(photo, again, renamed), theirs = listOf(photo))
        compose.setContent {
            AppTheme {
                MediaGalleryDialog(
                    state = state,
                    owner = Person.AIZAT,
                    photoContent = { _, modifier -> Box(modifier.background(Color.Gray)) },
                    onOpenPhoto = {},
                    onOpenFile = {},
                    onEnsureFiles = { _, _ -> },
                    onClose = {},
                )
            }
        }
        waitShown("Фото 2")
    }
}
