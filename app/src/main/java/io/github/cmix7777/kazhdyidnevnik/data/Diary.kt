package io.github.cmix7777.kazhdyidnevnik.data

/**
 * То, что человек сам ведёт в приложении на своём телефоне: свои дела, важные даты и бюджет.
 * [seeded] — какие начальные данные уже перенесены (например, смены из профиля).
 */
data class Diary(
    val events: List<UserEvent> = emptyList(),
    val deadlines: List<Deadline> = emptyList(),
    val money: List<MoneyEntry> = emptyList(),
    val seeded: Set<String> = emptySet(),
) {
    /** Добавить из [other] то, чего здесь ещё нет (по номеру записи). Ничего не удаляет. */
    fun mergeWith(other: Diary): Diary {
        val eventIds = events.map { it.id }.toSet()
        val deadlineIds = deadlines.mapNotNull { it.id }.toSet()
        val moneyIds = money.map { it.id }.toSet()
        return Diary(
            events = events + other.events.filter { it.id !in eventIds },
            deadlines = deadlines + other.deadlines.filter { deadline ->
                val id = deadline.id
                id != null && id !in deadlineIds
            },
            money = money + other.money.filter { it.id !in moneyIds },
            seeded = seeded + other.seeded,
        )
    }

    /** Сколько записей всего. */
    val size: Int get() = events.size + deadlines.size + money.size

    /** Заменить или добавить дело. */
    fun withEvent(event: UserEvent): Diary =
        copy(events = events.filter { it.id != event.id } + event)

    fun withoutEvent(id: String): Diary = copy(events = events.filter { it.id != id })

    fun withDeadline(deadline: Deadline): Diary =
        copy(deadlines = deadlines.filter { it.id != deadline.id } + deadline)

    fun withoutDeadline(id: String): Diary = copy(deadlines = deadlines.filter { it.id != id })

    fun withMoney(entry: MoneyEntry): Diary = copy(money = money.filter { it.id != entry.id } + entry)

    fun withoutMoney(id: String): Diary = copy(money = money.filter { it.id != id })

    /** Перенести разовые смены из профиля в свои дела (один раз для человека). */
    fun seededWithShifts(person: Person): Diary {
        val key = "shifts-${person.id}"
        if (!person.profile.shiftsAreEditable || key in seeded) return this
        val shifts = person.profile.shifts.mapIndexedNotNull { index, shift -> shift.toEvent("seed-${person.id}-$index") }
        return copy(events = events + shifts.filter { seed -> events.none { it.id == seed.id } }, seeded = seeded + key)
    }
}
