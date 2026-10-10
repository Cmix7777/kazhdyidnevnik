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
import io.github.cmix7777.kazhdyidnevnik.data.Person
import io.github.cmix7777.kazhdyidnevnik.data.UserEvent
import io.github.cmix7777.kazhdyidnevnik.notify.ReminderScheduler
import io.github.cmix7777.kazhdyidnevnik.service.Backups
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

/** Свои дела, важные даты и бюджет владельца телефона. */
class DiaryViewModel(app: Application) : AndroidViewModel(app) {

    var diary by mutableStateOf(Diary())
        private set

    init {
        DiaryRepository.init(app.filesDir)
        viewModelScope.launch {
            DiaryRepository.state.collect { diary = it }
        }
    }

    /** Перенести смены владельца из профиля в его дела (один раз). */
    fun seedFor(owner: Person) = change { it.seededWithShifts(owner) }

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
        }
    }

    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}
