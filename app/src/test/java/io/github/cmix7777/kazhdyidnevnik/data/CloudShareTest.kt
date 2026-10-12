package io.github.cmix7777.kazhdyidnevnik.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.util.Random

class CloudShareTest {

    private class Call(val method: String, val url: String, val body: String, val headers: Map<String, String>)

    /** Подставка вместо сети: записывает запросы и отвечает по сценарию. */
    private class FakeHttp(val answer: (Call) -> HttpReply) : HttpTransport {
        val calls = mutableListOf<Call>()

        override fun get(url: String, headers: Map<String, String>): HttpReply {
            val call = Call("GET", url, "", headers)
            calls += call
            return answer(call)
        }

        override fun post(url: String, body: String, contentType: String, headers: Map<String, String>): HttpReply {
            val call = Call("POST", url, body, headers + ("Content-Type" to contentType))
            calls += call
            return answer(call)
        }
    }

    private class MemoryTokens(var token: CloudToken? = null) : TokenStore {
        override fun load(): CloudToken? = token
        override fun save(token: CloudToken?) {
            this.token = token
        }
    }

    private val code = "abcdefghjkmnpqrstuvw"
    private var clock = 1_000_000L

    private fun signUpReply(id: String = "ID-1", refresh: String = "REF-1") =
        HttpReply(200, """{"kind":"identitytoolkit#SignupNewUserResponse","idToken":"$id","refreshToken":"$refresh","expiresIn":"3600","localId":"u"}""")

    private fun cloud(http: HttpTransport, tokens: TokenStore) = CloudShares(http, tokens, now = { clock })

    // ---- код связи ----

    @Test
    fun generatedCodeIsLongAndReadable() {
        val first = PairCode.generate(Random(1))
        assertEquals(PairCode.LENGTH, first.length)
        assertTrue(PairCode.isValid(first))
        assertTrue(first.none { it in "ilo01" })
        assertFalse(first == PairCode.generate(Random(2)))
    }

    @Test
    fun displayAndParseRoundTripEvenInsideAMessage() {
        val shown = PairCode.display(code)
        assertEquals("kds-abcde-fghjk-mnpqr-stuvw", shown)
        val message = PairCode.message(Person.AIZAT, code)
        assertTrue(message.contains(shown))
        // Мессенджер мог сделать буквы заглавными или приписать текст вокруг.
        assertEquals(code, PairCode.parse("Привет!\n" + message.uppercase() + "\nспасибо"))
        assertEquals(code, PairCode.parse("  $shown  "))
    }

    @Test
    fun parseRejectsNonsense() {
        assertNull(PairCode.parse("нет тут кода"))
        assertNull(PairCode.parse("kds-abcde-fghjk-mnpqr"))
        // Буквы i, l, o и цифры 0, 1 в коде не бывают.
        assertNull(PairCode.parse("kds-abcde-fghjk-mnpqr-stuv0"))
        assertNull(PairCode.parse("kds-abcde-fghjk-mnpqr-stuvi"))
        assertFalse(PairCode.isValid("short"))
        assertFalse(PairCode.isValid(code + "x"))
    }

    @Test
    fun oldStyleCodesAreRecognisedButNeverAccepted() {
        val old = "Код связи Каждогодневника (Настя): kdp-abcde-fghjk-mnpqr-stuvw"
        assertNull(PairCode.parse(old))
        assertTrue(PairCode.isLegacy(old))
        assertFalse(PairCode.isLegacy(PairCode.message(Person.AIZAT, code)))
        assertFalse(PairCode.isLegacy("просто текст"))
    }

    @Test
    fun documentNameDependsOnWhoOwnsItAndHidesTheCode() {
        val keys = CloudCrypto.derive(code)
        val aizat = keys.docId(Person.AIZAT)
        val nastya = keys.docId(Person.NASTYA)
        assertTrue(aizat.endsWith("-aizat"))
        assertTrue(nastya.endsWith("-nastya"))
        assertFalse(aizat == nastya)
        assertFalse(aizat.contains(code))
        // Правила базы требуют имя длиной от 20 до 60 знаков.
        assertTrue(aizat.length in 20..60)
        assertTrue(nastya.length in 20..60)
    }

    @Test
    fun fingerprintChangesOnlyWithData() {
        assertEquals(PairCode.fingerprint("a"), PairCode.fingerprint("a"))
        assertFalse(PairCode.fingerprint("a") == PairCode.fingerprint("b"))
        assertEquals(64, PairCode.fingerprint("a").length)
    }

    // ---- отправка ----

    @Test
    fun publishSignsInThenWritesTheDocument() {
        val http = FakeHttp { call ->
            when {
                call.url.contains("accounts:signUp") -> signUpReply()
                call.url.endsWith(":commit") -> HttpReply(200, """{"writeResults":[{}]}""")
                else -> HttpReply(500, "")
            }
        }
        val tokens = MemoryTokens()
        cloud(http, tokens).publish(code, Person.NASTYA, "KD1:abc-_", 123L)

        assertEquals(2, http.calls.size)
        val signUp = http.calls[0]
        assertTrue(signUp.url.contains("key=${CloudConfig.API_KEY}"))
        assertEquals("""{"returnSecureToken":true}""", signUp.body)

        val commit = http.calls[1]
        assertEquals(
            "https://firestore.googleapis.com/v1/projects/kazhdyidnevnik/databases/(default)/documents:commit",
            commit.url,
        )
        assertEquals("Bearer ID-1", commit.headers["Authorization"])
        assertEquals("application/json", commit.headers["Content-Type"])
        val update = Json.parseToJsonElement(commit.body).jsonObject["writes"]!!.jsonArray[0].jsonObject["update"]!!.jsonObject
        val keys = CloudCrypto.derive(code)
        val docId = keys.docId(Person.NASTYA)
        assertEquals(
            "projects/kazhdyidnevnik/databases/(default)/documents/shares/$docId",
            update["name"]!!.jsonPrimitive.content,
        )
        val fields = update["fields"]!!.jsonObject
        // В облако уходит зашифрованная строка, а не сами дела и не код связи.
        val stored = fields["payload"]!!.jsonObject["stringValue"]!!.jsonPrimitive.content
        assertTrue(stored.startsWith(CloudCrypto.PREFIX))
        assertFalse(stored.contains("KD1:abc-_"))
        assertFalse(commit.body.contains(code))
        assertEquals("KD1:abc-_", CloudCrypto.open(keys, docId, stored))
        assertEquals("123", fields["updatedAt"]!!.jsonObject["integerValue"]!!.jsonPrimitive.content)
        // Только два поля: больше правила базы не пропустят.
        assertEquals(setOf("payload", "updatedAt"), fields.keys)

        assertEquals("ID-1", tokens.token?.idToken)
        assertEquals(clock + 3_600_000L, tokens.token?.expiresAtMillis)
    }

    @Test
    fun savedTokenIsReusedWithoutSigningInAgain() {
        val http = FakeHttp { HttpReply(200, "{}") }
        val tokens = MemoryTokens(CloudToken("SAVED", "R", clock + 3_600_000L))
        cloud(http, tokens).publish(code, Person.AIZAT, "KD1:x", 1L)
        assertEquals(1, http.calls.size)
        assertEquals("Bearer SAVED", http.calls[0].headers["Authorization"])
    }

    @Test
    fun expiredTokenIsRefreshedNotRecreated() {
        val http = FakeHttp { call ->
            when {
                call.url.startsWith("https://securetoken.googleapis.com/v1/token") ->
                    HttpReply(200, """{"id_token":"NEW","refresh_token":"R2","expires_in":"3600"}""")
                else -> HttpReply(200, "{}")
            }
        }
        val tokens = MemoryTokens(CloudToken("OLD", "R1/+=", clock + 10_000L)) // меньше минуты до конца
        cloud(http, tokens).publish(code, Person.AIZAT, "KD1:x", 1L)

        val refresh = http.calls[0]
        assertEquals("grant_type=refresh_token&refresh_token=R1%2F%2B%3D", refresh.body)
        assertEquals("application/x-www-form-urlencoded", refresh.headers["Content-Type"])
        assertEquals("Bearer NEW", http.calls[1].headers["Authorization"])
        assertEquals("R2", tokens.token?.refreshToken)
        assertTrue(http.calls.none { it.url.contains("accounts:signUp") })
    }

    @Test
    fun failedRefreshFallsBackToANewAnonymousSignIn() {
        val http = FakeHttp { call ->
            when {
                call.url.startsWith("https://securetoken.googleapis.com") -> HttpReply(400, """{"error":{"message":"TOKEN_EXPIRED"}}""")
                call.url.contains("accounts:signUp") -> signUpReply(id = "FRESH", refresh = "RF")
                else -> HttpReply(200, "{}")
            }
        }
        val tokens = MemoryTokens(CloudToken("OLD", "R1", clock - 1L))
        cloud(http, tokens).publish(code, Person.AIZAT, "KD1:x", 1L)
        assertEquals("Bearer FRESH", http.calls.last().headers["Authorization"])
        assertEquals("RF", tokens.token?.refreshToken)
    }

    @Test
    fun rejectedTokenIsReplacedAndTheRequestRepeatedOnce() {
        var commits = 0
        val http = FakeHttp { call ->
            when {
                call.url.contains("accounts:signUp") -> signUpReply(id = "SECOND")
                call.url.endsWith(":commit") -> {
                    commits++
                    if (commits == 1) HttpReply(401, """{"error":{"status":"UNAUTHENTICATED"}}""") else HttpReply(200, "{}")
                }
                else -> HttpReply(500, "")
            }
        }
        val tokens = MemoryTokens(CloudToken("STALE", "R", clock + 3_600_000L))
        cloud(http, tokens).publish(code, Person.AIZAT, "KD1:x", 1L)
        assertEquals(2, commits)
        assertEquals("Bearer SECOND", http.calls.last().headers["Authorization"])
        assertEquals("SECOND", tokens.token?.idToken)
    }

    @Test
    fun rulesDenialIsReportedClearly() {
        val http = FakeHttp { call ->
            if (call.url.contains("accounts:signUp")) signUpReply()
            else HttpReply(403, """{"error":{"code":403,"message":"Missing or insufficient permissions."}}""")
        }
        try {
            cloud(http, MemoryTokens()).publish(code, Person.AIZAT, "KD1:x", 1L)
            fail("должна быть ошибка")
        } catch (e: CloudException) {
            assertEquals(403, e.code)
            assertTrue(e.message!!.contains("правилами базы"))
        }
    }

    @Test
    fun signInFailureIsReported() {
        val http = FakeHttp { HttpReply(400, """{"error":{"message":"OPERATION_NOT_ALLOWED"}}""") }
        try {
            cloud(http, MemoryTokens()).fetch(code, Person.AIZAT)
            fail("должна быть ошибка")
        } catch (e: CloudException) {
            assertTrue(e.message!!.contains("войти в облако"))
            assertTrue(e.message!!.contains("OPERATION_NOT_ALLOWED"))
        }
    }

    @Test
    fun invalidCodeNeverReachesTheNetwork() {
        val http = FakeHttp { HttpReply(200, "{}") }
        try {
            cloud(http, MemoryTokens()).publish("short", Person.AIZAT, "KD1:x", 1L)
            fail("должна быть ошибка")
        } catch (e: IllegalArgumentException) {
            assertTrue(http.calls.isEmpty())
        }
    }

    // ---- получение ----

    @Test
    fun fetchReadsTheOtherPersonsDocument() {
        val keys = CloudCrypto.derive(code)
        val docId = keys.docId(Person.NASTYA)
        val sealed = CloudCrypto.seal(keys, docId, "KD1:zzz")
        val http = FakeHttp { call ->
            if (call.url.contains("accounts:signUp")) {
                signUpReply()
            } else {
                HttpReply(
                    200,
                    """{"name":"x","fields":{"payload":{"stringValue":"$sealed"},"updatedAt":{"integerValue":"1790000000000"}},"createTime":"t"}""",
                )
            }
        }
        val remote = cloud(http, MemoryTokens()).fetch(code, Person.NASTYA)
        assertNotNull(remote)
        assertEquals("KD1:zzz", remote!!.payload)
        assertEquals(1790000000000L, remote.updatedAt)
        val get = http.calls.last()
        assertEquals("GET", get.method)
        assertEquals(
            "https://firestore.googleapis.com/v1/projects/kazhdyidnevnik/databases/(default)/documents/shares/$docId",
            get.url,
        )
        assertFalse(get.url.contains(code))
    }

    @Test
    fun documentThatDoesNotDecryptIsAnError() {
        val keys = CloudCrypto.derive(code)
        val otherKeys = CloudCrypto.derive("zzzzzzzzzzzzzzzzzzzz")
        val docId = keys.docId(Person.NASTYA)
        val answers = listOf(
            "KD1:открытый текст без шифрования",
            CloudCrypto.seal(otherKeys, docId, "KD1:чужой ключ"),
            CloudCrypto.seal(keys, keys.docId(Person.AIZAT), "KD1:чужой документ"),
        )
        for (stored in answers) {
            val http = FakeHttp { call ->
                if (call.url.contains("accounts:signUp")) {
                    signUpReply()
                } else {
                    HttpReply(200, """{"fields":{"payload":{"stringValue":"$stored"},"updatedAt":{"integerValue":"1"}}}""")
                }
            }
            try {
                cloud(http, MemoryTokens()).fetch(code, Person.NASTYA)
                fail("должна быть ошибка для: $stored")
            } catch (e: CloudException) {
                assertTrue(e.message!!.contains("не расшифровались"))
            }
        }
    }

    @Test
    fun missingDocumentMeansNothingSentYet() {
        val http = FakeHttp { call ->
            if (call.url.contains("accounts:signUp")) signUpReply() else HttpReply(404, """{"error":{"status":"NOT_FOUND"}}""")
        }
        assertNull(cloud(http, MemoryTokens()).fetch(code, Person.NASTYA))
    }

    @Test
    fun garbledAnswerIsAnError() {
        val http = FakeHttp { call ->
            if (call.url.contains("accounts:signUp")) signUpReply() else HttpReply(200, "<html>не json</html>")
        }
        try {
            cloud(http, MemoryTokens()).fetch(code, Person.NASTYA)
            fail("должна быть ошибка")
        } catch (e: CloudException) {
            assertTrue(e.message!!.contains("непонятно"))
        }
    }

    // ---- целиком: дела уходят из одного телефона и приходят в другой ----

    @Test
    fun eventsTravelThroughTheCloudAndBack() {
        val stored = mutableMapOf<String, String>()
        val http = FakeHttp { call ->
            when {
                call.url.contains("accounts:signUp") -> signUpReply()
                call.url.endsWith(":commit") -> {
                    val update = Json.parseToJsonElement(call.body).jsonObject["writes"]!!.jsonArray[0].jsonObject["update"]!!.jsonObject
                    val name = update["name"]!!.jsonPrimitive.content.substringAfterLast("/")
                    stored[name] = update["fields"]!!.jsonObject["payload"]!!.jsonObject["stringValue"]!!.jsonPrimitive.content
                    HttpReply(200, "{}")
                }
                else -> {
                    val payload = stored[call.url.substringAfterLast("/")]
                    if (payload == null) {
                        HttpReply(404, "")
                    } else {
                        val escaped = JsonPrimitive(payload).toString()
                        HttpReply(200, """{"fields":{"payload":{"stringValue":$escaped},"updatedAt":{"integerValue":"5"}}}""")
                    }
                }
            }
        }
        val nastyaEvent = UserEvent(
            id = "n1",
            kind = EventKind.WORK,
            title = "Смена",
            date = LocalDate.of(2026, 10, 13),
            start = LocalTime.of(16, 30),
            end = LocalTime.of(21, 30),
            note = "личное",
        )
        val sent = SharedEvents(Person.NASTYA, 100L, PartnerShare.relevant(listOf(nastyaEvent), LocalDate.of(2026, 10, 12)))
        val api = cloud(http, MemoryTokens())
        api.publish(code, Person.NASTYA, PartnerShare.encode(sent), 100L)
        // В хранилище облака лежит только шифротекст.
        assertEquals(1, stored.size)
        assertTrue(stored.values.single().startsWith(CloudCrypto.PREFIX))

        // Телефон Айзата читает документ Насти и разбирает дела.
        val remote = api.fetch(code, Person.NASTYA)!!
        val received = PartnerShare.decode(remote.payload)!!
        assertEquals(Person.NASTYA, received.from)
        assertEquals(1, received.events.size)
        assertEquals("Смена", received.events[0].title)
        assertEquals(LocalTime.of(16, 30), received.events[0].start)
        assertEquals("", received.events[0].note)

        // Своего документа у Айзата в облаке ещё нет.
        assertNull(api.fetch(code, Person.AIZAT))
    }
}
