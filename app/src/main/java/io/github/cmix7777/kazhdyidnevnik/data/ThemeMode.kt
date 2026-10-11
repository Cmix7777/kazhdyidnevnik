package io.github.cmix7777.kazhdyidnevnik.data

/** Оформление приложения: тёмное, светлое или как на телефоне. */
enum class ThemeMode(val id: String, val title: String) {
    DARK("dark", "Тёмная"),
    LIGHT("light", "Светлая"),
    SYSTEM("system", "Как в системе");

    /** Тёмная ли тема сейчас; [systemDark] — включена ли тёмная тема в самом телефоне. */
    fun isDark(systemDark: Boolean): Boolean = when (this) {
        DARK -> true
        LIGHT -> false
        SYSTEM -> systemDark
    }

    companion object {
        /** Читает сохранённое значение; по умолчанию приложение тёмное, как и раньше. */
        fun fromId(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: DARK
    }
}
