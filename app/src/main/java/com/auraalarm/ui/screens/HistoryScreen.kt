package com.auraalarm.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.auraalarm.data.HistoryStore
import com.auraalarm.data.WakeEntry
import com.auraalarm.ui.theme.Amber
import com.auraalarm.ui.theme.Danger
import com.auraalarm.ui.theme.Ok
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val entryFormat = DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", Locale.forLanguageTag("es"))

private fun durationText(seconds: Long): String =
    if (seconds < 60) "$seconds s" else "${seconds / 60} min ${seconds % 60} s"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val entries by HistoryStore.entries.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { HistoryStore.all(context) }
    val stats = remember(entries) { HistoryStore.stats(entries) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Racha e historial") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
                        Text("Racha actual", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row {
                            Text("${stats.currentStreak}", style = MaterialTheme.typography.displayLarge, color = Amber)
                            Text(
                                if (stats.currentStreak == 1) " despertar" else " despertares",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.align(androidx.compose.ui.Alignment.Bottom).padding(bottom = 12.dp),
                            )
                        }
                        Text(
                            "Seguidos sin volver a dormirte. Se rompe si fallas el aviso \"¿Sigues despierto?\".",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat("Mejor racha", "${stats.bestStreak}", Modifier.weight(1f))
                    Stat("Despertares", "${stats.wakeUps}", Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat("Flexiones", "${stats.totalPushups}", Modifier.weight(1f))
                    Stat("Sentadillas", "${stats.totalSquats}", Modifier.weight(1f))
                    Stat("Media en apagarla", durationText(stats.avgSecondsToDismiss), Modifier.weight(1f))
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                Text("Últimos despertares", style = MaterialTheme.typography.titleMedium)
            }
            if (entries.isEmpty()) {
                item {
                    Text(
                        "Aún no hay registros. Aparecerán aquí cuando apagues tu primera alarma.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(entries, key = { it.id }) { EntryRow(it) }
        }
    }
}

@Composable
private fun Stat(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EntryRow(e: WakeEntry) {
    val whenText = Instant.ofEpochMilli(e.ringStartedAt).atZone(ZoneId.systemDefault()).format(entryFormat)
    val (status, color) = when (e.awake) {
        true -> "Despierto ✓" to Ok
        false -> "Se volvió a dormir" to Danger
        null -> (if (e.isRecheck) "Segundo intento" else "Sin chequeo") to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().padding(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text(whenText.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${e.reps} ${e.exercise.plural} en ${durationText(e.secondsToDismiss)}" + if (e.isRecheck) " · reintento" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(status, color = color, style = MaterialTheme.typography.labelLarge)
        }
    }
}
