package com.glennwalker.bear.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/** The four LED colours. Everything in the app is drawn from these. */
object Led {
    val Lit = Color(0xFFDCE4C6)
    val Dim = Color(0xFF7D846F)
    val Off = Color(0xFF1C1F18)
    val Background = Color(0xFF0B0C09)

    /** The bear's voice: a dot-matrix monospace. The app sets the bundled Doto font at start-up. */
    var Voice: FontFamily = FontFamily.Monospace

    /** Stand-in for the dim checker pattern used for floor shadows. */
    val Shadow = Dim.copy(alpha = 0.45f)
}

private val Colors = darkColorScheme(
    primary = Led.Lit,
    onPrimary = Led.Background,
    secondary = Led.Dim,
    onSecondary = Led.Background,
    background = Led.Background,
    onBackground = Led.Lit,
    surface = Led.Background,
    onSurface = Led.Lit,
    surfaceVariant = Led.Off,
    onSurfaceVariant = Led.Dim,
    outline = Led.Dim,
    outlineVariant = Led.Off,
)

@Composable
fun BearTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
