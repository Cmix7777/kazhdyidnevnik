package io.github.cmix7777.kazhdyidnevnik.data

import java.time.LocalDate
import java.time.LocalTime

/** Чей дневник: в приложении две страницы — Айзата (слева) и Насти (справа). */
enum class Person(val id: String, val shortName: String, val genitive: String) {
    AIZAT("aizat", "Айзат", "Айзата"),
    NASTYA("nastya", "Настя", "Насти");

    val partner: Person get() = if (this == AIZAT) NASTYA else AIZAT

    val profile: Profile get() = if (this == AIZAT) Profiles.aizat else Profiles.nastya

    companion object {
        fun fromId(id: String?): Person? = entries.firstOrNull { it.id == id }
    }
}

/** Что у человека в дневнике и как ему подсказывать. */
data class Profile(
    /** Заголовок утренней сводки. */
    val morningTitle: String,
    /** За сколько минут до первой пары или смены выходить из дома. */
    val leaveBeforeMinutes: Long,
    val shifts: List<WorkShift>,
    val deadlines: List<Deadline>,
    /** Есть ли план учёбы с галочками. */
    val hasPlan: Boolean,
    /** Показывать ли дни практики. */
    val hasPractice: Boolean,
    /** Откуда берётся расписание (для подписей). */
    val scheduleSource: String,
    /**
     * Смены из [shifts] на телефоне владельца превращаются в свои дела, и дальше он сам их меняет.
     * На телефоне второго человека по-прежнему видны [shifts].
     */
    val shiftsAreEditable: Boolean = false,
)

object Profiles {

    val aizat = Profile(
        morningTitle = "Доброе утро, господин Айзат",
        leaveBeforeMinutes = 40,
        shifts = WorkSchedule.default,
        deadlines = Deadlines.default,
        hasPlan = true,
        hasPractice = true,
        scheduleSource = "timeo.mveu.ru",
    )

    val nastya = Profile(
        morningTitle = "Доброе утро, Анастасия",
        leaveBeforeMinutes = 50,
        shifts = NastyaWork.shifts,
        deadlines = emptyList(),
        hasPlan = false,
        hasPractice = false,
        scheduleSource = "сайт колледжа УдГУ",
        shiftsAreEditable = true,
    )
}

/** Смены Насти: постоянного графика нет, выходит, когда просят. */
object NastyaWork {
    val shifts: List<WorkShift> = listOf(
        WorkShift.on(LocalDate.of(2026, 10, 9), LocalTime.of(16, 30), LocalTime.of(21, 30)),
        WorkShift.on(LocalDate.of(2026, 10, 10), LocalTime.of(9, 0), LocalTime.of(12, 30)),
        WorkShift.on(LocalDate.of(2026, 10, 10), LocalTime.of(18, 0), LocalTime.of(21, 30)),
        WorkShift.on(LocalDate.of(2026, 10, 11), LocalTime.of(9, 0), LocalTime.of(21, 0)),
    )
}

/**
 * Пары, смены и дела человека за день. Свои дела есть только на телефоне владельца,
 * поэтому [events] учитываются, только если [isOwner].
 */
fun Person.dayItems(
    date: LocalDate,
    lessons: List<Lesson>,
    isOwner: Boolean,
    events: List<UserEvent> = emptyList(),
): List<DayItem> {
    val shifts = if (isOwner && profile.shiftsAreEditable) emptyList() else profile.shifts
    return buildDay(date, lessons, shifts, if (isOwner) events else emptyList())
}
