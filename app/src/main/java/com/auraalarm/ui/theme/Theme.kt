package com.auraalarm.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Amber = Color(0xFFFFB547)
val Ok = Color(0xFF5BD69A)
val Danger = Color(0xFFFF6B6B)

private val AuraColors = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF241500),
    primaryContainer = Color(0xFF3A2A10),
    onPrimaryContainer = Color(0xFFFFDDB0),
    secondary = Color(0xFF8FB4FF),
    onSecondary = Color(0xFF0A1A3A),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE8EAF0),
    surface = Color(0xFF0E1116),
    onSurface = Color(0xFFE8EAF0),
    surfaceVariant = Color(0xFF1A1F29),
    onSurfaceVariant = Color(0xFFA7AEBD),
    surfaceContainer = Color(0xFF161B24),
    surfaceContainerHigh = Color(0xFF1C222D),
    surfaceContainerHighest = Color(0xFF232A36),
    outline = Color(0xFF3A4252),
    error = Danger,
)

private val AuraTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Light),
        displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Light),
    )
}

/** Estilo para horas grandes: cifras de ancho fijo para que no "bailen". */
val TimeStyle = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Light, fontFeatureSettings = "tnum")

@Composable
fun AuraTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AuraColors, typography = AuraTypography, content = content)
}
