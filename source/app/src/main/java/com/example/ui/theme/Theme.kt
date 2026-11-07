package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = AccentCyan,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF004D40), // Dark teal for container
    onPrimaryContainer = AccentCyan,
    secondary = AccentBlue,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF003B5C),
    onSecondaryContainer = AccentBlue,
    tertiary = AccentPurple,
    onTertiary = Color.White,
    background = DarkBackground,
    onBackground = TextPrimaryDark,
    surface = DarkSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    outline = BorderDark,
    error = ErrorDark,
    errorContainer = Color(0xFF4A0000),
    onErrorContainer = ErrorDark
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD0F4DE),
    onPrimaryContainer = LightPrimary,
    secondary = LightCyan,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCAF0F8),
    onSecondaryContainer = LightPrimary,
    tertiary = AccentPurple,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    outline = BorderLight,
    error = ErrorLight,
    errorContainer = Color(0xFFFFCDD2),
    onErrorContainer = ErrorLight
)

private val CosmicSlateColorScheme = darkColorScheme(
    primary = Color(0xFF00F5FF), // High-contrast neon cyan
    onPrimary = Color(0xFF021B27),
    primaryContainer = Color(0xFF013045), // Deep metallic blue
    onPrimaryContainer = Color(0xFFE0FAFF),
    secondary = Color(0xFF8B5CF6), // Space purple
    onSecondary = Color(0xFF1F1235),
    secondaryContainer = Color(0xFF3B1F69),
    onSecondaryContainer = Color(0xFFF3E8FF),
    background = Color(0xFF0B0F19), // Cosmic dark space
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF151B2A), // Steel slate card surfaces
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF1E2638),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF334155),
    error = Color(0xFFF87171)
)

private val DynamicLightColorScheme = lightColorScheme(
    primary = Color(0xFF2563EB), // Sleek royal blue
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E40AF),
    secondary = Color(0xFF4F46E5), // Indigo
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEEF2FF),
    onSecondaryContainer = Color(0xFF3730A3),
    background = Color(0xFFF8FAFC), // Ultra white slate background
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFFE2E8F0),
    error = Color(0xFFEF4444)
)

private val ObsidianColorScheme = darkColorScheme(
    primary = Color(0xFFC084FC), // Soft violet/amethyst highlight
    onPrimary = Color(0xFF1E0045), // Deep indigo-violet on light violet
    primaryContainer = Color(0xFF3B0764), // Dark midnight violet container
    onPrimaryContainer = Color(0xFFF3E8FF), // Light violet text
    secondary = Color(0xFFA5B4FC), // Light periwinkle
    onSecondary = Color(0xFF0F172A),
    secondaryContainer = Color(0xFF1E1B4B),
    onSecondaryContainer = Color(0xFFE0E7FF),
    background = Color(0xFF050505), // Ultra-premium near black
    onBackground = Color(0xFFF3F4F6),
    surface = Color(0xFF0E0E11), // Rich charcoal-amethyst slate
    onSurface = Color(0xFFF3F4F6),
    surfaceVariant = Color(0xFF1C1B22),
    onSurfaceVariant = Color(0xFFA1A1AA),
    outline = Color(0xFF27272A),
    error = Color(0xFFEF4444)
)

private val CharcoalColorScheme = darkColorScheme(
    primary = Color(0xFF14B8A6), // High-end premium emerald teal
    onPrimary = Color(0xFF042F2E),
    primaryContainer = Color(0xFF115E59), // Deep dark teal
    onPrimaryContainer = Color(0xFFCCFBF1),
    secondary = Color(0xFF38BDF8), // Electric cyan accent
    onSecondary = Color(0xFF082F49),
    secondaryContainer = Color(0xFF0369A1),
    onSecondaryContainer = Color(0xFFE0F2FE),
    background = Color(0xFF0F172A), // Rich slate-grey
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF1E293B), // Matte grey
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF475569),
    error = Color(0xFFF87171)
)

private val SepiaColorScheme = lightColorScheme(
    primary = Color(0xFF78350F), // Luxurious deep amber-chocolate brown with exceptional contrast
    onPrimary = Color(0xFFFFFFFF), // Pure contrast
    primaryContainer = Color(0xFFFEF3C7), // Soft amber cream book container
    onPrimaryContainer = Color(0xFF451A03), // Warm chocolate ink
    secondary = Color(0xFFB45309), // Rich amber accent
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFDE68A),
    onSecondaryContainer = Color(0xFF78350F),
    background = Color(0xFFFDFBF7), // Warm antique milk/cream paper background (extremely cozy & easy on eyes)
    onBackground = Color(0xFF291A0D), // Deep ink text
    surface = Color(0xFFF5EFEB), // Beautiful smooth warm card background
    onSurface = Color(0xFF291A0D),
    surfaceVariant = Color(0xFFEDE2DA),
    onSurfaceVariant = Color(0xFF5F4E42), // Soft leather accent
    outline = Color(0xFFDCD0C4), // Subtle paper edge border
    error = Color(0xFFB3261E)
)

private val CreamColorScheme = lightColorScheme(
    primary = Color(0xFF0F172A), // Rich midnight charcoal primary
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE2E8F0), // Smooth cool slate container
    onPrimaryContainer = Color(0xFF0F172A),
    secondary = Color(0xFF475569), // Executive slate grey
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF1F5F9),
    onSecondaryContainer = Color(0xFF1E293B),
    background = Color(0xFFFAF9F6), // Alabaster/cream smooth background
    onBackground = Color(0xFF111827), // Clear high-contrast charcoal ink text
    surface = Color(0xFFF5F4F0), // Slightly deeper alabaster surface
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFEAE9E4),
    onSurfaceVariant = Color(0xFF525252),
    outline = Color(0xFFD4D4D4),
    error = Color(0xFFB3261E)
)

private val MidnightBlueColorScheme = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF032B45),
    primaryContainer = Color(0xFF075985),
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = Color(0xFF818CF8),
    onSecondary = Color(0xFF1E1B4B),
    secondaryContainer = Color(0xFF3730A3),
    onSecondaryContainer = Color(0xFFE0E7FF),
    background = Color(0xFF030D1B),
    onBackground = Color(0xFFF0F9FF),
    surface = Color(0xFF0A192F),
    onSurface = Color(0xFFF0F9FF),
    surfaceVariant = Color(0xFF172A46),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF1E3A5F),
    error = Color(0xFFF87171)
)

private val GraphiteColorScheme = darkColorScheme(
    primary = Color(0xFFE2E8F0),
    onPrimary = Color(0xFF0F172A),
    primaryContainer = Color(0xFF334155),
    onPrimaryContainer = Color(0xFFF8FAFC),
    secondary = Color(0xFF94A3B8),
    onSecondary = Color(0xFF0F172A),
    secondaryContainer = Color(0xFF475569),
    onSecondaryContainer = Color(0xFFF1F5F9),
    background = Color(0xFF121214),
    onBackground = Color(0xFFFAFAFA),
    surface = Color(0xFF1C1C20),
    onSurface = Color(0xFFFAFAFA),
    surfaceVariant = Color(0xFF27272A),
    onSurfaceVariant = Color(0xFFA1A1AA),
    outline = Color(0xFF3F3F46),
    error = Color(0xFFEF4444)
)

private val EmeraldStudioColorScheme = darkColorScheme(
    primary = Color(0xFF34D399),
    onPrimary = Color(0xFF022C22),
    primaryContainer = Color(0xFF065F46),
    onPrimaryContainer = Color(0xFFD1FAE5),
    secondary = Color(0xFF2DD4BF),
    onSecondary = Color(0xFF042F2E),
    secondaryContainer = Color(0xFF115E59),
    onSecondaryContainer = Color(0xFFCCFBF1),
    background = Color(0xFF021B15),
    onBackground = Color(0xFFECFDF5),
    surface = Color(0xFF062E24),
    onSurface = Color(0xFFECFDF5),
    surfaceVariant = Color(0xFF0D4536),
    onSurfaceVariant = Color(0xFF6EE7B7),
    outline = Color(0xFF10B981),
    error = Color(0xFFF87171)
)

private val HighContrastColorScheme = darkColorScheme(
    primary = Color(0xFFFFEB3B),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF333300),
    onPrimaryContainer = Color(0xFFFFFF00),
    secondary = Color(0xFF00FFFF),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF004444),
    onSecondaryContainer = Color(0xFF00FFFF),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFFDDDDDD),
    outline = Color(0xFFFFFFFF),
    error = Color(0xFFFF5252)
)

@Composable
fun MyApplicationTheme(
    appTheme: String = "Sepia",
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Force custom brand aesthetic
    content: @Composable () -> Unit
) {
    val isThemeDark = when (appTheme) {
        "Cosmic Slate" -> true
        "Obsidian" -> true
        "Charcoal" -> true
        "Dark" -> true
        "Midnight Blue" -> true
        "Graphite" -> true
        "Emerald Studio" -> true
        "High Contrast" -> true
        "Dynamic Light" -> false
        "Light" -> false
        "Sepia" -> false
        "Cream" -> false
        else -> darkTheme
    }

    val colorScheme = when (appTheme) {
        "Cosmic Slate" -> CosmicSlateColorScheme
        "Dynamic Light" -> DynamicLightColorScheme
        "Obsidian" -> ObsidianColorScheme
        "Charcoal" -> CharcoalColorScheme
        "Dark" -> DarkColorScheme
        "Light" -> LightColorScheme
        "Midnight Blue" -> MidnightBlueColorScheme
        "Graphite" -> GraphiteColorScheme
        "Emerald Studio" -> EmeraldStudioColorScheme
        "High Contrast" -> HighContrastColorScheme
        "Sepia" -> SepiaColorScheme
        "Cream" -> CreamColorScheme
        else -> {
            if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else if (darkTheme) {
                DarkColorScheme
            } else {
                LightColorScheme
            }
        }
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity
            activity?.window?.let { window ->
                
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isThemeDark
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
