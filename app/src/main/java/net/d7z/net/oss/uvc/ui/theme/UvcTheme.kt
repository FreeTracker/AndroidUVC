package net.d7z.net.oss.uvc.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val UvcLightColors = lightColorScheme(
    primary = Color(0xFF0E5A52),
    onPrimary = Color(0xFFF9FAFB),
    primaryContainer = Color(0xFFD9F0EA),
    onPrimaryContainer = Color(0xFF093B36),
    secondary = Color(0xFF9B5B30),
    onSecondary = Color(0xFFFFFBF8),
    secondaryContainer = Color(0xFFF7E2D2),
    onSecondaryContainer = Color(0xFF5E3416),
    tertiary = Color(0xFF415A77),
    onTertiary = Color(0xFFF7FAFC),
    tertiaryContainer = Color(0xFFD6E4F5),
    onTertiaryContainer = Color(0xFF1B324D),
    background = Color(0xFFF4EEE6),
    surface = Color(0xFFFFFBF6),
    surfaceVariant = Color(0xFFE8DED0),
    onSurface = Color(0xFF1D2939),
    onSurfaceVariant = Color(0xFF5E6B78),
    outline = Color(0xFFCDBCA9)
)

private val UvcDarkColors = darkColorScheme(
    primary = Color(0xFF8FD2C6),
    onPrimary = Color(0xFF003730),
    primaryContainer = Color(0xFF0A4A43),
    onPrimaryContainer = Color(0xFFD9F0EA),
    secondary = Color(0xFFF2BE98),
    onSecondary = Color(0xFF5A2D10),
    secondaryContainer = Color(0xFF7A4320),
    onSecondaryContainer = Color(0xFFF7E2D2),
    tertiary = Color(0xFFB4CAE6),
    onTertiary = Color(0xFF132C46),
    tertiaryContainer = Color(0xFF2A425E),
    onTertiaryContainer = Color(0xFFD6E4F5),
    background = Color(0xFF101820),
    surface = Color(0xFF18232D),
    surfaceVariant = Color(0xFF253341),
    onSurface = Color(0xFFE7EEF5),
    onSurfaceVariant = Color(0xFFB9C5D1),
    outline = Color(0xFF77889A)
)

@Composable
fun UvcComposeTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = if (darkTheme) UvcDarkColors else UvcLightColors

    val typography = MaterialTheme.typography.copy(
        headlineSmall = MaterialTheme.typography.headlineSmall.copy(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold
        ),
        titleLarge = MaterialTheme.typography.titleLarge.copy(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold
        ),
        bodyLarge = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.SansSerif),
        bodyMedium = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.SansSerif),
        labelLarge = MaterialTheme.typography.labelLarge.copy(
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.2.sp
        )
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}
