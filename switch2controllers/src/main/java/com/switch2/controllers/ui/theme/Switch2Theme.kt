package com.switch2.controllers.ui.theme

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF6366F1),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF312E81),
    secondary = Color(0xFFEC4899),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFCE7F3),
    onSecondaryContainer = Color(0xFF9D174D),
    tertiary = Color(0xFF0EA5E9),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE0F2FE),
    onTertiaryContainer = Color(0xFF0369A1),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFFCBD5E1),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8B5CF6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF5B21B6),
    onPrimaryContainer = Color(0xFFE9D5FF),
    secondary = Color(0xFFEC4899),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF9D174D),
    onSecondaryContainer = Color(0xFFFCE7F3),
    tertiary = Color(0xFF06B6D4),
    onTertiary = Color(0xFF083344),
    tertiaryContainer = Color(0xFF164E63),
    onTertiaryContainer = Color(0xFFECFEFF),
    background = Color(0xFF0A0B10),
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF11131E),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF1E2235),
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0xFF334155),
)

private val RedLightColors = lightColorScheme(primary = Color(0xFFE11D48), onPrimary = Color.White, primaryContainer = Color(0xFFFFE4E6), onPrimaryContainer = Color(0xFF881337), background = Color(0xFFF8FAFC), surface = Color.White)
private val RedDarkColors = darkColorScheme(primary = Color(0xFFFB7185), onPrimary = Color(0xFF4C0519), primaryContainer = Color(0xFF9F1239), onPrimaryContainer = Color(0xFFFFE4E6), background = Color(0xFF0A0B10), surface = Color(0xFF11131E), surfaceVariant = Color(0xFF1E2235))

private val GreenLightColors = lightColorScheme(primary = Color(0xFF10B981), onPrimary = Color.White, primaryContainer = Color(0xFFD1FAE5), onPrimaryContainer = Color(0xFF064E3B), background = Color(0xFFF8FAFC), surface = Color.White)
private val GreenDarkColors = darkColorScheme(primary = Color(0xFF34D399), onPrimary = Color(0xFF022C22), primaryContainer = Color(0xFF065F46), onPrimaryContainer = Color(0xFFD1FAE5), background = Color(0xFF0A0B10), surface = Color(0xFF11131E), surfaceVariant = Color(0xFF1E2235))

private val BlueLightColors = lightColorScheme(primary = Color(0xFF3B82F6), onPrimary = Color.White, primaryContainer = Color(0xFFDBEAFE), onPrimaryContainer = Color(0xFF1E3A8A), background = Color(0xFFF8FAFC), surface = Color.White)
private val BlueDarkColors = darkColorScheme(primary = Color(0xFF60A5FA), onPrimary = Color(0xFF172554), primaryContainer = Color(0xFF1D4ED8), onPrimaryContainer = Color(0xFFDBEAFE), background = Color(0xFF0A0B10), surface = Color(0xFF11131E), surfaceVariant = Color(0xFF1E2235))

private val PurpleLightColors = lightColorScheme(primary = Color(0xFF8B5CF6), onPrimary = Color.White, primaryContainer = Color(0xFFEDE9FE), onPrimaryContainer = Color(0xFF4C1D95), background = Color(0xFFF8FAFC), surface = Color.White)
private val PurpleDarkColors = darkColorScheme(primary = Color(0xFFA78BFA), onPrimary = Color(0xFF2E1065), primaryContainer = Color(0xFF6D28D9), onPrimaryContainer = Color(0xFFEDE9FE), background = Color(0xFF0A0B10), surface = Color(0xFF11131E), surfaceVariant = Color(0xFF1E2235))

fun isAppInDarkTheme(context: Context): Boolean {
    // 1. Check AppCompatDelegate night mode
    val nightMode = AppCompatDelegate.getDefaultNightMode()
    if (nightMode == AppCompatDelegate.MODE_NIGHT_YES) return true
    if (nightMode == AppCompatDelegate.MODE_NIGHT_NO) return false

    // 2. Check Host App SharedPreferences (e.g. Mass Fusion "list_app_theme", Better xCloud or default preferences)
    try {
        val prefs = context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
        val appTheme = prefs.getString("list_app_theme", null)
            ?: prefs.getString("night_mode", null)
            ?: prefs.getString("app_theme", null)
            ?: prefs.getString("theme", null)

        if (appTheme != null) {
            val lower = appTheme.lowercase()
            if (lower == "dark" || lower.contains("night")) return true
            if (lower == "light") return false
        }
    } catch (_: Exception) {}

    // 3. Fallback to system configuration
    val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    return uiMode == Configuration.UI_MODE_NIGHT_YES
}

fun resolveAppColorScheme(context: Context, isDark: Boolean): ColorScheme {
    try {
        val prefs = context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
        val themeChoice = prefs.getString("list_app_theme", null)
        when (themeChoice) {
            "crimson_red" -> return if (isDark) RedDarkColors else RedLightColors
            "forest_green" -> return if (isDark) GreenDarkColors else GreenLightColors
            "ocean_blue" -> return if (isDark) BlueDarkColors else BlueLightColors
            "royal_purple" -> return if (isDark) PurpleDarkColors else PurpleLightColors
            "system_dynamic" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    return if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                }
            }
        }
    } catch (_: Exception) {}

    // Default dynamic on Android 12+ or fallback to DarkColors/LightColors
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (isDark) DarkColors else LightColors
    }
}

@Composable
fun Switch2Theme(
    darkTheme: Boolean = isAppInDarkTheme(LocalContext.current),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = resolveAppColorScheme(context, darkTheme)

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
