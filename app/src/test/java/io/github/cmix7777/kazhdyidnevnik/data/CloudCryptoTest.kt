package io.github.cmix7777.kazhdyidnevnik.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class CloudCryptoTest {

    private val code = "abcdefghjkmnpqrstuvw"

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    @Test
    fun hkdfMatchesTheTestVectorFromRfc5869() {
        // Тестовый пример 1 из RFC 5869.
        val secret = ByteArray(22) { 0x0b }
        val salt = ByteArray(13) { it.toByte() }
        val info = ByteArray(10) { (0xf0 + it).toByte() }
        val out = CloudCrypto.hkdfSha256(secret, salt, info, 42)
        assertEquals(
            "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865",
            hex(out),
        )
    }

    @Test
    fun hkdfWithoutSaltMatchesTheVectorToo() {
        // Тестовый пример 3 из RFC 5869: пустая соль и пустая информация.
        val secret = ByteArray(22) { 0x0b }
        val out = CloudCrypto.hkdfSha256(secret, ByteArray(0), ByteArray(0), 42)
        assertEquals(
            "8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8",
            hex(out),
        )
    }

    @Test
    fun keysAreStableAndDependOnTheCode() {
        val a = CloudCrypto.derive(code)
        val b = CloudCrypto.derive(code)
        assertEquals(a.docId(Person.AIZAT), b.docId(Person.AIZAT))
        assertEquals(hex(a.aesKey), hex(b.aesKey))
        val other = CloudCrypto.derive("zzzzzzzzzzzzzzzzzzzz")
        assertFalse(a.docId(Person.AIZAT) == other.docId(Person.AIZAT))
        assertFalse(hex(a.aesKey) == hex(other.aesKey))
        assertEquals(32, a.aesKey.size)
    }

    @Test
    fun documentNameDoesNotRevealTheKey() {
        val keys = CloudCrypto.derive(code)
        val name = keys.docId(Person.AIZAT).substringBefore("-")
        assertEquals(32, name.length)
        assertTrue(name.all { it in "0123456789abcdef" })
        assertFalse(hex(keys.aesKey).startsWith(name))
        assertFalse(hex(keys.aesKey).contains(name))
        assertFalse(name.contains(code))
    }

    @Test
    fun sealAndOpenRoundTrip() {
        val keys = CloudCrypto.derive(code)
        val doc = keys.docId(Person.NASTYA)
        for (text in listOf("", "KD1:abc-_", "Смена 16:30–21:30, пары и всё остальное ".repeat(500))) {
            val sealed = CloudCrypto.seal(keys, doc, text)
            assertEquals(text, CloudCrypto.open(keys, doc, sealed))
        }
    }

    @Test
    fun sealedTextIsUnreadableAndUrlSafe() {
        val keys = CloudCrypto.derive(code)
        val doc = keys.docId(Person.AIZAT)
        val sealed = CloudCrypto.seal(keys, doc, "KD1:секретные дела")
        assertTrue(sealed.startsWith(CloudCrypto.PREFIX))
        assertFalse(sealed.contains("секретные"))
        assertTrue(sealed.removePrefix(CloudCrypto.PREFIX).all { it.isLetterOrDigit() || it == '-' || it == '_' })
    }

    @Test
    fun everySealingUsesAFreshNonce() {
        val keys = CloudCrypto.derive(code)
        val doc = keys.docId(Person.AIZAT)
        val first = CloudCrypto.seal(keys, doc, "одно и то же")
        val second = CloudCrypto.seal(keys, doc, "одно и то же")
        assertFalse(first == second)
        // Со своим источником случайных чисел результат повторяется (так проверяется формат).
        assertEquals(
            CloudCrypto.seal(keys, doc, "x", Random(7)),
            CloudCrypto.seal(keys, doc, "x", Random(7)),
        )
    }

    @Test
    fun anotherCodeCannotOpenIt() {
        val keys = CloudCrypto.derive(code)
        val doc = keys.docId(Person.AIZAT)
        val sealed = CloudCrypto.seal(keys, doc, "дела")
        val stranger = CloudCrypto.derive("zzzzzzzzzzzzzzzzzzzz")
        assertNull(CloudCrypto.open(stranger, doc, sealed))
        assertNotNull(CloudCrypto.open(keys, doc, sealed))
    }

    @Test
    fun changedDataIsRejected() {
        val keys = CloudCrypto.derive(code)
        val doc = keys.docId(Person.AIZAT)
        val sealed = CloudCrypto.seal(keys, doc, "дела на завтра")
        val body = sealed.removePrefix(CloudCrypto.PREFIX)
        for (index in listOf(0, body.length / 2, body.length - 1)) {
            val swapped = if (body[index] == 'A') 'B' else 'A'
            val broken = CloudCrypto.PREFIX + body.substring(0, index) + swapped + body.substring(index + 1)
            assertNull("изменён знак $index", CloudCrypto.open(keys, doc, broken))
        }
        assertNull(CloudCrypto.open(keys, doc, sealed.dropLast(4)))
    }

    @Test
    fun documentIsBoundToItsOwner() {
        // Дела Айзата нельзя выдать за дела Насти, подложив их в её документ.
        val keys = CloudCrypto.derive(code)
        val sealed = CloudCrypto.seal(keys, keys.docId(Person.AIZAT), "дела Айзата")
        assertNull(CloudCrypto.open(keys, keys.docId(Person.NASTYA), sealed))
    }

    @Test
    fun garbageIsRejectedWithoutCrashing() {
        val keys = CloudCrypto.derive(code)
        val doc = keys.docId(Person.AIZAT)
        for (text in listOf("", "KD1:abc", "KE2:", "KE2:!!!", "KE2:AAAA", "KE2:" + "A".repeat(40), "plain")) {
            assertNull("для «$text»", CloudCrypto.open(keys, doc, text))
        }
    }

    @Test
    fun keysDoNotPrintTheirSecrets() {
        val text = CloudCrypto.derive(code).toString()
        assertFalse(text.contains(hex(CloudCrypto.derive(code).aesKey)))
    }
}
