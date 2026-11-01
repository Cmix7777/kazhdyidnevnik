package io.github.cmix7777.kazhdyidnevnik

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.cmix7777.kazhdyidnevnik.data.ChatCloud
import io.github.cmix7777.kazhdyidnevnik.data.ChatLogic
import io.github.cmix7777.kazhdyidnevnik.data.ChatMark
import io.github.cmix7777.kazhdyidnevnik.data.ChatState
import io.github.cmix7777.kazhdyidnevnik.data.CloudToken
import io.github.cmix7777.kazhdyidnevnik.data.PairCode
import io.github.cmix7777.kazhdyidnevnik.data.Person
import io.github.cmix7777.kazhdyidnevnik.data.TokenStore
import io.github.cmix7777.kazhdyidnevnik.service.JsoupTransport
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Random

/**
 * Переписка через настоящий Firestore проекта (с настоящими правилами базы): два «телефона» с новым
 * случайным кодом связи пишут друг другу. Так проверяется, что маски полей, отметки и архив проходят правила.
 */
@RunWith(AndroidJUnit4::class)
class ChatCloudLiveTest {

    private class Tokens : TokenStore {
        var token: CloudToken? = null
        override fun load(): CloudToken? = token
        override fun save(token: CloudToken?) {
            this.token = token
        }
    }

    @Test
    fun twoPhonesTalkThroughTheRealCloud() {
        val code = PairCode.generate()
        val aizat = ChatCloud(JsoupTransport, Tokens())
        val nastya = ChatCloud(JsoupTransport, Tokens())

        assertNull(nastya.mark(code, Person.AIZAT))
        assertFalse(aizat.putRead(code, Person.AIZAT, 1))

        var state = ChatLogic.append(ChatState(restored = true), Person.AIZAT, "Привет из теста", 1_000)
        var plan = ChatLogic.planUpload(state, Person.AIZAT, push = null, hook = null)
        aizat.upload(code, Person.AIZAT, plan.pages, plan.head, 1_000)
        state = ChatLogic.afterUpload(state, plan, 1_000, state.revision)

        assertTrue(aizat.putRead(code, Person.AIZAT, 7))
        assertTrue(aizat.putTyping(code, Person.AIZAT, 42))
        assertEquals(ChatMark(1_000, 7, 42), nastya.mark(code, Person.AIZAT))
        val (head, _) = nastya.head(code, Person.AIZAT)!!
        assertEquals("Привет из теста", head.messages.single().text)

        // Много длинных сообщений: голова переполняется, старые уходят в архив, отметки не теряются.
        repeat(40) { state = ChatLogic.append(state, Person.AIZAT, "ж".repeat(1500) + it, 2_000L + it) }
        plan = ChatLogic.planUpload(state, Person.AIZAT, push = null, hook = null)
        assertEquals(1, plan.pages.size)
        aizat.upload(code, Person.AIZAT, plan.pages, plan.head, 3_000)
        val mark = nastya.mark(code, Person.AIZAT)!!
        assertEquals(3_000L, mark.version)
        assertEquals(7L, mark.read)
        val page = nastya.page(code, Person.AIZAT, 0)!!
        val rolled = nastya.head(code, Person.AIZAT)!!.first
        assertEquals(41, page.messages.size + rolled.messages.size)

        // Фото туда и обратно и стирание.
        val bytes = ByteArray(200_000).also { Random(3).nextBytes(it) }
        aizat.putFile(code, "00112233445566aa", bytes, 4_000)
        assertArrayEquals(bytes, nastya.file(code, "00112233445566aa"))
        aizat.wipeFile(code, "00112233445566aa", 5_000)
        assertNull(nastya.file(code, "00112233445566aa"))

        // Голову оставляем пустой, чтобы не держать в облаке тестовый текст.
        aizat.upload(code, Person.AIZAT, emptyList(), plan.head.copy(messages = emptyList()), 6_000)
    }
}
