package com.auraalarm.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.auraalarm.alarm.AlarmScheduler
import com.auraalarm.alarm.RingController
import com.auraalarm.data.SessionStore
import com.auraalarm.ui.screens.EditAlarmScreen
import com.auraalarm.ui.screens.HistoryScreen
import com.auraalarm.ui.screens.HomeScreen
import com.auraalarm.ui.screens.SetupScreen
import com.auraalarm.ui.theme.AuraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Por si el sistema perdió las alarmas (actualización, MIUI, etc.).
        AlarmScheduler.rescheduleAll(this)

        setContent {
            AuraTheme {
                // Navegación mínima: "home", "history", "setup" o "edit:<id>" (0 = nueva).
                var route by rememberSaveable { mutableStateOf("home") }
                BackHandler(enabled = route != "home") { route = "home" }

                when {
                    route == "home" -> HomeScreen(
                        onAdd = { route = "edit:0" },
                        onEdit = { id -> route = "edit:$id" },
                        onHistory = { route = "history" },
                        onSetup = { route = "setup" },
                    )
                    route == "history" -> HistoryScreen(onBack = { route = "home" })
                    route == "setup" -> SetupScreen(onBack = { route = "home" })
                    route.startsWith("edit:") -> EditAlarmScreen(
                        alarmId = route.removePrefix("edit:").toInt(),
                        onDone = { route = "home" },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Si hay una alarma sonando y abres la app, te lleva directo al reto.
        if (SessionStore.get(this) != null) RingController.ensureRinging(this)
    }
}
