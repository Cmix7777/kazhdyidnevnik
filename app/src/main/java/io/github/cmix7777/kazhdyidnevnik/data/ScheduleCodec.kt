package io.github.cmix7777.kazhdyidnevnik.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalTime

/** Сохранение недели в JSON для кэша на телефоне. */
object ScheduleCodec {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class LessonDto(
        val date: String,
        val number: Int? = null,
        val start: String,
        val end: String,
        val subject: String,
        val topic: String = "",
        val teacher: String = "",
        val room: String = "",
        val online: Boolean = false,
    )

    @Serializable
    private data class WeekDto(
        val weekStart: String,
        val fetchedAt: Long,
        val lessons: List<LessonDto>,
    )

    fun encode(week: WeekSchedule): String = json.encodeToString(
        WeekDto.serializer(),
        WeekDto(
            weekStart = week.weekStart.toString(),
            fetchedAt = week.fetchedAtMillis,
            lessons = week.lessons.map {
                LessonDto(
                    date = it.date.toString(),
                    number = it.number,
                    start = it.start.toString(),
                    end = it.end.toString(),
                    subject = it.subject,
                    topic = it.topic,
                    teacher = it.teacher,
                    room = it.room,
                    online = it.online,
                )
            },
        ),
    )

    fun decode(text: String): WeekSchedule {
        val dto = json.decodeFromString(WeekDto.serializer(), text)
        return WeekSchedule(
            weekStart = LocalDate.parse(dto.weekStart),
            fetchedAtMillis = dto.fetchedAt,
            lessons = dto.lessons.map {
                Lesson(
                    date = LocalDate.parse(it.date),
                    number = it.number,
                    start = LocalTime.parse(it.start),
                    end = LocalTime.parse(it.end),
                    subject = it.subject,
                    topic = it.topic,
                    teacher = it.teacher,
                    room = it.room,
                    online = it.online,
                )
            },
        )
    }
}
