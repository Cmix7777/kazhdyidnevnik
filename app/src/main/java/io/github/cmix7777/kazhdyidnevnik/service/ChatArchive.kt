package io.github.cmix7777.kazhdyidnevnik.service

import android.content.Context
import android.net.Uri
import io.github.cmix7777.kazhdyidnevnik.data.ChatLogic
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Архив переписки одним файлом (zip): сообщения и все фото, голосовые, кружочки и стикеры с телефона.
 * Облако для вложений — почтовый ящик (после доставки они там стираются), поэтому насовсем их хранит
 * только телефон и этот архив. Из архива возвращаются вложения, которых на телефоне нет; сами
 * сообщения и так восстанавливаются из облака.
 */
object ChatArchive {

    class Summary(val files: Int, val bytes: Long)

    fun export(context: Context, target: Uri): Summary? = runCatching {
        val app = context.applicationContext
        var count = 0
        var bytes = 0L
        app.contentResolver.openOutputStream(target)?.use { raw ->
            ZipOutputStream(raw.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("chat.json"))
                zip.write(ChatLogic.encodeState(ChatSync.state.value).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                for (file in ChatFiles.all(app)) {
                    zip.putNextEntry(ZipEntry("files/" + file.name))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    count++
                    bytes += file.length()
                }
                for (file in StickerStore.list(app)) {
                    zip.putNextEntry(ZipEntry("stickers/" + file.name))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        } ?: return@runCatching null
        Summary(count, bytes)
    }.getOrNull()

    /** Вернуть из архива вложения и стикеры, которых нет на телефоне. Возвращает, сколько вернулось. */
    fun import(context: Context, source: Uri): Int? = runCatching {
        val app = context.applicationContext
        var restored = 0
        app.contentResolver.openInputStream(source)?.use { raw ->
            ZipInputStream(raw.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name.substringAfterLast('/')
                    // Только плоские имена из своих папок: никаких путей наружу.
                    val safe = name.isNotEmpty() && !name.contains("..") && name.matches(Regex("[A-Za-z0-9._-]+"))
                    when {
                        !safe || entry.isDirectory -> Unit
                        entry.name.startsWith("files/") && name.endsWith(".jpg") -> {
                            val id = name.removeSuffix(".jpg")
                            if (!ChatFiles.exists(app, id)) {
                                ChatFiles.save(app, id, zip.readBytes())
                                File(ChatFiles.file(app, id).parentFile, "$id.gone").delete()
                                restored++
                            }
                        }
                        entry.name.startsWith("stickers/") -> {
                            if (StickerStore.list(app).none { it.name == name }) {
                                StickerStore.addNamed(app, name, zip.readBytes())
                                restored++
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
        } ?: return@runCatching null
        restored
    }.getOrNull()
}
