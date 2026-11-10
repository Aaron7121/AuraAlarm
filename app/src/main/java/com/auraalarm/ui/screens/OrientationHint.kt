package com.auraalarm.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.auraalarm.ui.theme.Amber

private const val CYCLE_MS = 3_200

/**
 * Un teléfono que gira con suavidad hasta encajar en la silueta discontinua de la posición correcta.
 * Se dibuja en coordenadas de pantalla, que ya siguen la orientación física (la pantalla del reto
 * gira con el sensor), así que "alto" en el dibujo es vertical de verdad.
 */
@Composable
fun PhoneRotationAnimation(toLandscape: Boolean, modifier: Modifier = Modifier, color: Color = Color.White) {
    val from = if (toLandscape) 0f else -90f
    val to = if (toLandscape) -90f else 0f
    val transition = rememberInfiniteTransition(label = "phoneRotation")
    val angle by transition.animateFloat(
        initialValue = from,
        targetValue = to,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = CYCLE_MS
                from at 0 using LinearEasing
                from at 700 using FastOutSlowInEasing
                to at 1_700 using LinearEasing
                to at CYCLE_MS
            },
        ),
        label = "angle",
    )
    val alpha by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = CYCLE_MS
                0f at 0
                1f at 350
                1f at 2_750
                0f at CYCLE_MS
            },
        ),
        label = "alpha",
    )

    Canvas(modifier) {
        val s = size.minDimension
        val w = s * 0.36f
        val h = s * 0.66f
        val stroke = s * 0.03f
        rotate(to) { drawPhone(w, h, color.copy(alpha = 0.25f), stroke, ghost = true) }
        rotate(angle) { drawPhone(w, h, color.copy(alpha = alpha), stroke, ghost = false) }
    }
}

private fun DrawScope.drawPhone(w: Float, h: Float, color: Color, stroke: Float, ghost: Boolean) {
    val topLeft = Offset(center.x - w / 2, center.y - h / 2)
    drawRoundRect(
        color = color,
        topLeft = topLeft,
        size = Size(w, h),
        cornerRadius = CornerRadius(w * 0.16f),
        style = Stroke(
            width = stroke,
            pathEffect = if (ghost) PathEffect.dashPathEffect(floatArrayOf(stroke * 2.5f, stroke * 2f)) else null,
        ),
    )
    if (!ghost) {
        // Cámara/altavoz arriba, para que se note qué lado es cuál al girar.
        drawLine(
            color,
            Offset(center.x - w * 0.14f, topLeft.y + h * 0.08f),
            Offset(center.x + w * 0.14f, topLeft.y + h * 0.08f),
            strokeWidth = stroke * 0.8f,
            cap = StrokeCap.Round,
        )
        drawCircle(color, radius = stroke * 0.9f, center = Offset(center.x, topLeft.y + h * 0.91f))
    }
}

private fun title(toLandscape: Boolean) = if (toLandscape) "Pon el teléfono en horizontal" else "Pon el teléfono en vertical"

private fun reason(toLandscape: Boolean) =
    if (toLandscape) "Así cabe todo tu cuerpo en plancha" else "Así se te ve de la cabeza a los pies"

/** Aviso a pantalla completa mientras el teléfono está mal orientado y aún no te detecta. */
@Composable
fun OrientationHintBig(toLandscape: Boolean, isLandscapeNow: Boolean) {
    Box(
        Modifier.fillMaxSize().background(Color(0xB3000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            PhoneRotationAnimation(toLandscape, Modifier.size(if (isLandscapeNow) 120.dp else 190.dp), color = Amber)
            Spacer(Modifier.height(if (isLandscapeNow) 12.dp else 24.dp))
            Text(
                title(toLandscape),
                style = if (isLandscapeNow) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                reason(toLandscape),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Recordatorio pequeño cuando, aun mal orientado, ya te está detectando. */
@Composable
fun OrientationHintCompact(toLandscape: Boolean, modifier: Modifier = Modifier) {
    Surface(color = Color(0xCC1C222D), shape = RoundedCornerShape(50), modifier = modifier) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            PhoneRotationAnimation(toLandscape, Modifier.size(28.dp), color = Amber)
            Spacer(Modifier.width(8.dp))
            Text(
                if (toLandscape) "Mejor en horizontal" else "Mejor en vertical",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )
        }
    }
}
