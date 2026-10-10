package io.github.cmix7777.kazhdyidnevnik.data

import java.time.LocalDate
import kotlin.math.abs

/**
 * Запись бюджета: доход («плюс») или трата («минус»).
 * [amount] — в копейках, всегда больше нуля; знак задаёт [income].
 */
data class MoneyEntry(
    val id: String,
    val date: LocalDate,
    val amount: Long,
    val income: Boolean,
    val category: String,
    val note: String = "",
    val createdAt: Long = 0L,
) {
    val signed: Long get() = if (income) amount else -amount
}

object MoneyCategories {
    val expense: List<String> = listOf(
        "Еда", "Транспорт", "Одежда", "Красота", "Связь", "Развлечения",
        "Подарки", "Дом", "Здоровье", "Учёба", "Другое",
    )
    val income: List<String> = listOf("Зарплата", "Стипендия", "Подарок", "Возврат", "Другое")
}

/** Остаток, графики и статистика по записям бюджета. */
object MoneyStats {

    /** Остаток после всех записей до [upTo] включительно (или всех записей). */
    fun balance(entries: List<MoneyEntry>, upTo: LocalDate? = null): Long =
        entries.filter { upTo == null || !it.date.isAfter(upTo) }.sumOf { it.signed }

    /** Остаток на конец каждого дня с [from] по [to]: дни без записей повторяют прошлый остаток. */
    fun balanceByDay(entries: List<MoneyEntry>, from: LocalDate, to: LocalDate): List<Pair<LocalDate, Long>> {
        if (to.isBefore(from)) return emptyList()
        val byDate = entries.groupBy { it.date }.mapValues { (_, list) -> list.sumOf { it.signed } }
        var running = balance(entries, from.minusDays(1))
        val result = ArrayList<Pair<LocalDate, Long>>()
        var day = from
        while (!day.isAfter(to)) {
            running += byDate[day] ?: 0L
            result += day to running
            day = day.plusDays(1)
        }
        return result
    }

    /** Пришло и ушло за период (обе суммы положительные). */
    fun totals(entries: List<MoneyEntry>, from: LocalDate, to: LocalDate): Pair<Long, Long> {
        val inRange = entries.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
        return inRange.filter { it.income }.sumOf { it.amount } to inRange.filter { !it.income }.sumOf { it.amount }
    }

    /** Траты по категориям за период, от больших к меньшим. */
    fun expensesByCategory(entries: List<MoneyEntry>, from: LocalDate, to: LocalDate): List<Pair<String, Long>> =
        entries
            .filter { !it.income && !it.date.isBefore(from) && !it.date.isAfter(to) }
            .groupBy { it.category }
            .map { (category, list) -> category to list.sumOf { it.amount } }
            .sortedByDescending { it.second }
}

/** Ввод и показ сумм: «70 000 ₽», «99,90 ₽», «−1 200 ₽». */
object MoneyFormat {

    private const val NBSP = ' '
    private const val MAX_KOPECKS = 100_000_000_000L

    /** Сумма из текста в копейках или null, если это не сумма больше нуля. */
    fun parse(text: String): Long? {
        val cleaned = text.trim()
            .replace(" ", "")
            .replace(NBSP.toString(), "")
            .replace(" ", "")
            .replace("₽", "")
            .replace("руб", "")
            .replace(',', '.')
        if (cleaned.isEmpty() || cleaned.count { it == '.' } > 1) return null
        if (!cleaned.all { it.isDigit() || it == '.' }) return null
        val rubles = cleaned.substringBefore('.').ifEmpty { "0" }
        val fraction = cleaned.substringAfter('.', "")
        if (fraction.length > 2 || rubles.length > 10) return null
        val kopecks = rubles.toLong() * 100 + fraction.padEnd(2, '0').toLong()
        return kopecks.takeIf { it in 1..MAX_KOPECKS }
    }

    /** [sign] — показывать «+» у положительных. Минус всегда длинный: «−». */
    fun format(kopecks: Long, sign: Boolean = false): String {
        val prefix = when {
            kopecks < 0 -> "−"
            sign && kopecks > 0 -> "+"
            else -> ""
        }
        val absolute = abs(kopecks)
        val rubles = groupThousands(absolute / 100)
        val rest = absolute % 100
        val fraction = if (rest == 0L) "" else ",%02d".format(rest)
        return "$prefix$rubles$fraction$NBSP₽"
    }

    /** Коротко для подписей графика: «70 тыс.», «1,2 млн», «950». */
    fun compact(kopecks: Long): String {
        val rubles = kopecks / 100
        val prefix = if (rubles < 0) "−" else ""
        val value = abs(rubles)
        return prefix + when {
            value >= 1_000_000 -> trim(value / 1_000_000.0) + "${NBSP}млн"
            value >= 1_000 -> trim(value / 1_000.0) + "${NBSP}тыс."
            else -> value.toString()
        }
    }

    /** Текст суммы для поля ввода при редактировании: «70000», «99,9». */
    fun forInput(kopecks: Long): String {
        val rest = kopecks % 100
        return if (rest == 0L) (kopecks / 100).toString() else "%d,%02d".format(kopecks / 100, rest)
    }

    private fun trim(value: Double): String {
        val rounded = if (value >= 100) Math.round(value).toDouble() else Math.round(value * 10) / 10.0
        val text = if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
        return text.replace('.', ',')
    }

    private fun groupThousands(value: Long): String {
        val digits = value.toString()
        val sb = StringBuilder()
        digits.forEachIndexed { index, ch ->
            if (index > 0 && (digits.length - index) % 3 == 0) sb.append(NBSP)
            sb.append(ch)
        }
        return sb.toString()
    }
}
