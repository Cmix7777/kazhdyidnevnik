package io.github.cmix7777.kazhdyidnevnik.ui.fx

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
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

    /** Экономия заряда: включено энергосбережение или батареи мало и телефон не заряжается. Эффекты тогда замирают. */
    var saving by mutableStateOf(false)
        private set

    private var watching = false

    /** Следить за энергосбережением и зарядом, пока работает приложение. */
    fun watchPower(context: Context) {
        val app = context.applicationContext
        synchronized(this) {
            if (watching) return
            watching = true
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                update(app, if (intent.action == Intent.ACTION_BATTERY_CHANGED) intent else null)
            }
        }
        val filter = IntentFilter().apply {
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        val sticky = runCatching { ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED) }.getOrNull()
        update(app, sticky)
    }

    private fun update(context: Context, battery: Intent?) {
        val powerSave = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
        val low = battery?.let {
            val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val plugged = it.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            level >= 0 && scale > 0 && !plugged && level * 100 / scale <= LOW_BATTERY_PERCENT
        }
        saving = powerSave || (low ?: lowBattery)
        if (low != null) lowBattery = low
    }

    private var lowBattery = false
    private const val LOW_BATTERY_PERCENT = 15

    /** Шейдеры AGSL есть начиная с Android 13. На старых телефонах эффекты рисуются проще. */
    val shadersSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** В телефоне анимации выключены совсем (спец. возможности или настройки разработчика). */
    fun systemAnimationsOff(context: Context): Boolean =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

    /** Показывать ли эффекты с движением. */
    fun animate(context: Context): Boolean = !reduced && !saving && !systemAnimationsOff(context)
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

    /** Капля привета улетела: лёгкий щелчок с подъёмом. */
    fun dropThrow(context: Context) = play(context) { vibrator ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, VibrationEffect.Composition.PRIMITIVE_CLICK)
        ) {
            VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.4f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.6f, 20)
                .compose()
        } else {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
        }
    }

    /** Капля привета упала на экран: мягкий глухой удар. */
    fun dropLand(context: Context) = play(context) { vibrator ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD)
        ) {
            VibrationEffect.startComposition().addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.7f).compose()
        } else {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
        }
    }

    /** Лёгкий щелчок: сообщение ушло, свайп дотянул до ответа. */
    fun tick(context: Context) = play(context) { vibrator ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK)
        ) {
            VibrationEffect.startComposition().addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.6f).compose()
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
        // «Меньше анимаций» обещает и без вибрации.
        if (EffectsSettings.reduced) return
        val vibrator = vibrator(context) ?: return
        if (!vibrator.hasVibrator()) return
        runCatching { vibrator.vibrate(build(vibrator)) }
    }
}
