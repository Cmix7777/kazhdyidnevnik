package io.github.cmix7777.kazhdyidnevnik.data

import java.time.LocalDate
import java.time.LocalTime

/** Чей дневник: в приложении две страницы — Айзата (слева) и Насти (справа). */
enum class Person(val id: String, val shortName: String, val genitive: String) {
    AIZAT("aizat", "Айзат", "Айзата"),
    NASTYA("nastya", "Настя", "Насти");

    val partner: Person get() = if (this == AIZAT) NASTYA else AIZAT

    /** «с Айзатом», «с Настей». */
    val instrumental: String get() = if (this == AIZAT) "Айзатом" else "Настей"

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
    val leave: LeaveTimes,
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
    /**
     * Постоянные дела, которые один раз попадают в «свои дела» владельца (например, занятия в автошколе).
     * Дальше он меняет и удаляет их сам, обратно они не возвращаются.
     */
    val regularEvents: List<UserEvent> = emptyList(),
)

object Profiles {

    val aizat = Profile(
        morningTitle = "Доброе утро, господин Айзат",
        leave = LeaveTimes.Aizat,
        shifts = WorkSchedule.default,
        deadlines = Deadlines.default,
        hasPlan = true,
        hasPractice = true,
        scheduleSource = "timeo.mveu.ru",
        regularEvents = listOf(AizatRegular.drivingTheory),
    )

    val nastya = Profile(
        morningTitle = "Доброе утро, Анастасия",
        leave = LeaveTimes.Nastya,
        shifts = NastyaWork.shifts,
        deadlines = emptyList(),
        hasPlan = false,
        hasPractice = false,
        scheduleSource = "сайт колледжа УдГУ",
        shiftsAreEditable = true,
    )
}

/** Постоянные дела Айзата вне колледжа и работы. */
object AizatRegular {
    /** Теория по вождению: каждое воскресенье с 15 до 19. */
    val drivingTheory = UserEvent(
        id = "seed-aizat-driving-theory",
        kind = EventKind.DRIVING,
        title = "Теория по вождению",
        date = LocalDate.of(2026, 10, 11),
        start = LocalTime.of(15, 0),
        end = LocalTime.of(19, 0),
        weekly = true,
        remindBefore = 60,
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
 * Пары, смены и дела человека за день. Свои дела есть только на телефоне владельца;
 * дела второго человека учитываются, только если он ими поделился ([eventsKnown]).
 */
fun Person.dayItems(
    date: LocalDate,
    lessons: List<Lesson>,
    isOwner: Boolean,
    events: List<UserEvent> = emptyList(),
    eventsKnown: Boolean = isOwner,
): List<DayItem> {
    // Дела второго человека известны, только если он ими поделился: тогда его смены уже среди них.
    val shifts = if (eventsKnown && profile.shiftsAreEditable) emptyList() else profile.shifts
    return buildDay(date, lessons, shifts, if (eventsKnown) events else emptyList())
}
