package com.bobsdevattic.networkanalyzer.data

/** User's theme choice. SYSTEM follows the device dark-mode setting. */
enum class ThemeMode(val label: String) {
    SYSTEM("System default"),
    LIGHT("Light"),
    DARK("Dark"),
}
