package io.github.cmix7777.kazhdyidnevnik.data

import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import java.util.Random
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Два ключа, выведенных из кода связи: имя документа в облаке и ключ шифрования. Они получаются из кода
 * независимо друг от друга (HKDF), поэтому по имени документа, которое видит сервер, ни код, ни ключ
 * не восстановить. Сам код с телефона никуда, кроме мессенджера, не уходит.
 */
class CloudKeys internal constructor(private val docPrefix: String, internal val aesKey: ByteArray) {

    /** Имя документа этого человека внутри пары. Правила базы требуют длину от 20 до 60 знаков. */
    fun docId(person: Person): String = docPrefix + "-" + person.id

    override fun toString(): String = "CloudKeys(скрыто)"
}

/**
 * Шифрование дел перед отправкой в облако: AES-256-GCM, свой случайный одноразовый номер на каждую
 * отправку, а имя документа входит в проверяемые данные, поэтому чужой документ подменить нельзя.
 * В облаке остаётся строка вида KE2:... без единого читаемого слова.
 */
object CloudCrypto {

    const val PREFIX = "KE2:"
    private const val SALT = "kazhdyidnevnik/cloud/v2"
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val MAC = "HmacSHA256"

    fun derive(code: String): CloudKeys {
        val secret = code.toByteArray(Charsets.UTF_8)
        val salt = SALT.toByteArray(Charsets.UTF_8)
        val doc = hkdfSha256(secret, salt, "doc-id".toByteArray(Charsets.UTF_8), 16).toHex()
        val key = hkdfSha256(secret, salt, "payload-key".toByteArray(Charsets.UTF_8), 32)
        return CloudKeys(doc, key)
    }

    /** Зашифровать [plain] для документа [docId]. */
    fun seal(keys: CloudKeys, docId: String, plain: String, random: Random = SecureRandom()): String {
        val nonce = ByteArray(NONCE_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keys.aesKey, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(docId.toByteArray(Charsets.UTF_8))
        val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(nonce + body)
    }

    /** Расшифровать. null — не тот код, данные изменены или это вообще не наша строка. */
    fun open(keys: CloudKeys, docId: String, sealed: String): String? {
        if (!sealed.startsWith(PREFIX)) return null
        return try {
            val raw = Base64.getUrlDecoder().decode(sealed.removePrefix(PREFIX))
            if (raw.size < NONCE_BYTES + TAG_BITS / 8) return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(keys.aesKey, "AES"),
                GCMParameterSpec(TAG_BITS, raw.copyOfRange(0, NONCE_BYTES)),
            )
            cipher.updateAAD(docId.toByteArray(Charsets.UTF_8))
            String(cipher.doFinal(raw, NONCE_BYTES, raw.size - NONCE_BYTES), Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /** HKDF по RFC 5869 на HMAC-SHA256. Проверен на тестовом примере из стандарта. */
    internal fun hkdfSha256(secret: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length in 1..(255 * 32)) { "Неверная длина ключа" }
        val extract = Mac.getInstance(MAC)
        extract.init(SecretKeySpec(if (salt.isEmpty()) ByteArray(32) else salt, MAC))
        val pseudoRandomKey = extract.doFinal(secret)

        val expand = Mac.getInstance(MAC)
        expand.init(SecretKeySpec(pseudoRandomKey, MAC))
        val out = ByteArray(length)
        var previous = ByteArray(0)
        var filled = 0
        var counter = 1
        while (filled < length) {
            expand.update(previous)
            expand.update(info)
            expand.update(counter.toByte())
            previous = expand.doFinal()
            val take = minOf(previous.size, length - filled)
            System.arraycopy(previous, 0, out, filled, take)
            filled += take
            counter++
        }
        return out
    }

    internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
