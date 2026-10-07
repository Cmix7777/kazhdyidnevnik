package io.github.cmix7777.kazhdyidnevnik.service

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import io.github.cmix7777.kazhdyidnevnik.data.Backup
import io.github.cmix7777.kazhdyidnevnik.data.ProgressRepository
import io.github.cmix7777.kazhdyidnevnik.notify.AppSettings
import io.github.cmix7777.kazhdyidnevnik.notify.ReminderScheduler
import java.time.LocalDateTime

/**
 * Резервные копии прогресса. Раз в день копия сама сохраняется в «Загрузки/Каждыйдневник»:
 * эта папка остаётся, даже если удалить приложение.
 */
object Backups {

    const val FOLDER = "Каждыйдневник"
    private const val FILE_NAME = "kazhdyidnevnik-backup.json"
    private const val AUTO_EVERY_MS = 20 * 60 * 60 * 1000L

    sealed interface RestoreResult {
        data class Done(val added: Int, val total: Int, val settingsRestored: Boolean) : RestoreResult
        data object NotABackup : RestoreResult
        data class Failed(val reason: String) : RestoreResult
    }

    private fun content(context: Context): String = Backup.encode(
        done = ProgressRepository.current(context.filesDir),
        settings = AppSettings(context).reminders,
        createdAt = LocalDateTime.now(),
    )

    /** Сохранить копию в «Загрузки/Каждыйдневник» (перезаписывает прошлую копию этого приложения). */
    fun saveAuto(context: Context): Boolean {
        val settings = AppSettings(context)
        val text = content(context)
        val resolver = context.contentResolver

        val saved = settings.autoBackupUri?.let { Uri.parse(it) }
        if (saved != null && write(context, saved, text)) {
            settings.lastAutoBackupMillis = System.currentTimeMillis()
            return true
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
        }
        val uri = runCatching { resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) }.getOrNull()
            ?: return false
        if (!write(context, uri, text)) return false
        settings.autoBackupUri = uri.toString()
        settings.lastAutoBackupMillis = System.currentTimeMillis()
        return true
    }

    /** Автокопия, если с прошлой прошло больше 20 часов. */
    fun saveAutoIfDue(context: Context) {
        val last = AppSettings(context).lastAutoBackupMillis
        if (System.currentTimeMillis() - last > AUTO_EVERY_MS) saveAuto(context)
    }

    /** Восстановить из файла, выбранного пользователем. Отметки объединяются, ничего не стирается. */
    fun restore(context: Context, uri: Uri): RestoreResult {
        val text: String? = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
        } catch (e: Exception) {
            null
        }
        if (text == null) return RestoreResult.Failed("не получилось открыть файл")

        val backup = Backup.decode(text) ?: return RestoreResult.NotABackup
        val added = ProgressRepository.mergeAll(context.filesDir, backup.done)
        backup.settings?.let { AppSettings(context).reminders = it }
        ReminderScheduler.scheduleNext(context)
        return RestoreResult.Done(added = added, total = backup.done.size, settingsRestored = backup.settings != null)
    }

    private fun write(context: Context, uri: Uri, text: String): Boolean = try {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } != null
    } catch (e: Exception) {
        false
    }
}
