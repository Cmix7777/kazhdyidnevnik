package io.github.cmix7777.kazhdyidnevnik.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Важная дата. Если точная дата неизвестна, [approximate] подписывает её словами
 * («конец октября»), а [date] — ориентир для обратного отсчёта.
 */
data class Deadline(
    val title: String,
    val date: LocalDate,
    val approximate: String? = null,
    val note: String? = null,
)

object Deadlines {
    val default: List<Deadline> = listOf(
        Deadline(
            title = "Сдача сайта на C#",
            date = LocalDate.of(2026, 10, 31),
            approximate = "примерно конец октября",
        ),
        Deadline(
            title = "Практика",
            date = LocalDate.of(2026, 11, 2),
            note = "со 2 по 15 ноября",
        ),
        Deadline(
            title = "Экзамен по вождению",
            date = LocalDate.of(2026, 12, 1),
            approximate = "примерно декабрь",
            note = "внутренний экзамен автошколы может быть раньше",
        ),
        Deadline(
            title = "Квалификационный демоэкзамен",
            date = LocalDate.of(2027, 6, 1),
            approximate = "примерно июнь 2027",
        ),
    )
}

fun daysBetween(from: LocalDate, to: LocalDate): Long = ChronoUnit.DAYS.between(from, to)

/** Склонение: «1 день», «2 дня», «5 дней». */
fun pluralDays(n: Long): String = "$n " + plural(n, "день", "дня", "дней")

/** Склонение: «1 пара», «2 пары», «5 пар». */
fun pluralLessons(n: Int): String = "$n " + plural(n.toLong(), "пара", "пары", "пар")

fun plural(n: Long, one: String, few: String, many: String): String {
    val mod100 = kotlin.math.abs(n) % 100
    val mod10 = mod100 % 10
    return when {
        mod100 in 11L..14L -> many
        mod10 == 1L -> one
        mod10 in 2L..4L -> few
        else -> many
    }
}

/** «через 26 дней», «сегодня», «3 дня назад». */
fun countdownText(days: Long): String = when {
    days > 0 -> "через ${pluralDays(days)}"
    days == 0L -> "сегодня"
    else -> "${pluralDays(-days)} назад"
}
