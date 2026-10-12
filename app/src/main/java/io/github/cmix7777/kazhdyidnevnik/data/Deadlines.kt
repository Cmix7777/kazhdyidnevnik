package io.github.cmix7777.kazhdyidnevnik.data

import java.time.LocalDate
import java.time.Year
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
    /** Есть у дат, добавленных в приложении: по нему их меняют и удаляют. */
    val id: String? = null,
    /**
     * Повод. У особой даты (день рождения, годовщина) он есть: она повторяется каждый год, видна обоим,
     * в этот день приложение встречает анимацией. null — обычная важная дата без повода.
     */
    val occasion: Occasion? = null,
    /** Чей день рождения (null — кого-то другого). */
    val whose: Person? = null,
    /** Год в [date] настоящий: тогда считаем, сколько исполнится или сколько лет вместе. */
    val yearKnown: Boolean = true,
) {
    val special: Boolean get() = occasion != null

    /** Ближайший раз с [today] включительно: особая дата — в этом году или в следующем. */
    fun nextDate(today: LocalDate): LocalDate = if (occasion == null) date else SpecialDates.next(date, today)

    /** Сколько лет исполнится (или лет вместе) в день [day]; null — год неизвестен или это не особая дата. */
    fun yearsAt(day: LocalDate): Int? =
        if (occasion != null && yearKnown) (day.year - date.year).takeIf { it > 0 } else null

    /** Название для показа: своё или по поводу. */
    val shownTitle: String
        get() = title.trim().ifEmpty {
            when (occasion) {
                Occasion.BIRTHDAY -> whose?.let { "День рождения ${it.genitive}" } ?: "День рождения"
                Occasion.ANNIVERSARY -> "Годовщина"
                null -> ""
            }
        }

    /** Подпись к особой дате в день [day]: «исполнится 21», «3 года вместе». */
    fun yearsText(day: LocalDate): String? {
        val years = yearsAt(day) ?: return null
        return when (occasion) {
            Occasion.BIRTHDAY -> "исполнится $years"
            Occasion.ANNIVERSARY -> "$years ${plural(years.toLong(), "год", "года", "лет")} вместе"
            null -> null
        }
    }
}

/** Поводы особых дат. Новый год отдельно не добавляется: он особый день для всех сам. */
enum class Occasion(val id: String, val title: String) {
    BIRTHDAY("birthday", "День рождения"),
    ANNIVERSARY("anniversary", "Годовщина");

    companion object {
        fun fromId(id: String?): Occasion? = entries.firstOrNull { it.id == id }
    }
}

/** Что за особый день встречает приложение анимацией. */
enum class SpecialKind { ANNIVERSARY, BIRTHDAY, NEW_YEAR }

/** Особый день сегодня: вид, дата-основание (у Нового года её нет) и ключ, чтобы показать его один раз. */
data class SpecialToday(val kind: SpecialKind, val date: Deadline?, val key: String)

object SpecialDates {

    /** Дата [date] в году [year]; 29 февраля в невисокосный год — 28-е. */
    fun onYear(date: LocalDate, year: Int): LocalDate =
        if (date.monthValue == 2 && date.dayOfMonth == 29 && !Year.isLeap(year.toLong())) {
            LocalDate.of(year, 2, 28)
        } else {
            date.withYear(year)
        }

    /** Ближайший раз особой даты, начиная с [today]. */
    fun next(date: LocalDate, today: LocalDate): LocalDate {
        val thisYear = onYear(date, today.year)
        return if (thisYear.isBefore(today)) onYear(date, today.year + 1) else thisYear
    }

    /** Особые даты обоих без повторов: свои и те, которыми поделился второй человек. */
    fun merge(mine: List<Deadline>, partners: List<Deadline>): List<Deadline> {
        val own = mine.filter { it.special }
        val ids = own.mapNotNull { it.id }.toSet()
        return own + partners.filter { it.special && it.id !in ids }
    }

    /**
     * Какой особый день сегодня. Годовщина важнее дня рождения, день рождения важнее Нового года;
     * если совпало несколько, показываем один.
     */
    fun today(dates: List<Deadline>, today: LocalDate): SpecialToday? {
        val todays = dates.filter { it.special && it.nextDate(today) == today }
        todays.firstOrNull { it.occasion == Occasion.ANNIVERSARY }?.let {
            return SpecialToday(SpecialKind.ANNIVERSARY, it, "$today:${it.id}")
        }
        todays.firstOrNull { it.occasion == Occasion.BIRTHDAY }?.let {
            return SpecialToday(SpecialKind.BIRTHDAY, it, "$today:${it.id}")
        }
        if (today.monthValue == 1 && today.dayOfMonth == 1) return SpecialToday(SpecialKind.NEW_YEAR, null, "$today:new-year")
        return null
    }

    /** За сколько дней до дня рождения напомнить заранее. */
    const val BIRTHDAY_AHEAD_DAYS = 7
}

object Deadlines {
    val default: List<Deadline> = listOf(
        Deadline(
            title = "Проверка по тестированию",
            date = LocalDate.of(2026, 10, 12),
            note = "пары в 10:00 и 12:10, онлайн",
        ),
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
