package io.github.cmix7777.kazhdyidnevnik.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.cmix7777.kazhdyidnevnik.data.Deadline
import io.github.cmix7777.kazhdyidnevnik.data.Diary
import io.github.cmix7777.kazhdyidnevnik.data.DiaryRepository
import io.github.cmix7777.kazhdyidnevnik.data.MoneyEntry
import io.github.cmix7777.kazhdyidnevnik.data.PartnerEventsRepository
import io.github.cmix7777.kazhdyidnevnik.data.PartnerShare
import io.github.cmix7777.kazhdyidnevnik.data.Person
import io.github.cmix7777.kazhdyidnevnik.data.SharedEvents
import io.github.cmix7777.kazhdyidnevnik.data.SpecialDates
import io.github.cmix7777.kazhdyidnevnik.data.dayItems
import io.github.cmix7777.kazhdyidnevnik.data.DayItem
import io.github.cmix7777.kazhdyidnevnik.data.Lesson
import io.github.cmix7777.kazhdyidnevnik.data.UserEvent
import io.github.cmix7777.kazhdyidnevnik.notify.ReminderScheduler
import io.github.cmix7777.kazhdyidnevnik.service.Backups
import io.github.cmix7777.kazhdyidnevnik.service.PartnerSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

/** Свои дела, важные даты и бюджет владельца телефона. */
class DiaryViewModel(app: Application) : AndroidViewModel(app) {

    var diary by mutableStateOf(Diary())
        private set

    /** Дела второго человека, которыми он поделился (null — ещё нет). */
    var partnerShare by mutableStateOf<SharedEvents?>(null)
        private set

    init {
        DiaryRepository.init(app.filesDir)
        PartnerEventsRepository.init(app.filesDir)
        viewModelScope.launch {
            DiaryRepository.state.collect { diary = it }
        }
        viewModelScope.launch {
            PartnerEventsRepository.state.collect { partnerShare = it }
        }
        // Если телефоны связаны, при запуске сразу обмениваемся делами.
        PartnerSync.load(app)
        PartnerSync.syncNow(app, force = false)
    }

    /** Обменяться делами со вторым телефоном сейчас (если телефоны связаны). */
    fun syncPartner(force: Boolean = true) = PartnerSync.syncNow(getApplication<Application>(), force)

    /** Пары, смены и дела [person] за день: свои дела — из дневника, чужие — если ими поделились. */
    fun dayItems(person: Person, isOwner: Boolean, date: LocalDate, lessons: List<Lesson>): List<DayItem> {
        val shared = partnerShare?.takeIf { it.from == person }
        val events = if (isOwner) diary.events else shared?.events.orEmpty()
        return person.dayItems(date, lessons, isOwner, events, eventsKnown = isOwner || shared != null)
    }

    /** Особые даты обоих: свои и те, которыми поделился второй человек. */
    fun specialDates(): List<Deadline> = SpecialDates.merge(diary.deadlines, partnerShare?.dates.orEmpty())

    /** Сообщение с моими делами, чтобы отправить второму человеку. */
    fun shareMessage(owner: Person): String {
        val shared = SharedEvents(
            owner,
            System.currentTimeMillis(),
            PartnerShare.relevant(diary.events, LocalDate.now()),
            PartnerShare.relevantDates(diary.deadlines),
        )
        return PartnerShare.message(owner, shared)
    }

    /** Результат вставки чужих дел. */
    enum class ImportResult { Done, NoCode, OwnCode }

    /** Вставить дела второго человека из скопированного сообщения. */
    fun importPartner(text: String, owner: Person): ImportResult {
        val shared = PartnerShare.decode(text) ?: return ImportResult.NoCode
        if (shared.from == owner) return ImportResult.OwnCode
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            PartnerEventsRepository.save(app.filesDir, shared)
            ReminderScheduler.scheduleNext(app)
        }
        return ImportResult.Done
    }

    /** Перенести смены и постоянные дела владельца из профиля в его дела (один раз). */
    fun seedFor(owner: Person) = change { it.withSeeds(owner) }

    fun saveEvent(event: UserEvent) = change { it.withEvent(event) }

    fun deleteEvent(id: String) = change { it.withoutEvent(id) }

    /** Убрать повторяющееся дело только в один день. */
    fun skipDay(event: UserEvent, date: LocalDate) = change { it.withEvent(event.copy(skipped = event.skipped + date)) }

    fun saveDeadline(deadline: Deadline) = change { it.withDeadline(deadline) }

    fun deleteDeadline(id: String) = change { it.withoutDeadline(id) }

    fun saveMoney(entry: MoneyEntry) = change { it.withMoney(entry) }

    fun deleteMoney(id: String) = change { it.withoutMoney(id) }

    private fun change(block: (Diary) -> Diary) {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            DiaryRepository.update(app.filesDir, block)
            ReminderScheduler.scheduleNext(app)
            runCatching { Backups.saveAfterChange(app) }
            // Свои дела изменились: через пару секунд они уйдут второму телефону.
            PartnerSync.syncSoon(app)
        }
    }

    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}
