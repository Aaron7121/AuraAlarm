package com.auraalarm.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import com.auraalarm.alarm.AlarmSound
import com.auraalarm.ui.theme.Amber
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.auraalarm.alarm.AlarmScheduler
import com.auraalarm.data.Alarm
import com.auraalarm.data.AlarmStore
import com.auraalarm.data.Exercise
import com.auraalarm.ui.AlarmActivity
import java.time.ZonedDateTime
import kotlin.math.roundToInt

private const val MIN_REPS = 3
private const val MAX_REPS = 50

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditAlarmScreen(alarmId: Int, onDone: () -> Unit) {
    val context = LocalContext.current
    val original = remember(alarmId) { AlarmStore.get(context, alarmId) ?: Alarm(id = 0) }

    val timeState = rememberTimePickerState(original.hour, original.minute, is24Hour = true)
    var days by rememberSaveable { mutableStateOf(original.days.toList()) }
    var exercise by rememberSaveable { mutableStateOf(original.exercise) }
    var reps by rememberSaveable { mutableFloatStateOf(original.reps.toFloat()) }
    var label by rememberSaveable { mutableStateOf(original.label) }
    var soundUri by rememberSaveable { mutableStateOf(original.soundUri) }
    var soundName by rememberSaveable { mutableStateOf(original.soundName) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun current() = original.copy(
        hour = timeState.hour,
        minute = timeState.minute,
        days = days.toSet(),
        exercise = exercise,
        reps = reps.roundToInt(),
        label = label.trim(),
        soundUri = soundUri,
        soundName = soundName,
        enabled = true,
    )

    fun save() {
        val saved = AlarmStore.upsert(context, current())
        AlarmScheduler.rescheduleAll(context)
        val at = saved.nextTrigger(ZonedDateTime.now())
        Toast.makeText(context, "Sonará ${untilText(at)}", Toast.LENGTH_LONG).show()
        onDone()
    }

    // La cámara es imprescindible: sin ella la alarma no se podría apagar.
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) save() else Toast.makeText(context, "Sin permiso de cámara no se puede apagar la alarma", Toast.LENGTH_LONG).show()
    }
    fun saveWithCameraCheck() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) save()
        else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (alarmId == 0) "Nueva alarma" else "Editar alarma") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                },
                actions = {
                    if (alarmId != 0) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Eliminar") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TimePicker(
                state = timeState,
                colors = TimePickerDefaults.colors(clockDialColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            )

            Section("Repetir") {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    Alarm.DAY_LETTERS.forEachIndexed { i, letter ->
                        val day = i + 1
                        FilterChip(
                            selected = day in days,
                            onClick = { days = if (day in days) days - day else days + day },
                            label = { Text(letter) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Text(
                    current().daysText(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Section("Ejercicio para apagarla") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Exercise.entries.forEach { e ->
                        FilterChip(
                            selected = exercise == e,
                            onClick = {
                                if (exercise != e && reps.roundToInt() == exercise.defaultReps) reps = e.defaultReps.toFloat()
                                exercise = e
                            },
                            label = { Text(e.label) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Section("Repeticiones: ${reps.roundToInt()} ${exercise.plural}") {
                Slider(
                    value = reps,
                    onValueChange = { reps = it },
                    valueRange = MIN_REPS.toFloat()..MAX_REPS.toFloat(),
                    steps = MAX_REPS - MIN_REPS - 1,
                )
            }

            Section("Sonido") {
                SoundPicker(
                    soundUri = soundUri,
                    soundName = soundName,
                    onChange = { uri, name ->
                        soundUri = uri
                        soundName = name
                    },
                )
            }

            Section("Nombre (opcional)") {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(40) },
                    placeholder = { Text("Trabajo, gimnasio…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { AlarmActivity.startTest(context, exercise, 3) },
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Probar")
                }
                Button(onClick = ::saveWithCameraCheck, modifier = Modifier.weight(1f).height(52.dp)) {
                    Text("Guardar")
                }
            }
            Text(
                "\"Probar\" abre la cámara sin sonido para que compruebes que te detecta (3 repeticiones).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Eliminar alarma?") },
            text = { Text("La alarma de las ${original.timeText()} se borrará.") },
            confirmButton = {
                TextButton(onClick = {
                    AlarmScheduler.cancel(context, original.id)
                    AlarmStore.delete(context, original.id)
                    AlarmScheduler.rescheduleAll(context)
                    confirmDelete = false
                    onDone()
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

/**
 * Elegir el sonido de la alarma: un tono del teléfono (selector del sistema, incluye los tonos
 * de alarma que ya tengas) o cualquier canción/archivo de audio. "Silencio" no se ofrece.
 */
@Composable
private fun SoundPicker(soundUri: String?, soundName: String?, onChange: (String?, String?) -> Unit) {
    val context = LocalContext.current
    var preview by remember { mutableStateOf<MediaPlayer?>(null) }
    fun stopPreview() {
        preview?.let { runCatching { it.stop() }; it.release() }
        preview = null
    }
    DisposableEffect(Unit) { onDispose { stopPreview() } }
    LaunchedEffect(soundUri) { stopPreview() }

    val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = result.data?.let { IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java) }
        if (uri == null || AlarmSound.isSystemDefault(uri)) {
            onChange(null, null)
        } else {
            onChange(uri.toString(), AlarmSound.ringtoneTitle(context, uri) ?: "Tono del teléfono")
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Permiso permanente: la alarma tiene que poder leer el archivo dentro de días o semanas.
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        onChange(uri.toString(), AlarmSound.fileName(context, uri) ?: "Archivo de audio")
    }

    fun openRingtonePicker() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Sonido de la alarma")
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_ALARM_ALERT_URI)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, soundUri?.toUri() ?: Settings.System.DEFAULT_ALARM_ALERT_URI)
        runCatching { ringtonePicker.launch(intent) }.onFailure {
            Toast.makeText(context, "Este teléfono no tiene selector de tonos; elige un archivo", Toast.LENGTH_SHORT).show()
            filePicker.launch(arrayOf("audio/*"))
        }
    }

    fun togglePreview() {
        if (preview != null) {
            stopPreview()
            return
        }
        val am = context.getSystemService(AudioManager::class.java)
        if (am.getStreamVolume(AudioManager.STREAM_ALARM) == 0) {
            Toast.makeText(context, "El volumen de alarma está a 0. Cuando suene de verdad se pondrá al máximo.", Toast.LENGTH_LONG).show()
        }
        val played = AlarmSound.playFirstAvailable(context, soundUri, looping = false)
        if (played == null) {
            Toast.makeText(context, "No se pudo reproducir ningún sonido", Toast.LENGTH_SHORT).show()
            return
        }
        val (player, usedChosen) = played
        if (soundUri != null && !usedChosen) {
            Toast.makeText(context, "No se puede reproducir ese sonido; sonaría el predeterminado. Elige otro.", Toast.LENGTH_LONG).show()
        }
        player.setOnCompletionListener { if (preview === it) stopPreview() }
        preview = player
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Notifications, contentDescription = null, tint = Amber)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    soundName ?: "Alarma del teléfono",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        soundUri == null -> "Predeterminado"
                        AlarmSound.isSystemTone(soundUri) -> "Tono del teléfono"
                        else -> "Archivo propio"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = ::togglePreview) { Text(if (preview != null) "Parar" else "Escuchar") }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = ::openRingtonePicker, modifier = Modifier.weight(1f)) { Text("Tonos del teléfono") }
        OutlinedButton(onClick = { filePicker.launch(arrayOf("audio/*")) }, modifier = Modifier.weight(1f)) { Text("Canción o archivo") }
    }
    if (soundUri != null) {
        TextButton(onClick = { onChange(null, null) }) { Text("Usar el sonido predeterminado") }
    }
}
