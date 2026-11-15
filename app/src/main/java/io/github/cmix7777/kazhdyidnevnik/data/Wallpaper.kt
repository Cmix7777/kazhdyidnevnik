package io.github.cmix7777.kazhdyidnevnik.data

/**
 * Фон переписки на этом телефоне (у каждого свой, второму он не передаётся).
 * [hue] — поворот оттенка по кругу, 0..1; [power] — яркость; [speed] — скорость движения;
 * [tilt] — отзываться на наклон телефона; [touch] — круги от касаний и волна от новых сообщений.
 */
data class WallpaperChoice(
    val id: String = Wallpapers.SMOKE,
    val hue: Float = 0f,
    val power: Float = 1f,
    val speed: Float = 1f,
    val tilt: Boolean = true,
    val touch: Boolean = true,
)

/**
 * Один фон из списка. [scale] — во сколько раз меньше экрана он считается (потом растягивается):
 * мягким фонам хватает трети, фонам с тонкими линиями нужно больше. [fps] — кадров в секунду.
 */
class WallpaperInfo(val id: String, val title: String, val scale: Float, val fps: Int)

object Wallpapers {
    /** Прежний фон: дымка в цветах двоих. */
    const val SMOKE = "smoke"
    const val NONE = "none"

    const val POWER_MIN = 0.5f
    const val POWER_MAX = 1.5f
    const val SPEED_MIN = 0.25f
    const val SPEED_MAX = 2f

    val ALL: List<WallpaperInfo> = listOf(
        WallpaperInfo(SMOKE, "Дымка", 4f, 15),
        WallpaperInfo("aurora", "Северное сияние", 3f, 30),
        WallpaperInfo("cosmos", "Космос", 2.5f, 24),
        WallpaperInfo("galaxy", "Галактика", 2.5f, 24),
        WallpaperInfo("sea", "Ночное море", 2.5f, 30),
        WallpaperInfo("deep", "Под водой", 3f, 30),
        WallpaperInfo("snow", "Снегопад", 2.5f, 30),
        WallpaperInfo("storm", "Гроза", 2.5f, 30),
        WallpaperInfo("fire", "Костёр", 3f, 30),
        WallpaperInfo("lava", "Лава", 3.5f, 30),
        WallpaperInfo("lights", "Огоньки", 3f, 30),
        WallpaperInfo("hearts", "Сердечки", 2.5f, 30),
        WallpaperInfo("sakura", "Сакура", 2.5f, 30),
        WallpaperInfo("silk", "Шёлк", 3.5f, 24),
        WallpaperInfo("crystal", "Витраж", 2.5f, 24),
        WallpaperInfo("retro", "Ретровейв", 2.2f, 30),
        WallpaperInfo("code", "Код", 2.2f, 30),
        WallpaperInfo(NONE, "Без фона", 1f, 0),
    )

    fun info(id: String): WallpaperInfo = ALL.firstOrNull { it.id == id } ?: ALL.first()

    /** Рисуется ли фон шейдером (дымка и «без фона» — нет). */
    fun live(id: String): Boolean = id != SMOKE && id != NONE && ALL.any { it.id == id }

    /** Привести выбор к допустимому: незнакомый фон — дымка, числа — в свои пределы. */
    fun clean(choice: WallpaperChoice): WallpaperChoice = choice.copy(
        id = if (ALL.any { it.id == choice.id }) choice.id else SMOKE,
        hue = if (choice.hue.isFinite()) ((choice.hue % 1f) + 1f) % 1f else 0f,
        power = if (choice.power.isFinite()) choice.power.coerceIn(POWER_MIN, POWER_MAX) else 1f,
        speed = if (choice.speed.isFinite()) choice.speed.coerceIn(SPEED_MIN, SPEED_MAX) else 1f,
    )

    /**
     * Когда телефон не успевает рисовать фон, он считается грубее: [level] 0 — как задумано, дальше мельче
     * и реже. Возвращает делитель размера и число кадров в секунду.
     */
    fun quality(info: WallpaperInfo, level: Int): Pair<Float, Int> = when (level.coerceIn(0, MAX_LEVEL)) {
        0 -> info.scale to info.fps
        1 -> info.scale * 1.35f to info.fps
        2 -> info.scale * 1.35f to (info.fps * 2 / 3).coerceAtLeast(12)
        else -> info.scale * 1.8f to (info.fps / 2).coerceAtLeast(12)
    }

    const val MAX_LEVEL = 3
}
