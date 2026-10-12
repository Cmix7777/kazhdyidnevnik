package io.github.cmix7777.kazhdyidnevnik.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Капля привета: один человек зажимает имя второго и «бросает» каплю. Второй увидит её, когда откроет
 * приложение. Несколько капель за сутки сливаются в одну с числом и временем каждой.
 */
object GreetingDrops {

    /** Сколько хранить отправленные капли (их видно второму в течение суток, запас на задержку связи). */
    const val KEEP_MS = 48L * 60 * 60 * 1000

    /** Капли старше суток уже не показываются, а тихо отмечаются просмотренными. */
    const val SHOW_MS = 24L * 60 * 60 * 1000

    /** Время на отмену после броска. */
    const val UNDO_MS = 3_000L

    fun recent(drops: List<Long>, now: Long): List<Long> = drops.filter { now - it in 0 until KEEP_MS }.sorted()

    /** Добавить отправленную каплю и убрать старые. */
    fun withSent(drops: List<Long>, at: Long): List<Long> = recent(drops + at, at)

    /** Что показать сейчас: непросмотренные капли за последние сутки, по времени. */
    fun unseen(drops: List<Long>, seenUntil: Long, now: Long): List<Long> =
        drops.filter { it > seenUntil && now - it < SHOW_MS && it <= now + 5 * 60 * 1000 }.sorted()

    /** До какого времени считать всё просмотренным (самая поздняя капля, если она новее прежней отметки). */
    fun seenMark(drops: List<Long>, seenUntil: Long): Long = maxOf(seenUntil, drops.maxOrNull() ?: 0L)

    /** «Айзат думал о тебе», «Настя думала о тебе 3 раза». */
    fun title(from: Person, count: Int): String {
        val verb = if (from == Person.AIZAT) "думал" else "думала"
        val base = "${from.shortName} $verb о тебе"
        return if (count <= 1) base else "$base $count ${times(count)}"
    }

    /** «сегодня в 14:20», «в 9:10, 14:20 и 18:05», «последний раз в 18:05». */
    fun subtitle(drops: List<Long>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): String {
        val times = drops.sorted().map { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone) }
        if (times.isEmpty()) return ""
        fun clock(t: LocalDateTime) = "%d:%02d".format(t.hour, t.minute)
        if (times.size == 1) {
            val t = times.first()
            val day = when (t.toLocalDate()) {
                today -> "сегодня"
                today.minusDays(1) -> "вчера"
                else -> "недавно"
            }
            return "$day в ${clock(t)}"
        }
        if (times.size > 5) return "последний раз в ${clock(times.last())}"
        val list = times.map(::clock)
        return "в " + list.dropLast(1).joinToString(", ") + " и " + list.last()
    }

    /** «раз» или «раза»: 2 раза, 5 раз, 22 раза. */
    private fun times(n: Int): String {
        val mod100 = n % 100
        val mod10 = n % 10
        return if (mod10 in 2..4 && mod100 !in 12..14) "раза" else "раз"
    }

    /** «Капля полетела к Насте». */
    fun sentText(to: Person): String = "Капля полетела к " + if (to == Person.AIZAT) "Айзату" else "Насте"
}
