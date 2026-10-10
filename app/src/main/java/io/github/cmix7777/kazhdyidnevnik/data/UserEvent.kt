package io.github.cmix7777.kazhdyidnevnik.data

import java.time.LocalDate
import java.time.LocalTime

/** Вид своего дела. */
enum class EventKind(val title: String) {
    WORK("Работа"),
    DRIVING("Вождение"),
    STUDY("Учёба"),
    OTHER("Другое"),
}

/**
 * Своё дело, которое человек добавил в приложении: смена, вождение и т. п.
 * Разовое — в дату [date]; повторяющееся ([weekly]) — каждую неделю в тот же день, начиная с [date].
 * [skipped] — даты, в которые повторяющееся дело убрали. [remindBefore] — за сколько минут напомнить.
 */
data class UserEvent(
    val id: String,
    val kind: EventKind,
    val title: String,
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val weekly: Boolean = false,
    val skipped: Set<LocalDate> = emptySet(),
    val remindBefore: Int? = null,
    val note: String = "",
) {
    /** Название для показа: своё или название вида. */
    val displayTitle: String get() = title.trim().ifEmpty { kind.title }

    fun occursOn(target: LocalDate): Boolean = when {
        target in skipped -> false
        weekly -> !target.isBefore(date) && target.dayOfWeek == date.dayOfWeek
        else -> target == date
    }

    companion object {
        /** Варианты напоминания в минутах (null — без напоминания). */
        val reminderChoices: List<Int?> = listOf(null, 15, 30, 60, 120)

        fun reminderText(minutes: Int?): String = when (minutes) {
            null -> "Нет"
            60 -> "За час"
            120 -> "За 2 часа"
            else -> "За $minutes мин"
        }
    }
}

/** Разовые смены из профиля превращаются в свои дела, чтобы их можно было менять. */
fun WorkShift.toEvent(id: String): UserEvent? {
    val day = date ?: return null
    return UserEvent(id = id, kind = EventKind.WORK, title = "", date = day, start = start, end = end)
}
