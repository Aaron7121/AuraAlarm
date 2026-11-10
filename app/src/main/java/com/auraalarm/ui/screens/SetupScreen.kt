package com.auraalarm.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.auraalarm.alarm.AlarmScheduler
import com.auraalarm.data.SettingsStore
import com.auraalarm.ui.DeviceSetup
import com.auraalarm.ui.SetupAction
import com.auraalarm.ui.SetupItem
import com.auraalarm.ui.theme.Amber
import com.auraalarm.ui.theme.Ok

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        refresh++
        AlarmScheduler.rescheduleAll(context) // p. ej. tras conceder alarmas exactas
    }
    val items = remember(refresh) { DeviceSetup.items(context) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refresh++
        if (!granted) {
            Toast.makeText(context, "Si no aparece el diálogo, concédelo desde Ajustes > Apps > AuraAlarm", Toast.LENGTH_LONG).show()
        }
    }

    var awakeCheck by remember { mutableStateOf(SettingsStore.awakeCheckEnabled(context)) }
    var awakeDelay by remember { mutableIntStateOf(SettingsStore.awakeCheckDelayMinutes(context)) }
    var repSound by remember { mutableStateOf(SettingsStore.repSoundEnabled(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes y permisos") },
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
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Chequeo anti-dormirse", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Tras apagar la alarma aparece un aviso que debes tocar en 2 minutos. Si no lo tocas, vuelve a sonar con otro reto.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Switch(checked = awakeCheck, onCheckedChange = {
                                awakeCheck = it
                                SettingsStore.setAwakeCheckEnabled(context, it)
                            })
                        }
                        if (awakeCheck) {
                            Spacer(Modifier.height(8.dp))
                            Text("Aviso a los…", style = MaterialTheme.typography.bodyMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(3, 5, 10).forEach { m ->
                                    FilterChip(
                                        selected = awakeDelay == m,
                                        onClick = {
                                            awakeDelay = m
                                            SettingsStore.setAwakeCheckDelayMinutes(context, m)
                                        },
                                        label = { Text("$m min") },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Sonido en cada repetición", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Un \"ding\" al contar cada repetición y un acorde al completar el reto.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(checked = repSound, onCheckedChange = {
                            repSound = it
                            SettingsStore.setRepSoundEnabled(context, it)
                        })
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text("Para que suene sí o sí", style = MaterialTheme.typography.titleMedium)
                if (DeviceSetup.isXiaomi) {
                    Text(
                        "Detectamos un Xiaomi: MIUI/HyperOS bloquea las alarmas de otras apps si no completas los pasos marcados como Xiaomi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Amber,
                    )
                }
            }

            items(items, key = { it.key }) { item ->
                SetupRow(
                    item = item,
                    onAction = {
                        when (val action = item.action) {
                            is SetupAction.RuntimePermission -> permissionLauncher.launch(action.permission)
                            is SetupAction.OpenSettings ->
                                if (action.intents.isNotEmpty() && !DeviceSetup.open(context, action.intents)) {
                                    Toast.makeText(context, "No se pudo abrir esa pantalla de ajustes", Toast.LENGTH_SHORT).show()
                                }
                        }
                    },
                    onManualToggle = { done ->
                        SettingsStore.setManualStepDone(context, item.key, done)
                        refresh++
                    },
                )
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Consejo: usa \"Probar\" en una alarma para comprobar que la cámara te detecta en el sitio donde harás el ejercicio.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SetupRow(item: SetupItem, onAction: () -> Unit, onManualToggle: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (item.done) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = if (item.done) "Listo" else "Pendiente",
                    tint = when {
                        item.done -> Ok
                        item.critical -> MaterialTheme.colorScheme.error
                        else -> Amber
                    },
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(item.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(4.dp))
            Text(item.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            val hasSettings = (item.action as? SetupAction.OpenSettings)?.intents?.isNotEmpty() != false
            if (item.manual || (!item.done && hasSettings)) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hasSettings && (!item.done || item.manual)) {
                        FilledTonalButton(onClick = onAction) {
                            Text(if (item.action is SetupAction.RuntimePermission) "Conceder" else "Abrir ajustes")
                        }
                    }
                    if (item.manual) {
                        Spacer(Modifier.weight(1f))
                        Text("Hecho", style = MaterialTheme.typography.bodyMedium)
                        Checkbox(checked = item.done, onCheckedChange = onManualToggle)
                    }
                }
            }
        }
    }
}
