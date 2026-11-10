package com.auraalarm.pose

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions

private val LANDMARK_TYPES = mapOf(
    Joint.NOSE to PoseLandmark.NOSE,
    Joint.LEFT_SHOULDER to PoseLandmark.LEFT_SHOULDER,
    Joint.RIGHT_SHOULDER to PoseLandmark.RIGHT_SHOULDER,
    Joint.LEFT_ELBOW to PoseLandmark.LEFT_ELBOW,
    Joint.RIGHT_ELBOW to PoseLandmark.RIGHT_ELBOW,
    Joint.LEFT_WRIST to PoseLandmark.LEFT_WRIST,
    Joint.RIGHT_WRIST to PoseLandmark.RIGHT_WRIST,
    Joint.LEFT_HIP to PoseLandmark.LEFT_HIP,
    Joint.RIGHT_HIP to PoseLandmark.RIGHT_HIP,
    Joint.LEFT_KNEE to PoseLandmark.LEFT_KNEE,
    Joint.RIGHT_KNEE to PoseLandmark.RIGHT_KNEE,
    Joint.LEFT_ANKLE to PoseLandmark.LEFT_ANKLE,
    Joint.RIGHT_ANKLE to PoseLandmark.RIGHT_ANKLE,
)

fun Pose.toBodyPose(): BodyPose? {
    val joints = buildMap {
        for ((joint, type) in LANDMARK_TYPES) {
            getPoseLandmark(type)?.let { put(joint, Landmark(it.position.x, it.position.y, it.inFrameLikelihood)) }
        }
    }
    return if (joints.isEmpty()) null else BodyPose(joints)
}

/**
 * Vista de cámara con detección de postura de ML Kit en tiempo real, 100 % en el dispositivo.
 * Con COORDINATE_SYSTEM_VIEW_REFERENCED, CameraX entrega los puntos ya en coordenadas de la
 * vista previa (escalados, rotados y en espejo para la cámara frontal), listos para dibujar.
 */
@Composable
fun PoseCamera(
    useFrontCamera: Boolean,
    onPose: (BodyPose?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnPose by rememberUpdatedState(onPose)

    val detector = remember {
        PoseDetection.getClient(
            PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                .build(),
        )
    }
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
        }
    }

    LaunchedEffect(useFrontCamera) {
        runCatching {
            controller.cameraSelector =
                if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    DisposableEffect(lifecycleOwner) {
        val executor = ContextCompat.getMainExecutor(context)
        controller.setImageAnalysisAnalyzer(
            executor,
            MlKitAnalyzer(listOf(detector), ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED, executor) { result ->
                currentOnPose(result.getValue(detector)?.toBodyPose())
            },
        )
        controller.bindToLifecycle(lifecycleOwner)
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
        }
    }

    DisposableEffect(Unit) {
        onDispose { detector.close() }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                this.controller = controller
            }
        },
    )
}

/** Dibuja el esqueleto detectado encima de la vista previa (mismas coordenadas). */
@Composable
fun SkeletonOverlay(pose: BodyPose?, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (pose == null) return@Canvas
        for ((a, b) in BodyPose.BONES) {
            val la = pose.visible(a, 0.3f) ?: continue
            val lb = pose.visible(b, 0.3f) ?: continue
            drawLine(color, Offset(la.x, la.y), Offset(lb.x, lb.y), strokeWidth = 10f, cap = StrokeCap.Round)
        }
        for (lm in pose.joints.values) {
            if (lm.likelihood >= 0.3f) {
                drawCircle(Color.White, radius = 11f, center = Offset(lm.x, lm.y))
                drawCircle(color, radius = 7f, center = Offset(lm.x, lm.y))
            }
        }
    }
}
