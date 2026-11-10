package com.auraalarm.data

import java.time.DayOfWeek
import java.time.ZonedDateTime
import kotlin.random.Random

enum class Exercise(val label: String, val plural: String) {
    PUSHUPS("Flexiones", "flexiones"),
    SQUATS("Sentadillas", "sentadillas"),
    RANDOM("Aleatorio", "repeticiones");

    /** Convierte [RANDOM] en un ejercicio concreto en el momento en que suena la alarma. */
    fun resolve(random: Random = Random.Default): Exercise =
        if (this == RANDOM) listOf(PUSHUPS, SQUATS).random(random) else this

    val defaultReps: Int
        get() = when (this) {
            PUSHUPS -> 10
            SQUATS -> 15
            RANDOM -> 12
        }
}

/**
 * Alarma propia de AuraAlarm.
 * [days] usa la numeración ISO de [DayOfWeek]: 1 = lunes … 7 = domingo. Vacío = suena una sola vez.
 */
data class Alarm(
    val id: Int = 0,
    val hour: Int = 7,
    val minute: Int = 0,
    val days: Set<Int> = emptySet(),
    val enabled: Boolean = true,
    val exercise: Exercise = Exercise.SQUATS,
    val reps: Int = Exercise.SQUATS.defaultReps,
    val label: String = "",
) {
    val isRepeating: Boolean get() = days.isNotEmpty()

    /** Próximo instante (estrictamente posterior a [now]) en que debe sonar. */
    fun nextTrigger(now: ZonedDateTime): ZonedDateTime {
        var date = now.toLocalDate()
        // 8 vueltas cubren "hoy ya pasó y el único día marcado es hoy" (dentro de 7 días).
        repeat(8) {
            val candidate = date.atTime(hour, minute).atZone(now.zone)
            if (candidate.isAfter(now) && (days.isEmpty() || date.dayOfWeek.value in days)) {
                return candidate
            }
            date = date.plusDays(1)
        }
        error("No se pudo calcular la próxima alarma")
    }

    fun timeText(): String = "%02d:%02d".format(hour, minute)

    fun daysText(): String = when {
        days.isEmpty() -> "Una vez"
        days.size == 7 -> "Todos los días"
        days == setOf(1, 2, 3, 4, 5) -> "Lunes a viernes"
        days == setOf(6, 7) -> "Fines de semana"
        else -> days.sorted().joinToString(" ") { DAY_LETTERS[it - 1] }
    }

    companion object {
        val DAY_LETTERS = listOf("L", "M", "X", "J", "V", "S", "D")
    }
}

/** Alarma sonando ahora mismo. Se persiste para sobrevivir a cierres forzados y reinicios. */
data class RingSession(
    val alarmId: Int,
    val label: String,
    val exercise: Exercise,
    val reps: Int,
    val startedAt: Long,
    val isRecheck: Boolean,
)

/** Un despertar completado. [awake] = null si el chequeo anti-dormirse está pendiente o desactivado. */
data class WakeEntry(
    val id: Long,
    val alarmId: Int,
    val exercise: Exercise,
    val reps: Int,
    val ringStartedAt: Long,
    val dismissedAt: Long,
    val isRecheck: Boolean,
    val awake: Boolean?,
) {
    val secondsToDismiss: Long get() = ((dismissedAt - ringStartedAt) / 1000).coerceAtLeast(0)
}

/** Chequeo anti-dormirse en curso. */
data class AwakeCheckState(
    val entryId: Long,
    val alarmId: Int,
    val label: String,
    val reps: Int,
    /** Momento en que se muestra el aviso "¿Sigues despierto?". */
    val checkAt: Long,
    /** Momento en que, si no confirmaste, vuelve a sonar. 0 = el aviso aún no se ha mostrado. */
    val deadlineAt: Long,
)
