package com.auraalarm.ui

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.auraalarm.alarm.AlarmScheduler
import com.auraalarm.data.SettingsStore

sealed interface SetupAction {
    data class RuntimePermission(val permission: String) : SetupAction
    data class OpenSettings(val intents: List<Intent>) : SetupAction
}

data class SetupItem(
    val key: String,
    val title: String,
    val description: String,
    val done: Boolean,
    /** Android no deja comprobarlo por código: el usuario lo marca como hecho. */
    val manual: Boolean,
    /** Sin esto la alarma puede no sonar o no apagarse. */
    val critical: Boolean,
    val action: SetupAction,
)

object DeviceSetup {

    val isXiaomi: Boolean
        get() = listOf(Build.MANUFACTURER, Build.BRAND).any {
            it.lowercase() in setOf("xiaomi", "redmi", "poco")
        }

    fun items(context: Context): List<SetupItem> = buildList {
        val pkg = context.packageName
        val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))

        add(
            SetupItem(
                key = "camera",
                title = "Cámara",
                description = "Para contar tus repeticiones. Las imágenes se analizan en el teléfono y no se guardan ni se envían.",
                done = granted(context, Manifest.permission.CAMERA),
                manual = false,
                critical = true,
                action = SetupAction.RuntimePermission(Manifest.permission.CAMERA),
            ),
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(
                SetupItem(
                    key = "notifications",
                    title = "Notificaciones",
                    description = "Necesarias para que la alarma aparezca en pantalla completa y para el aviso \"¿Sigues despierto?\".",
                    done = granted(context, Manifest.permission.POST_NOTIFICATIONS),
                    manual = false,
                    critical = true,
                    action = SetupAction.RuntimePermission(Manifest.permission.POST_NOTIFICATIONS),
                ),
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(
                SetupItem(
                    key = "exact",
                    title = "Alarmas exactas",
                    description = "Para que suene exactamente a la hora programada.",
                    done = AlarmScheduler.canScheduleExact(context),
                    manual = false,
                    critical = true,
                    action = SetupAction.OpenSettings(
                        listOf(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$pkg")), appDetails),
                    ),
                ),
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = context.getSystemService(NotificationManager::class.java)
            add(
                SetupItem(
                    key = "fullscreen",
                    title = "Pantalla completa",
                    description = "Permite abrir el reto sobre la pantalla de bloqueo cuando suena.",
                    done = nm.canUseFullScreenIntent(),
                    manual = false,
                    critical = true,
                    action = SetupAction.OpenSettings(
                        listOf(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$pkg")), appDetails),
                    ),
                ),
            )
        }

        add(
            SetupItem(
                key = "overlay",
                title = "Mostrar sobre otras apps",
                description = "Abre el reto aunque estés usando el teléfono cuando suene la alarma.",
                done = Settings.canDrawOverlays(context),
                manual = false,
                critical = isXiaomi,
                action = SetupAction.OpenSettings(
                    listOf(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$pkg")), appDetails),
                ),
            ),
        )

        val pm = context.getSystemService(PowerManager::class.java)
        add(
            SetupItem(
                key = "battery",
                title = "Sin optimización de batería",
                description = "Evita que Android duerma la app y retrase la alarma.",
                done = pm.isIgnoringBatteryOptimizations(pkg),
                manual = false,
                critical = true,
                action = SetupAction.OpenSettings(
                    listOf(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pkg")),
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                    ),
                ),
            ),
        )

        if (isXiaomi) {
            add(
                SetupItem(
                    key = "miui_autostart",
                    title = "Xiaomi · Inicio automático",
                    description = "Activa AuraAlarm en Inicio automático. Sin esto MIUI/HyperOS no reprograma las alarmas tras reiniciar y puede bloquearlas.",
                    done = SettingsStore.manualStepDone(context, "miui_autostart"),
                    manual = true,
                    critical = true,
                    action = SetupAction.OpenSettings(
                        listOf(
                            Intent().setComponent(
                                ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                            ),
                            appDetails,
                        ),
                    ),
                ),
            )
            add(
                SetupItem(
                    key = "miui_popups",
                    title = "Xiaomi · Otros permisos",
                    description = "En \"Otros permisos\" activa: Mostrar ventanas emergentes mientras se ejecuta en segundo plano, " +
                        "Mostrar en pantalla de bloqueo y Ventanas emergentes. Sin esto la alarma suena pero el reto no aparece.",
                    done = SettingsStore.manualStepDone(context, "miui_popups"),
                    manual = true,
                    critical = true,
                    action = SetupAction.OpenSettings(
                        listOf(
                            Intent("miui.intent.action.APP_PERM_EDITOR")
                                .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                                .putExtra("extra_pkgname", pkg),
                            Intent("miui.intent.action.APP_PERM_EDITOR")
                                .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.AppPermissionsEditorActivity")
                                .putExtra("extra_pkgname", pkg),
                            appDetails,
                        ),
                    ),
                ),
            )
            add(
                SetupItem(
                    key = "miui_battery",
                    title = "Xiaomi · Ahorro de batería: Sin restricciones",
                    description = "En la configuración de batería de la app elige \"Sin restricciones\".",
                    done = SettingsStore.manualStepDone(context, "miui_battery"),
                    manual = true,
                    critical = true,
                    action = SetupAction.OpenSettings(
                        listOf(
                            Intent()
                                .setComponent(ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"))
                                .putExtra("package_name", pkg)
                                .putExtra("package_label", "AuraAlarm"),
                            appDetails,
                        ),
                    ),
                ),
            )
            add(
                SetupItem(
                    key = "miui_lock_recents",
                    title = "Xiaomi · Bloquear en Recientes",
                    description = "Abre Recientes, mantén pulsada la tarjeta de AuraAlarm y toca el candado. Así \"Limpiar todo\" no la cierra.",
                    done = SettingsStore.manualStepDone(context, "miui_lock_recents"),
                    manual = true,
                    critical = false,
                    action = SetupAction.OpenSettings(emptyList()),
                ),
            )
        }
    }

    fun missingCritical(context: Context): Int = items(context).count { it.critical && !it.done }

    /** Abre el primer intent que el sistema sepa resolver. */
    fun open(context: Context, intents: List<Intent>): Boolean {
        for (intent in intents) {
            val ok = runCatching {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
            if (ok) return true
        }
        return false
    }

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
