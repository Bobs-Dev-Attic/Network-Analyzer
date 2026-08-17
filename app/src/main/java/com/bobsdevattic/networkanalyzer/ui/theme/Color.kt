package com.bobsdevattic.networkanalyzer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** A semantic role: foreground colour plus the tinted container it sits on. */
@Immutable
data class StatusColor(val fg: Color, val container: Color)

/**
 * Status colours are deliberately *fixed*, not derived from dynamic colour: PASS is
 * green on every device regardless of wallpaper. Chrome (tabs, buttons, top bar) still
 * uses the dynamic scheme — see [NetworkAnalyzerTheme].
 */
@Immutable
data class StatusColors(
    val good: StatusColor,
    val warn: StatusColor,
    val bad: StatusColor,
    val info: StatusColor,
    val band24: StatusColor,
    val band5: StatusColor,
    val band6: StatusColor,
)

val LightStatusColors = StatusColors(
    good = StatusColor(Color(0xFF1B6E3C), Color(0xFFDCF0E3)),
    warn = StatusColor(Color(0xFF8A5300), Color(0xFFFDEBD2)),
    bad = StatusColor(Color(0xFFB3261E), Color(0xFFF9DEDC)),
    info = StatusColor(Color(0xFF3B4FA8), Color(0xFFDEE1FF)),
    band24 = StatusColor(Color(0xFF00696E), Color(0xFFB8ECEF)),
    band5 = StatusColor(Color(0xFF3B4FA8), Color(0xFFDEE1FF)),
    band6 = StatusColor(Color(0xFF6B3FA0), Color(0xFFEDDCFF)),
)

val DarkStatusColors = StatusColors(
    good = StatusColor(Color(0xFF7BD99F), Color(0xFF12341F)),
    warn = StatusColor(Color(0xFFF5C169), Color(0xFF3A2A0C)),
    bad = StatusColor(Color(0xFFF2B8B5), Color(0xFF4A1F1C)),
    info = StatusColor(Color(0xFF9FB0FF), Color(0xFF1B2559)),
    band24 = StatusColor(Color(0xFF4FD8E0), Color(0xFF063B3E)),
    band5 = StatusColor(Color(0xFF9FB0FF), Color(0xFF1B2559)),
    band6 = StatusColor(Color(0xFFCBA8F5), Color(0xFF2E1B45)),
)

/**
 * Provided by [NetworkAnalyzerTheme] from its *already-resolved* dark flag. Screens must
 * read status colours from here rather than branching on `isSystemInDarkTheme()`, which
 * reports the system value and so disagrees with the app's own ThemeMode override.
 */
val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }

val MaterialTheme.statusColors: StatusColors
    @Composable
    @ReadOnlyComposable
    get() = LocalStatusColors.current
