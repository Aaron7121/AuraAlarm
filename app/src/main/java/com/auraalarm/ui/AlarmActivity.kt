package com.auraalarm.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.auraalarm.alarm.RingController
import com.auraalarm.data.Exercise
import com.auraalarm.data.SessionStore
import com.auraalarm.ui.screens.ChallengeScreen
import com.auraalarm.ui.theme.AuraTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pantalla del reto. Se muestra sobre la pantalla de bloqueo y no tiene botón de salida:
 * la única forma de cerrarla (fuera del modo prueba) es completar las repeticiones.
 */
class AlarmActivity : ComponentActivity() {

    private var isTest = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        enableEdgeToEdge()

        isTest = intent.getBooleanExtra(EXTRA_TEST, false)
        val exercise: Exercise
        val reps: Int
        val label: String
        if (isTest) {
            exercise = Exercise.valueOf(intent.getStringExtra(EXTRA_EXERCISE) ?: Exercise.SQUATS.name)
            reps = intent.getIntExtra(EXTRA_REPS, 3)
            label = ""
        } else {
            val session = SessionStore.get(this)
            if (session == null) {
                finish()
                return
            }
            exercise = session.exercise
            reps = session.reps
            label = session.label
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(!isTest) {
            override fun handleOnBackPressed() = Unit // sin escape
        })

        setContent {
            AuraTheme {
                ChallengeScreen(
                    exercise = exercise,
                    target = reps,
                    label = label,
                    isTest = isTest,
                    onPersonActive = { active -> if (!isTest) RingController.ducked.value = active },
                    onCompleted = ::onChallengeCompleted,
                    onCancelTest = { finish() },
                )
            }
        }
    }

    private fun onChallengeCompleted() {
        if (!isTest) RingController.complete(applicationContext)
        lifecycleScope.launch {
            delay(2_500)
            finish()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Una alarma real que llega mientras haces una prueba sustituye a la prueba (nunca al revés).
        if (isTest && !intent.getBooleanExtra(EXTRA_TEST, false)) {
            setIntent(intent)
            recreate()
        }
    }

    override fun onStop() {
        super.onStop()
        // Si sales del reto, la alarma vuelve a todo volumen.
        RingController.ducked.value = false
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (!isTest && keyCode in VOLUME_KEYS) return true
        return super.onKeyDown(keyCode, event)
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    companion object {
        private const val EXTRA_TEST = "test"
        private const val EXTRA_EXERCISE = "exercise"
        private const val EXTRA_REPS = "reps"
        private val VOLUME_KEYS = setOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE)

        /** Prueba la detección sin sonido ni registro en el historial. */
        fun startTest(context: Context, exercise: Exercise, reps: Int) {
            if (SessionStore.get(context) != null) return // hay una alarma real sonando
            context.startActivity(
                Intent(context, AlarmActivity::class.java)
                    .putExtra(EXTRA_TEST, true)
                    .putExtra(EXTRA_EXERCISE, exercise.resolve().name)
                    .putExtra(EXTRA_REPS, reps),
            )
        }
    }
}
