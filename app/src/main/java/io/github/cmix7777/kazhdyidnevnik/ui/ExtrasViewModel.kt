package io.github.cmix7777.kazhdyidnevnik.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.cmix7777.kazhdyidnevnik.data.DayWeather
import io.github.cmix7777.kazhdyidnevnik.data.Release
import io.github.cmix7777.kazhdyidnevnik.data.plural
import io.github.cmix7777.kazhdyidnevnik.notify.AppSettings
import io.github.cmix7777.kazhdyidnevnik.service.Backups
import io.github.cmix7777.kazhdyidnevnik.service.Updates
import io.github.cmix7777.kazhdyidnevnik.service.WeatherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.HttpStatusException
import java.io.File
import java.io.IOException

/** Состояние обновления приложения. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val release: Release, val progress: Float) : UpdateState
    data class NeedsPermission(val release: Release, val file: File) : UpdateState
    data class Ready(val release: Release, val file: File) : UpdateState
    data class Failed(val message: String, val release: Release?) : UpdateState
}

/** Новая версия, о которой сейчас идёт речь (или null). */
val UpdateState.release: Release?
    get() = when (this) {
        is UpdateState.Available -> release
        is UpdateState.Downloading -> release
        is UpdateState.NeedsPermission -> release
        is UpdateState.Ready -> release
        is UpdateState.Failed -> release
        else -> null
    }

/** Обновления, погода и резервные копии. */
class ExtrasViewModel(private val app: Application) : AndroidViewModel(app) {

    private val settings = AppSettings(app)

    var update by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set

    var lastUpdateCheckMillis by mutableLongStateOf(settings.lastUpdateCheckMillis)
        private set

    /** Прогноз на ближайшие дни. */
    var weather by mutableStateOf<List<DayWeather>>(emptyList())
        private set

    var lastBackupMillis by mutableLongStateOf(settings.lastAutoBackupMillis)
        private set

    var backupMessage by mutableStateOf<String?>(null)
        private set

    init {
        Updates.cleanup(app)
        settings.knownRelease?.takeIf { Updates.isNewer(it) }?.let { update = UpdateState.Available(it) }
        if (System.currentTimeMillis() - settings.lastUpdateCheckMillis > AUTO_CHECK_MS) checkUpdate(manual = false)

        WeatherRepository.cached(app)?.let { weather = it.second }
        if (WeatherRepository.isStale(app)) refreshWeather()

        viewModelScope.launch {
            withContext(Dispatchers.IO) { Backups.saveAutoIfDue(app) }
            lastBackupMillis = settings.lastAutoBackupMillis
        }
    }

    fun checkUpdate(manual: Boolean = true) {
        val current = update
        if (current is UpdateState.Checking || current is UpdateState.Downloading) return
        if (manual) update = UpdateState.Checking
        viewModelScope.launch {
            update = try {
                val release = Updates.latest(app)
                if (Updates.isNewer(release)) UpdateState.Available(release) else UpdateState.UpToDate
            } catch (e: Exception) {
                if (manual) UpdateState.Failed(describe(e), null) else current
            }
            lastUpdateCheckMillis = settings.lastUpdateCheckMillis
        }
    }

    /** Скачать новую версию и открыть установщик. */
    fun downloadAndInstall() {
        val release = update.release ?: return
        if (update is UpdateState.Downloading) return
        update = UpdateState.Downloading(release, 0f)
        viewModelScope.launch {
            try {
                val file = Updates.download(app, release) { progress ->
                    update = UpdateState.Downloading(release, progress)
                }
                install(release, file)
            } catch (e: Exception) {
                update = UpdateState.Failed(describe(e), release)
            }
        }
    }

    /** Повторно открыть установщик (после разрешения или если окно закрыли). */
    fun installAgain() {
        when (val state = update) {
            is UpdateState.NeedsPermission -> install(state.release, state.file)
            is UpdateState.Ready -> install(state.release, state.file)
            else -> Unit
        }
    }

    fun openInstallPermission() = Updates.openInstallPermission(app)

    private fun install(release: Release, file: File) {
        if (!Updates.canInstall(app)) {
            update = UpdateState.NeedsPermission(release, file)
            return
        }
        update = UpdateState.Ready(release, file)
        try {
            Updates.install(app, file)
        } catch (e: Exception) {
            update = UpdateState.Failed("не открылся установщик", release)
        }
    }

    fun refreshWeather() {
        viewModelScope.launch {
            runCatching { WeatherRepository.fetch(app) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { weather = it }
        }
    }

    fun saveBackupNow() {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { Backups.saveAuto(app) }
            lastBackupMillis = settings.lastAutoBackupMillis
            backupMessage = if (ok) {
                "Копия сохранена в «Загрузки/${Backups.FOLDER}»."
            } else {
                "Не получилось сохранить копию."
            }
        }
    }

    /** Восстановить из выбранного файла. [onSettingsRestored] — перечитать настройки уведомлений. */
    fun restore(uri: Uri, onSettingsRestored: () -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { Backups.restore(app, uri) }
            backupMessage = when (result) {
                is Backups.RestoreResult.Done -> {
                    if (result.settingsRestored) onSettingsRestored()
                    val marks = plural(result.total.toLong(), "отметка", "отметки", "отметок")
                    "Готово: в копии ${result.total} $marks, новых из них ${result.added}." +
                        if (result.settingsRestored) " Настройки уведомлений тоже восстановлены." else ""
                }
                Backups.RestoreResult.NotABackup -> "Это не копия Каждыйдневника. Нужен файл kazhdyidnevnik-backup.json."
                is Backups.RestoreResult.Failed -> "Не получилось: ${result.reason}."
            }
        }
    }

    private fun describe(e: Exception): String = when (e) {
        is HttpStatusException -> "GitHub ответил ошибкой ${e.statusCode}"
        is IOException -> "нет связи с GitHub"
        else -> e.javaClass.simpleName
    }

    private companion object {
        const val AUTO_CHECK_MS = 6 * 60 * 60 * 1000L
    }
}
