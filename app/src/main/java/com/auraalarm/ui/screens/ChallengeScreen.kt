package com.auraalarm.ui.screens

import android.Manifest
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auraalarm.data.Exercise
import com.auraalarm.data.SettingsStore
import com.auraalarm.pose.BodyPose
import com.auraalarm.pose.PoseCamera
import com.auraalarm.pose.RepCounter
import com.auraalarm.pose.RepFeedback
import com.auraalarm.pose.SkeletonOverlay
import com.auraalarm.ui.RepSounds
import com.auraalarm.ui.theme.Amber
import com.auraalarm.ui.theme.Ok
import com.auraalarm.ui.theme.TimeStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class ChallengeState(
    val reps: Int = 0,
    val target: Int,
    val feedback: RepFeedback? = null,
    val pose: BodyPose? = null,
    val completed: Boolean = false,
    /** Hay una persona haciendo el ejercicio ahora mismo (para bajar el volumen). */
    val personActive: Boolean = false,
)

class ChallengeViewModel(exercise: Exercise, target: Int) : ViewModel() {
    private val counter = RepCounter.forExercise(exercise)
    private val _state = MutableStateFlow(ChallengeState(target = target))
    val state: StateFlow<ChallengeState> = _state.asStateFlow()
    private var lastVisibleAt = Long.MIN_VALUE / 2

    init {
        // Si dejan de llegar fotogramas (cámara tapada o cerrada), deja de considerarte activo.
        viewModelScope.launch {
            while (true) {
                delay(1_000)
                val active = isActive(SystemClock.elapsedRealtime())
                if (active != _state.value.personActive) _state.value = _state.value.copy(personActive = active)
            }
        }
    }

    fun onPose(pose: BodyPose?) {
        if (_state.value.completed) return
        val now = SystemClock.elapsedRealtime()
        val feedback = counter.update(pose, now)
        if (feedback.bodyVisible) lastVisibleAt = now
        val reps = feedback.reps.coerceAtMost(_state.value.target)
        _state.value = _state.value.copy(
            reps = reps,
            feedback = feedback,
            pose = pose,
            completed = reps >= _state.value.target,
            personActive = isActive(now),
        )
    }

    private fun isActive(now: Long) = now - lastVisibleAt < 4_000
}

private fun placementTip(exercise: Exercise): String = when (exercise) {
    Exercise.PUSHUPS ->
        "Pon el teléfono en el suelo en horizontal, de lado respecto a ti y a 1–2 m, para que se vea todo tu cuerpo en plancha."
    else ->
        "Apoya el teléfono contra una pared o un mueble a unos 2 m, de lado o de frente, para que se vea tu cuerpo de la cabeza a los pies."
}

@Composable
fun ChallengeScreen(
    exercise: Exercise,
    target: Int,
    label: String,
    isTest: Boolean,
    onPersonActive: (Boolean) -> Unit,
    onCompleted: () -> Unit,
    onCancelTest: () -> Unit,
) {
    val context = LocalContext.current
    val vm: ChallengeViewModel = viewModel(key = "$exercise-$target") { ChallengeViewModel(exercise, target) }
    val state by vm.state.collectAsStateWithLifecycle()

    var hasCamera by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCamera = it }
    var useFront by rememberSaveable { mutableStateOf(true) }
    var showTip by rememberSaveable { mutableStateOf(true) }

    // Flexiones: el cuerpo está tumbado, cabe mejor en horizontal. Sentadillas: de pie, en vertical.
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val wantLandscape = exercise == Exercise.PUSHUPS
    val wrongOrientation = hasCamera && isLandscape != wantLandscape && !state.completed

    // "Ding" en cada repetición y arpegio al terminar.
    val sounds = remember { if (SettingsStore.repSoundEnabled(context)) RepSounds(context) else null }
    DisposableEffect(sounds) { onDispose { sounds?.release() } }
    var announcedReps by rememberSaveable { mutableIntStateOf(state.reps) }
    LaunchedEffect(state.reps) {
        if (state.reps > announcedReps) {
            if (state.reps >= state.target) sounds?.playComplete() else sounds?.playRep()
        }
        announcedReps = state.reps
    }

    LaunchedEffect(state.personActive) { onPersonActive(state.personActive) }
    LaunchedEffect(state.completed) {
        if (state.completed) {
            onCompleted()
        }
    }
    LaunchedEffect(state.feedback?.bodyVisible) {
        if (state.feedback?.bodyVisible == true) showTip = false
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasCamera) {
            PoseCamera(useFrontCamera = useFront, onPose = vm::onPose, modifier = Modifier.fillMaxSize())
            SkeletonOverlay(
                pose = state.pose,
                color = if (state.feedback?.bodyVisible == true) Amber else Color(0x88FFFFFF),
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Degradados para que el texto se lea sobre la imagen de la cámara.
        Box(
            Modifier.fillMaxWidth().height(if (isLandscape) 140.dp else 220.dp).align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent))),
        )
        Box(
            Modifier.fillMaxWidth().fillMaxHeight(0.5f).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE000000)))),
        )

        val tipVisible = hasCamera && showTip && !wrongOrientation
        val tip: @Composable (Modifier) -> Unit = { modifier ->
            AnimatedVisibility(visible = tipVisible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
                Surface(color = Color(0xCC1C222D), shape = RoundedCornerShape(16.dp)) {
                    Text(
                        placementTip(exercise),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                    )
                }
            }
        }

        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(if (isLandscape) 16.dp else 20.dp)) {
            TopBar(label = label, isTest = isTest, onCancelTest = onCancelTest, onSwitchCamera = { useFront = !useFront })

            AnimatedVisibility(
                visible = wrongOrientation && state.personActive,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
            ) {
                OrientationHintCompact(toLandscape = wantLandscape)
            }

            Spacer(Modifier.weight(1f))

            if (!hasCamera) {
                CameraMissing(onRequest = { cameraPermission.launch(Manifest.permission.CAMERA) })
                Spacer(Modifier.weight(1f))
                CounterPanel(exercise = exercise, state = state, compact = isLandscape)
            } else if (isLandscape) {
                // En horizontal no cabe todo apilado: contador a la izquierda, consejo a la derecha.
                Row(verticalAlignment = Alignment.Bottom) {
                    CounterPanel(exercise = exercise, state = state, compact = true, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(24.dp))
                    tip(Modifier.weight(1f))
                }
            } else {
                tip(Modifier.fillMaxWidth().padding(bottom = 16.dp))
                CounterPanel(exercise = exercise, state = state, compact = false)
            }
        }

        // Aviso grande mientras el teléfono está mal orientado y todavía no te detecta.
        AnimatedVisibility(
            visible = wrongOrientation && !state.personActive,
            enter = fadeIn() + scaleIn(initialScale = 0.96f),
            exit = fadeOut() + scaleOut(targetScale = 0.96f),
        ) {
            OrientationHintBig(toLandscape = wantLandscape, isLandscapeNow = isLandscape)
        }

        AnimatedVisibility(visible = state.completed, enter = fadeIn(), exit = fadeOut()) {
            Completed(isTest = isTest, exercise = exercise, reps = state.target)
        }
    }
}

@Composable
private fun TopBar(label: String, isTest: Boolean, onCancelTest: () -> Unit, onSwitchCamera: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val time = remember(now / 60_000) { LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(time, style = TimeStyle, color = Color.White)
            Text(
                when {
                    isTest -> "Modo prueba · sin sonido"
                    label.isNotBlank() -> label
                    else -> "¡Hora de despertar!"
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (isTest) Amber else Color.White.copy(alpha = 0.85f),
            )
        }
        FilledTonalIconButton(onClick = onSwitchCamera) {
            Icon(Icons.Filled.Refresh, contentDescription = "Cambiar de cámara")
        }
        if (isTest) {
            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(onClick = onCancelTest) {
                Icon(Icons.Filled.Close, contentDescription = "Salir de la prueba")
            }
        }
    }
}

@Composable
private fun CounterPanel(exercise: Exercise, state: ChallengeState, compact: Boolean, modifier: Modifier = Modifier) {
    val progress by animateFloatAsState(state.reps / state.target.toFloat(), label = "progress")
    val depth by animateFloatAsState(state.feedback?.depth ?: 0f, label = "depth")
    val visible = state.feedback?.bodyVisible == true
    val depthColor by animateColorAsState(if ((state.feedback?.depth ?: 0f) >= 0.75f) Ok else Amber, label = "depthColor")

    // Pequeño salto del número cada vez que cuenta una repetición, visible desde lejos.
    val bump = remember { Animatable(1f) }
    LaunchedEffect(state.reps) {
        if (state.reps > 0) {
            bump.snapTo(1.18f)
            bump.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
        }
    }

    Column(modifier.fillMaxWidth()) {
        Text(
            exercise.label.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = Amber,
            letterSpacing = 3.sp,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "${state.reps}",
                fontSize = if (compact) 64.sp else 96.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                style = TimeStyle,
                modifier = Modifier.graphicsLayer {
                    scaleX = bump.value
                    scaleY = bump.value
                    transformOrigin = TransformOrigin(0f, 1f)
                },
            )
            Text(
                " / ${state.target}",
                fontSize = if (compact) 26.sp else 36.sp,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = if (compact) 10.dp else 16.dp),
            )
            Spacer(Modifier.weight(1f))
            // Medidor de profundidad: se llena al bajar, se pone verde al llegar al punto válido.
            Box(
                Modifier.padding(bottom = if (compact) 12.dp else 18.dp).width(14.dp).height(if (compact) 56.dp else 84.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color.White.copy(alpha = 0.15f)),
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(Modifier.fillMaxWidth().fillMaxHeight(depth.coerceIn(0f, 1f)).background(depthColor))
            }
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
            color = Amber,
            trackColor = Color.White.copy(alpha = 0.15f),
            drawStopIndicator = {},
        )
        Spacer(Modifier.height(if (compact) 8.dp else 14.dp))
        Text(
            state.feedback?.message ?: "Buscando tu cuerpo…",
            style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (visible) Color.White else Color(0xFFFFD08A),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CameraMissing(onRequest: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Falta el permiso de cámara",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Sin cámara no se pueden contar las repeticiones y la alarma no se apagará.",
            color = Color.White.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRequest) { Text("Permitir cámara") }
    }
}

@Composable
private fun Completed(isTest: Boolean, exercise: Exercise, reps: Int) {
    Box(
        Modifier.fillMaxSize().background(Color(0xF00E1116)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Ok, modifier = Modifier.size(96.dp))
            Spacer(Modifier.height(20.dp))
            Text(
                if (isTest) "¡La detección funciona!" else "¡Buenos días!",
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "$reps ${exercise.plural} completadas",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}
