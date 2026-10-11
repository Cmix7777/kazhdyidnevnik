package io.github.cmix7777.kazhdyidnevnik.data

import java.time.LocalDate
import java.time.LocalTime

/**
 * Срочная подготовка к проверке по тестированию 12 октября 2026: в обычной программе
 * по этому предмету первая тема только начинается, а проверка уже завтра.
 * Накануне вечером и утром в день проверки блок «Тестирование» заменяется экспресс-повторением базы.
 */
object ExamPrep {

    /** День проверки по предмету «Тестирование информационных систем». */
    val testingCheck: LocalDate = LocalDate.of(2026, 10, 12)

    /**
     * Один этап срочной подготовки: [number] из [of]. [focus] — темы, которые берём сейчас,
     * [review] — темы с прошлого этапа, которые надо быстро повторить (или пройти, если вечером не успел).
     */
    data class Step(
        val number: Int,
        val of: Int,
        val focus: List<Topic>,
        val review: List<Topic>,
        val desired: Int,
        val minimum: Int,
        val onExamDay: Boolean,
    ) {
        val topics: List<Topic> get() = focus + review
    }

    /** Этап срочной подготовки на [date] или null, если сегодня обычный день. */
    fun step(date: LocalDate): Step? = when (date) {
        testingCheck.minusDays(1) -> Step(
            number = 1, of = 2,
            focus = Curriculum.qa.subList(0, 3), review = emptyList(),
            desired = 100, minimum = 45, onExamDay = false,
        )
        testingCheck -> Step(
            number = 2, of = 2,
            focus = Curriculum.qa.subList(3, 6), review = Curriculum.qa.subList(0, 3),
            desired = 70, minimum = 30, onExamDay = true,
        )
        else -> null
    }

    /** Блок плана для этапа [step] со временем [start]–[end]. */
    fun block(step: Step, start: LocalTime, end: LocalTime): PlanBlock {
        val focus = step.focus.joinToString("; ") { it.title }
        val review = step.review.joinToString("; ") { it.title }
        val head = if (step.onExamDay) "Проверка по тестированию сегодня" else "Проверка по тестированию завтра"
        val detail = buildString {
            append("$head. ")
            append(if (step.onExamDay) "Сначала: " else "Сегодня база: ")
            append(focus).append(". ")
            if (review.isNotEmpty()) {
                append("Потом быстро повтори (а если вечером не успел, пройди заново): ").append(review).append(". ")
            }
            append("По каждой теме прочитай суть, скажи её вслух своими словами и ответь на пару вопросов.")
        }
        val prompt = buildString {
            append(if (step.onExamDay) "Сегодня" else "Завтра")
            append(" у меня проверка по предмету «Тестирование информационных систем» (4 курс колледжа). ")
            append("Раньше я по нему почти не готовился, начинаю с нуля. ")
            append("Проведи со мной экспресс-подготовку по темам: $focus. ")
            if (review.isNotEmpty()) {
                append("Потом быстро повтори со мной (по одному вопросу на тему): $review. ")
            }
            append("По каждой теме: сначала объясни суть простыми словами за 2–3 минуты с примером на сайте BookClub, ")
            append("потом задай мне 3 вопроса, как на проверке, по одному, и после каждого моего ответа скажи, верно ли. ")
            append("В конце дай шпаргалку: по 3–4 пункта на каждую тему.")
        }
        return PlanBlock(
            kind = PlanKind.QA,
            start = start,
            end = end,
            title = "Срочно к проверке по тестированию (${step.number}/${step.of})",
            detail = detail,
            links = step.topics.flatMap { it.links },
            prompt = prompt,
        )
    }
}
