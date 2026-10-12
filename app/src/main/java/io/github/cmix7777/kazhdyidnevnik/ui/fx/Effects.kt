package io.github.cmix7777.kazhdyidnevnik.ui.fx

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.cmix7777.kazhdyidnevnik.notify.AppSettings

/**
 * Живые эффекты (склейка недель и другие). Хранится выбор «меньше анимаций», а здесь он лежит как состояние,
 * чтобы переключатель в настройках действовал сразу.
 */
object EffectsSettings {
    var reduced by mutableStateOf(false)
        private set

    fun load(settings: AppSettings) {
        reduced = settings.reduceMotion
    }

    fun choose(settings: AppSettings, value: Boolean) {
        settings.reduceMotion = value
        reduced = value
    }

    /** Шейдеры AGSL есть начиная с Android 13. На старых телефонах эффекты рисуются проще. */
    val shadersSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** В телефоне анимации выключены совсем (спец. возможности или настройки разработчика). */
    fun systemAnimationsOff(context: Context): Boolean =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

    /** Показывать ли эффекты с движением. */
    fun animate(context: Context): Boolean = !reduced && !systemAnimationsOff(context)
}

/** Вибрации эффектов. Если телефон не умеет тонкие щелчки, играет похожая стандартная вибрация. */
object Haptics {

    /** Склейка: мягкий двойной «чпок». */
    fun glue(context: Context) = play(context) { vibrator ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            vibrator.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_CLICK,
                VibrationEffect.Composition.PRIMITIVE_THUD,
            )
        ) {
            VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.5f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.8f, 45)
                .compose()
        } else {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
        }
    }

    /** Разрыв склейки: короткий сухой щелчок. */
    fun tear(context: Context) = play(context) { vibrator ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL)
        ) {
            VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 0.6f)
                .compose()
        } else {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
        }
    }

    /**
     * Натяжение капли при обновлении: короткий тик, чем сильнее тянешь, тем он плотнее.
     * [strength] от 0 до 1.
     */
    fun tension(context: Context, strength: Float) = play(context) { vibrator ->
        val scale = strength.coerceIn(0.15f, 1f)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, VibrationEffect.Composition.PRIMITIVE_TICK)
        ) {
            val primitive = if (strength < 0.6f) VibrationEffect.Composition.PRIMITIVE_LOW_TICK else VibrationEffect.Composition.PRIMITIVE_TICK
            VibrationEffect.startComposition().addPrimitive(primitive, scale).compose()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK)
        ) {
            VibrationEffect.startComposition().addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, scale).compose()
        } else {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
        }
    }

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    private fun play(context: Context, build: (Vibrator) -> VibrationEffect) {
        val vibrator = vibrator(context) ?: return
        if (!vibrator.hasVibrator()) return
        runCatching { vibrator.vibrate(build(vibrator)) }
    }
}
