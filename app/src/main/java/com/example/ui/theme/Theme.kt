package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = ElegantAccentPurple,
    secondary = ElegantCardBg,
    background = ElegantDarkBg,
    surface = ElegantCardBg,
    onPrimary = ElegantDarkPurple,
    onBackground = ElegantTextLight,
    onSurface = ElegantTextLight
  )

private val LightColorScheme = DarkColorScheme // Always use Elegant Dark for protective tools


@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true,
  dynamicColor: Boolean = false, // Keep Elegant Dark's custom gorgeous visual style locked
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme
  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
