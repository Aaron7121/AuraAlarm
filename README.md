# AuraAlarm

Alarma para Android que **solo se apaga haciendo ejercicio**: flexiones o sentadillas contadas en tiempo real con la cámara.
No tiene botón de posponer ni de apagar. La única salida es levantarse y moverse.

- **Detección de postura en el teléfono** con [Google ML Kit Pose Detection](https://developers.google.com/ml-kit/vision/pose-detection). Funciona sin internet y las imágenes no se guardan ni salen del teléfono. La librería ML Kit puede enviar a Google métricas de uso anónimas, nunca imágenes.
- **Sin escape**: volumen al máximo forzado, botones de volumen y "atrás" bloqueados, y un vigilante que la vuelve a hacer sonar si el sistema mata la app. Si apagas el teléfono con la alarma sonando, vuelve a sonar al encenderlo (si han pasado menos de 3 h).
- **Chequeo anti-dormirse**: unos minutos después de apagarla aparece "¿Sigues despierto?". Si no lo tocas en 2 minutos, vuelve a sonar con un reto nuevo.
- **Racha e historial**: despertares seguidos sin volver a dormirte, repeticiones totales y tiempo medio en apagarla.
- **Guía de permisos para Xiaomi / Redmi / POCO** (Inicio automático, ventanas emergentes en segundo plano, batería sin restricciones).

## Instalar

### Opción A: APK ya compilado (sin instalar nada en el PC)

1. En GitHub, ve a **Actions → Compilar APK** y abre la ejecución más reciente.
2. Descarga el artefacto **AuraAlarm-apk** (un .zip que contiene `app-debug.apk`).
3. Pásalo al teléfono y ábrelo. Android pedirá permiso para "instalar apps desconocidas" desde el navegador o el gestor de archivos.

Todas las compilaciones usan la misma clave de firma (`app/debug.keystore`), así que las nuevas versiones se instalan encima de la anterior sin perder tus alarmas.

### Opción B: Android Studio

Abre la carpeta del proyecto en Android Studio, conecta el teléfono con la depuración USB activada y pulsa **Run**.

Por línea de comandos, con el Android SDK instalado:

```bash
./gradlew testDebugUnitTest assembleDebug
# APK en app/build/outputs/apk/debug/app-debug.apk
```

## Primer uso (importante en Xiaomi)

1. Abre AuraAlarm y toca el aviso rojo, o ⚙️ → **Ajustes y permisos**.
2. Completa todo lo que aparezca con ⚠️. En un Xiaomi son, además de los permisos normales:
   - **Inicio automático**: activado.
   - **Otros permisos**: *Mostrar ventanas emergentes mientras se ejecuta en segundo plano*, *Mostrar en pantalla de bloqueo* y *Ventanas emergentes*.
   - **Ahorro de batería**: *Sin restricciones*.
   - **Bloquear en Recientes**: mantén pulsada la tarjeta de AuraAlarm en Recientes y toca el candado.
3. Crea una alarma y pulsa **Probar** en el sitio donde harás el ejercicio. Así compruebas que la cámara te detecta antes de depender de ella.

## Dónde poner el teléfono

| Ejercicio | Colocación |
|---|---|
| **Sentadillas** | Apoyado contra una pared o un mueble a unos 2 m, de lado o de frente. Tiene que verse el cuerpo entero, de los hombros a los pies. |
| **Flexiones** | En el suelo, **en horizontal**, de lado respecto a ti y a 1–2 m. Tienen que verse hombro, codo, mano y cadera. |

La pantalla del reto gira sola con el sensor aunque tengas el bloqueo de rotación, para saber dónde es "abajo".

## Cómo cuenta las repeticiones

ML Kit da 33 puntos del cuerpo por fotograma. AuraAlarm usa los del lado mejor visto y aplica una máquina de estados **arriba → abajo → arriba**, con histéresis y suavizado para que el ruido no cuente repeticiones dobles.

- **Sentadillas**: combina el ángulo de la rodilla (cadera–rodilla–tobillo), fiable de perfil, con lo que baja la cadera respecto a la rodilla, fiable de frente. Para contar la repetición hay que volver a estar **de pie con el torso vertical**, así que no se puede hacer tumbado en la cama.
- **Flexiones**: ángulo del codo (hombro–codo–muñeca). Exige **cuerpo horizontal** y **manos por debajo de los hombros**, así que no valen ni flexiones de brazos de pie ni mover los brazos boca arriba.

La lógica está en `app/src/main/java/com/auraalarm/pose/RepCounter.kt` y tiene tests con posturas sintéticas, incluidos los intentos de trampa, en `app/src/test/.../RepCounterTest.kt`.

## Sincronizar con el reloj de Android

Android no permite que otra app lea la lista de alarmas del reloj del sistema ni las silencie, así que **AuraAlarm gestiona sus propias alarmas**. Si tienes las mismas horas en el reloj nativo, desactívalas allí para que no suenen dos alarmas a la vez.

## Último recurso

Si algo fallara (por ejemplo, la cámara se estropea), **Ajustes de Android → Apps → AuraAlarm → Forzar detención** para el sonido. Si es necesario, desinstálala. Ten en cuenta que, mientras la alarma no se complete, al volver a abrir la app sigue sonando.

## Estructura

```
app/src/main/java/com/auraalarm/
├── alarm/   AlarmScheduler (AlarmManager.setAlarmClock), AlarmService (sonido en primer plano),
│            RingController (ciclo de vida + vigilante), AwakeCheck, receptores (alarma, arranque)
├── data/    Modelos y almacenamiento local (alarmas, sesión activa, historial, ajustes)
├── pose/    PoseCamera (CameraX + ML Kit), BodyPose/Geometry, RepCounter (sentadillas y flexiones)
└── ui/      MainActivity, AlarmActivity (reto sobre la pantalla de bloqueo), pantallas Compose, DeviceSetup
```

Kotlin · Jetpack Compose (Material 3) · CameraX 1.5 · ML Kit Pose Detection · minSdk 26 (Android 8) · targetSdk 36.
