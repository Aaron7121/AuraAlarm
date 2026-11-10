package com.auraalarm.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

private fun Context.prefs(name: String): SharedPreferences =
    applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
    (0 until length()).map { transform(getJSONObject(it)) }

private fun JSONObject.optNullableBoolean(key: String): Boolean? =
    if (has(key) && !isNull(key)) getBoolean(key) else null

private fun exerciseOf(name: String?): Exercise =
    Exercise.entries.firstOrNull { it.name == name } ?: Exercise.SQUATS

/* ------------------------------------------------------------------ Alarmas */

object AlarmStore {
    private const val FILE = "aura_alarms"
    private const val KEY = "alarms"

    private val _alarms = MutableStateFlow<List<Alarm>>(emptyList())
    val alarms: StateFlow<List<Alarm>> = _alarms.asStateFlow()

    @Volatile
    private var loaded = false

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val raw = context.prefs(FILE).getString(KEY, null)
            _alarms.value = raw?.let { runCatching { decode(it) }.getOrNull() } ?: emptyList()
            loaded = true
        }
    }

    fun all(context: Context): List<Alarm> {
        ensureLoaded(context)
        return _alarms.value
    }

    fun get(context: Context, id: Int): Alarm? = all(context).firstOrNull { it.id == id }

    @Synchronized
    fun upsert(context: Context, alarm: Alarm): Alarm {
        val current = all(context)
        val saved = if (alarm.id == 0) alarm.copy(id = (current.maxOfOrNull { it.id } ?: 0) + 1) else alarm
        val updated = (current.filterNot { it.id == saved.id } + saved).sortedWith(compareBy({ it.hour }, { it.minute }))
        persist(context, updated)
        return saved
    }

    @Synchronized
    fun delete(context: Context, id: Int) {
        persist(context, all(context).filterNot { it.id == id })
    }

    private fun persist(context: Context, list: List<Alarm>) {
        context.prefs(FILE).edit().putString(KEY, encode(list)).commit()
        _alarms.value = list
    }

    private fun encode(list: List<Alarm>): String = JSONArray().apply {
        list.forEach { a ->
            put(JSONObject().apply {
                put("id", a.id)
                put("hour", a.hour)
                put("minute", a.minute)
                put("days", JSONArray(a.days.sorted()))
                put("enabled", a.enabled)
                put("exercise", a.exercise.name)
                put("reps", a.reps)
                put("label", a.label)
            })
        }
    }.toString()

    private fun decode(raw: String): List<Alarm> = JSONArray(raw).mapObjects { o ->
        val days = o.optJSONArray("days")
        Alarm(
            id = o.getInt("id"),
            hour = o.getInt("hour"),
            minute = o.getInt("minute"),
            days = days?.let { arr -> (0 until arr.length()).map { arr.getInt(it) }.toSet() } ?: emptySet(),
            enabled = o.optBoolean("enabled", true),
            exercise = exerciseOf(o.optString("exercise")),
            reps = o.optInt("reps", 15),
            label = o.optString("label", ""),
        )
    }
}

/* ------------------------------------------------- Alarma sonando (sesión) */

object SessionStore {
    private const val FILE = "aura_session"
    private const val KEY = "session"

    private val _session = MutableStateFlow<RingSession?>(null)
    val session: StateFlow<RingSession?> = _session.asStateFlow()

    @Synchronized
    fun get(context: Context): RingSession? {
        val raw = context.prefs(FILE).getString(KEY, null) ?: return null.also { _session.value = null }
        return runCatching {
            val o = JSONObject(raw)
            RingSession(
                alarmId = o.getInt("alarmId"),
                label = o.optString("label", ""),
                exercise = exerciseOf(o.getString("exercise")),
                reps = o.getInt("reps"),
                startedAt = o.getLong("startedAt"),
                isRecheck = o.optBoolean("isRecheck", false),
            )
        }.getOrNull().also { _session.value = it }
    }

    @Synchronized
    fun set(context: Context, s: RingSession) {
        val o = JSONObject().apply {
            put("alarmId", s.alarmId)
            put("label", s.label)
            put("exercise", s.exercise.name)
            put("reps", s.reps)
            put("startedAt", s.startedAt)
            put("isRecheck", s.isRecheck)
        }
        context.prefs(FILE).edit().putString(KEY, o.toString()).commit()
        _session.value = s
    }

    @Synchronized
    fun clear(context: Context) {
        context.prefs(FILE).edit().remove(KEY).commit()
        _session.value = null
    }
}

/* ---------------------------------------------------------------- Historial */

data class WakeStats(
    val currentStreak: Int,
    val bestStreak: Int,
    val wakeUps: Int,
    val totalPushups: Int,
    val totalSquats: Int,
    val avgSecondsToDismiss: Long,
)

object HistoryStore {
    private const val FILE = "aura_history"
    private const val KEY = "entries"
    private const val MAX_ENTRIES = 500

    private val _entries = MutableStateFlow<List<WakeEntry>>(emptyList())
    val entries: StateFlow<List<WakeEntry>> = _entries.asStateFlow()

    @Volatile
    private var loaded = false

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val raw = context.prefs(FILE).getString(KEY, null)
            _entries.value = raw?.let { runCatching { decode(it) }.getOrNull() } ?: emptyList()
            loaded = true
        }
    }

    fun all(context: Context): List<WakeEntry> {
        ensureLoaded(context)
        return _entries.value
    }

    @Synchronized
    fun add(context: Context, entry: WakeEntry) {
        persist(context, (listOf(entry) + all(context)).take(MAX_ENTRIES))
    }

    @Synchronized
    fun setAwake(context: Context, entryId: Long, awake: Boolean) {
        persist(context, all(context).map { if (it.id == entryId) it.copy(awake = awake) else it })
    }

    private fun persist(context: Context, list: List<WakeEntry>) {
        context.prefs(FILE).edit().putString(KEY, encode(list)).commit()
        _entries.value = list
    }

    fun stats(list: List<WakeEntry>): WakeStats {
        // La racha cuenta alarmas (no días), así que un fin de semana sin alarma no la rompe.
        // Solo la rompe fallar el chequeo anti-dormirse.
        val primary = list.filterNot { it.isRecheck }.sortedByDescending { it.dismissedAt }
        val current = primary.takeWhile { it.awake != false }.size
        var best = 0
        var run = 0
        primary.reversed().forEach { e ->
            run = if (e.awake == false) 0 else run + 1
            best = maxOf(best, run)
        }
        return WakeStats(
            currentStreak = current,
            bestStreak = best,
            wakeUps = primary.size,
            totalPushups = list.filter { it.exercise == Exercise.PUSHUPS }.sumOf { it.reps },
            totalSquats = list.filter { it.exercise == Exercise.SQUATS }.sumOf { it.reps },
            avgSecondsToDismiss = primary.map { it.secondsToDismiss }.average().takeIf { !it.isNaN() }?.toLong() ?: 0,
        )
    }

    private fun encode(list: List<WakeEntry>): String = JSONArray().apply {
        list.forEach { e ->
            put(JSONObject().apply {
                put("id", e.id)
                put("alarmId", e.alarmId)
                put("exercise", e.exercise.name)
                put("reps", e.reps)
                put("ringStartedAt", e.ringStartedAt)
                put("dismissedAt", e.dismissedAt)
                put("isRecheck", e.isRecheck)
                put("awake", e.awake ?: JSONObject.NULL)
            })
        }
    }.toString()

    private fun decode(raw: String): List<WakeEntry> = JSONArray(raw).mapObjects { o ->
        WakeEntry(
            id = o.getLong("id"),
            alarmId = o.optInt("alarmId"),
            exercise = exerciseOf(o.optString("exercise")),
            reps = o.optInt("reps"),
            ringStartedAt = o.optLong("ringStartedAt"),
            dismissedAt = o.optLong("dismissedAt"),
            isRecheck = o.optBoolean("isRecheck", false),
            awake = o.optNullableBoolean("awake"),
        )
    }
}

/* ------------------------------------------------- Chequeo anti-dormirse */

object AwakeCheckStore {
    private const val FILE = "aura_awake_check"
    private const val KEY = "state"

    @Synchronized
    fun get(context: Context): AwakeCheckState? {
        val raw = context.prefs(FILE).getString(KEY, null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            AwakeCheckState(
                entryId = o.getLong("entryId"),
                alarmId = o.getInt("alarmId"),
                label = o.optString("label", ""),
                reps = o.getInt("reps"),
                checkAt = o.getLong("checkAt"),
                deadlineAt = o.optLong("deadlineAt", 0),
            )
        }.getOrNull()
    }

    @Synchronized
    fun set(context: Context, s: AwakeCheckState) {
        val o = JSONObject().apply {
            put("entryId", s.entryId)
            put("alarmId", s.alarmId)
            put("label", s.label)
            put("reps", s.reps)
            put("checkAt", s.checkAt)
            put("deadlineAt", s.deadlineAt)
        }
        context.prefs(FILE).edit().putString(KEY, o.toString()).commit()
    }

    @Synchronized
    fun clear(context: Context) {
        context.prefs(FILE).edit().remove(KEY).commit()
    }
}

/* ------------------------------------------------------------------ Ajustes */

object SettingsStore {
    private const val FILE = "aura_settings"

    fun awakeCheckEnabled(context: Context): Boolean =
        context.prefs(FILE).getBoolean("awake_check", true)

    fun setAwakeCheckEnabled(context: Context, enabled: Boolean) {
        context.prefs(FILE).edit().putBoolean("awake_check", enabled).apply()
    }

    fun awakeCheckDelayMinutes(context: Context): Int =
        context.prefs(FILE).getInt("awake_check_delay", 5)

    fun setAwakeCheckDelayMinutes(context: Context, minutes: Int) {
        context.prefs(FILE).edit().putInt("awake_check_delay", minutes).apply()
    }

    /** Pasos manuales (p. ej. Autoinicio de Xiaomi) que Android no deja comprobar por código. */
    fun manualStepDone(context: Context, key: String): Boolean =
        context.prefs(FILE).getBoolean("manual_$key", false)

    fun setManualStepDone(context: Context, key: String, done: Boolean) {
        context.prefs(FILE).edit().putBoolean("manual_$key", done).apply()
    }
}
