package com.lazyapps.wifianalyzer.ui.theme

import androidx.compose.ui.graphics.Color

internal val Navy950 = Color(0xFF07111F)
internal val Navy900 = Color(0xFF0B1625)
internal val Navy800 = Color(0xFF122033)
internal val Slate200 = Color(0xFFD9E2F1)
internal val Slate700 = Color(0xFF34445B)

internal fun AccentColor.lightSeed(): Color = when (this) {
    AccentColor.BLUE -> Color(0xFF2456D8)
    AccentColor.INDIGO -> Color(0xFF4A43C4)
    AccentColor.PURPLE -> Color(0xFF7A3EC8)
    AccentColor.CYAN -> Color(0xFF006C7D)
    AccentColor.GREEN -> Color(0xFF16784A)
    AccentColor.ORANGE -> Color(0xFFC44800)
    AccentColor.PINK -> Color(0xFFB72D65)
}

internal fun AccentColor.darkSeed(): Color = when (this) {
    AccentColor.BLUE -> Color(0xFF7EA2FF)
    AccentColor.INDIGO -> Color(0xFFA9A3FF)
    AccentColor.PURPLE -> Color(0xFFD2A6FF)
    AccentColor.CYAN -> Color(0xFF72D5E7)
    AccentColor.GREEN -> Color(0xFF70D9A2)
    AccentColor.ORANGE -> Color(0xFFFFB684)
    AccentColor.PINK -> Color(0xFFFFA7C7)
}
