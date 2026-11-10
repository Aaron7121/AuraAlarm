package com.auraalarm.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.auraalarm.alarm.AlarmScheduler
import com.auraalarm.data.Alarm
import com.auraalarm.data.AlarmStore
import com.auraalarm.data.HistoryStore
import com.auraalarm.ui.DeviceSetup
import com.auraalarm.ui.theme.Amber
import com.auraalarm.ui.theme.TimeStyle
import kotlinx.coroutines.delay
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val ES = Locale.forLanguageTag("es")

/** "en 7 h 12 min" */
fun untilText(target: ZonedDateTime, now: ZonedDateTime = ZonedDateTime.now()): String {
    val minutes = ((java.time.Duration.between(now, target).toMillis() + 59_999) / 60_000).coerceAtLeast(0)
    val d = minutes / (24 * 60)
    val h = (minutes / 60) % 24
    val m = minutes % 60
    return buildString {
        append("en ")
        if (d > 0) append("$d d ")
        if (h > 0) append("$h h ")
        if (d == 0L) append("$m min")
    }.trim()
}

fun dayAndTime(t: ZonedDateTime): String {
    val day = t.dayOfWeek.getDisplayName(TextStyle.SHORT, ES).replaceFirstChar { it.uppercase() }
    return "$day ${t.format(DateTimeFormatter.ofPattern("HH:mm"))}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAdd: () -> Unit,
    onEdit: (Int) -> Unit,
    onHistory: () -> Unit,
    onSetup: () -> Unit,
) {
    val context = LocalContext.current
    val alarms by AlarmStore.alarms.collectAsStateWithLifecycle()
    val entries by HistoryStore.entries.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        AlarmStore.all(context)
        HistoryStore.all(context)
    }

    var missing by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { missing = DeviceSetup.missingCritical(context) }

    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            tick++
        }
    }
    val next = remember(alarms, tick) { AlarmScheduler.nextAlarm(alarms) }
    val stats = remember(entries) { HistoryStore.stats(entries) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AuraAlarm", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = onHistory) { Icon(Icons.AutoMirrored.Filled.List, "Historial") }
                    IconButton(onClick = onSetup) { Icon(Icons.Filled.Settings, "Ajustes y permisos") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Nueva alarma") },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (missing > 0) {
                item {
                    Card(
                        onClick = onSetup,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Faltan $missing ajustes para que la alarma suene sí o sí. Toca para configurarlos.",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
                        Text("Próxima alarma", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        if (next == null) {
                            Text("Ninguna activa", style = MaterialTheme.typography.headlineSmall)
                        } else {
                            Text(dayAndTime(next.second), style = MaterialTheme.typography.headlineMedium)
                            Text(untilText(next.second), color = Amber)
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.clickable(onClick = onHistory), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Star, null, tint = Amber, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when (stats.currentStreak) {
                                    0 -> "Sin racha todavía"
                                    1 -> "Racha: 1 despertar"
                                    else -> "Racha: ${stats.currentStreak} despertares"
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }

            if (alarms.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "Crea tu primera alarma.\nSolo se apaga haciendo el ejercicio.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }

            items(alarms, key = { it.id }) { alarm ->
                AlarmRow(
                    alarm = alarm,
                    onClick = { onEdit(alarm.id) },
                    onToggle = { enabled ->
                        AlarmStore.upsert(context, alarm.copy(enabled = enabled))
                        AlarmScheduler.rescheduleAll(context)
                    },
                )
            }
        }
    }
}

@Composable
private fun AlarmRow(alarm: Alarm, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val dim = if (alarm.enabled) 1f else 0.45f
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(alarm.timeText(), style = TimeStyle, color = MaterialTheme.colorScheme.onSurface.copy(alpha = dim))
                Text(
                    listOfNotNull(alarm.label.takeIf { it.isNotBlank() }, alarm.daysText()).joinToString(" · "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dim),
                )
                Text(
                    "${alarm.reps} ${alarm.exercise.plural}" + if (alarm.exercise.name == "RANDOM") " (flexiones o sentadillas)" else "",
                    color = Amber.copy(alpha = dim),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Switch(checked = alarm.enabled, onCheckedChange = onToggle)
        }
    }
}
