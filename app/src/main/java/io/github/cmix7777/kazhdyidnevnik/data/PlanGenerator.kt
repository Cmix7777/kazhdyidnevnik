package io.github.cmix7777.kazhdyidnevnik.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

enum class PlanKind { PDD, CSHARP, QA, LARAVEL, MOCK }

enum class DayLoad { HEAVY, NORMAL, FREE }

/** Блок учёбы в плане дня. */
data class PlanBlock(
    val kind: PlanKind,
    val start: LocalTime,
    val end: LocalTime,
    val title: String,
    val detail: String,
    val links: List<Link> = emptyList(),
    val prompt: String,
    val mockTask: MockTask? = null,
) {
    val minutes: Int get() = (end.toSecondOfDay() - start.toSecondOfDay()) / 60

    fun key(date: LocalDate): String = progressKey(date, kind)
}

fun progressKey(date: LocalDate, kind: PlanKind): String = "$date|${kind.name}"

/**
 * Строит план учёбы на день: 2–3 часа в обычный день, меньше в тяжёлый, больше в свободный.
 * Блоки ставятся только в свободные окна между парами и сменами с запасом на дорогу.
 */
object PlanGenerator {

    /** С этого дня приложение показывает план учёбы и напоминает о нём. */
    val firstDay: LocalDate = LocalDate.of(2026, 10, 8)

    /** План на день с учётом [firstDay]: до него план пустой. */
    fun planFor(date: LocalDate, items: List<DayItem>): List<PlanBlock> =
        if (date.isBefore(firstDay)) emptyList() else plan(date, items)

    private const val DAY_START = 8 * 60 + 30
    private const val DAY_END = 23 * 60
    private const val GAP = 10
    private const val MIN_WINDOW = 15

    data class Window(val start: Int, val end: Int) {
        val length: Int get() = end - start
    }

    private data class Request(val kind: PlanKind, val desired: Int, val minimum: Int)

    /** Занятое время дня в минутах от полуночи, с запасом на дорогу. */
    fun busyIntervals(date: LocalDate, items: List<DayItem>): List<Window> {
        val busy = items.map { item ->
            val online = item is DayItem.LessonItem && item.lesson.online
            val before = if (online) 10 else 40
            val after = if (online) 10 else 30
            Window(minutes(item.start) - before, minutes(item.end) + after)
        }.toMutableList()
        // Во время практики по будням считаем занятым время с 8:20 до 17:30.
        if (Practice.dayNumber(date) != null && date.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) {
            busy += Window(8 * 60 + 20, 17 * 60 + 30)
        }
        return merge(busy)
    }

    fun freeWindows(date: LocalDate, items: List<DayItem>): List<Window> {
        val result = mutableListOf<Window>()
        var cursor = DAY_START
        for (b in busyIntervals(date, items)) {
            if (b.end <= cursor) continue
            if (b.start > cursor) result += Window(cursor, minOf(b.start, DAY_END))
            cursor = maxOf(cursor, b.end)
            if (cursor >= DAY_END) break
        }
        if (cursor < DAY_END) result += Window(cursor, DAY_END)
        return result.filter { it.length >= MIN_WINDOW }
    }

    fun load(date: LocalDate, items: List<DayItem>): DayLoad {
        val free = freeWindows(date, items).sumOf { it.length }
        val busy = items.sumOf { minutes(it.end) - minutes(it.start) } +
            if (Practice.dayNumber(date) != null && date.dayOfWeek.value <= 5) 8 * 60 else 0
        return when {
            busy >= 9 * 60 || free < 150 -> DayLoad.HEAVY
            free >= 420 -> DayLoad.FREE
            else -> DayLoad.NORMAL
        }
    }

    fun plan(date: LocalDate, items: List<DayItem>): List<PlanBlock> {
        val windows = freeWindows(date, items).toMutableList()
        val csharp = Curriculum.csharpDue(date)
        val requests = mutableListOf<Request>()

        if (MockExams.isMockDay(date)) {
            requests += Request(PlanKind.PDD, 25, 15)
            requests += Request(PlanKind.MOCK, MockExams.MINUTES, 180)
        } else {
            when (load(date, items)) {
                DayLoad.HEAVY -> {
                    requests += Request(PlanKind.PDD, 20, 15)
                    if (csharp) requests += Request(PlanKind.CSHARP, 20, 15)
                    val main = if (date.toEpochDay() % 2 == 0L) PlanKind.QA else PlanKind.LARAVEL
                    requests += Request(main, 45, 30)
                }
                DayLoad.NORMAL -> {
                    requests += Request(PlanKind.PDD, 25, 15)
                    if (csharp) requests += Request(PlanKind.CSHARP, 25, 20)
                    requests += Request(PlanKind.QA, 40, 30)
                    requests += Request(PlanKind.LARAVEL, 50, 30)
                }
                DayLoad.FREE -> {
                    requests += Request(PlanKind.PDD, 30, 20)
                    if (csharp) requests += Request(PlanKind.CSHARP, 30, 20)
                    requests += Request(PlanKind.QA, 45, 30)
                    requests += Request(PlanKind.LARAVEL, 75, 45)
                }
            }
        }

        val blocks = mutableListOf<PlanBlock>()
        for (request in requests) {
            val index = windows.indexOfFirst { it.length >= request.desired }
                .takeIf { it >= 0 }
                ?: windows.indexOfFirst { it.length >= request.minimum }.takeIf { it >= 0 }
                ?: continue
            val window = windows[index]
            val duration = minOf(request.desired, window.length)
            val start = window.start
            val end = start + duration
            blocks += describe(date, request.kind, time(start), time(end))
            val rest = Window(end + GAP, window.end)
            if (rest.length >= MIN_WINDOW) windows[index] = rest else windows.removeAt(index)
        }
        return blocks.sortedBy { it.start }
    }

    private fun describe(date: LocalDate, kind: PlanKind, start: LocalTime, end: LocalTime): PlanBlock {
        val step = Curriculum.step(date)
        return when (kind) {
            PlanKind.PDD -> PlanBlock(
                kind, start, end,
                title = "Билеты ПДД",
                detail = "Реши 2 билета, ошибки разбери сразу.",
                prompt = "Я готовлюсь к теоретическому экзамену ПДД категории B. Задай мне 10 вопросов " +
                    "по правилам дорожного движения по одному. После каждого моего ответа скажи, верно ли, " +
                    "и коротко объясни правило.",
            )
            PlanKind.CSHARP -> {
                val task = Curriculum.csharpTask(date)
                PlanBlock(
                    kind, start, end,
                    title = "Сдача сайта на C#",
                    detail = "$task.",
                    prompt = "Помоги подготовиться к сдаче моего сайта BookClub (ASP.NET Core Web API, " +
                        "PostgreSQL, EF Core) преподавателю. Задача на сегодня: $task. Задай мне вопросы, " +
                        "которые может задать преподаватель, и помоги сформулировать ответы.",
                )
            }
            PlanKind.QA -> {
                val (topic, repeat) = Curriculum.qaTopic(date)
                PlanBlock(
                    kind, start, end,
                    title = (if (repeat) "Повторение: " else "Тестирование: ") + topic.title,
                    detail = "$step. ${topic.practice}",
                    links = topic.links,
                    prompt = "Объясни мне с нуля тему «${topic.title}» из тестирования ПО. Я студент 4 курса " +
                        "и учусь на тестировщика. Сегодня у меня: ${step.lowercase()}. Дай коротко теорию с " +
                        "примерами и маленькое упражнение на 15 минут, потом проверь мой ответ.",
                )
            }
            PlanKind.LARAVEL -> {
                val (topic, repeat) = Curriculum.laravelTopic(date)
                PlanBlock(
                    kind, start, end,
                    title = (if (repeat) "Повторение: " else "Laravel: ") + topic.title,
                    detail = "$step. ${topic.practice}",
                    links = topic.links,
                    prompt = "Я готовлюсь к демоэкзамену 09.02.07: сайт на Laravel с Breeze и Bootstrap за 4 часа, " +
                        "документация только в Zeal. Объясни тему «${topic.title}» с нуля и с примерами кода. " +
                        "Сегодня у меня: ${step.lowercase()}. Дай упражнение на 20 минут и проверь мой код.",
                )
            }
            PlanKind.MOCK -> {
                val task = MockExams.taskFor(date)
                PlanBlock(
                    kind, start, end,
                    title = "Пробный демоэкзамен",
                    detail = "ТЗ: ${task.title}. Засеки время, пользуйся только Zeal.",
                    prompt = "Я сделал пробный демоэкзамен по учебному ТЗ «${task.title}» на Laravel, Breeze и " +
                        "Bootstrap. Проверь мой результат: я пришлю код и описание. Оцени по модулям и скажи, " +
                        "что улучшить к настоящему экзамену.",
                    mockTask = task,
                )
            }
        }
    }

    private fun merge(intervals: List<Window>): List<Window> {
        val sorted = intervals.sortedBy { it.start }
        val result = mutableListOf<Window>()
        for (w in sorted) {
            val last = result.lastOrNull()
            if (last != null && w.start <= last.end) {
                result[result.size - 1] = Window(last.start, maxOf(last.end, w.end))
            } else {
                result += w
            }
        }
        return result
    }

    private fun minutes(time: LocalTime): Int = time.hour * 60 + time.minute

    private fun time(minutes: Int): LocalTime = LocalTime.of(minutes / 60, minutes % 60)
}
