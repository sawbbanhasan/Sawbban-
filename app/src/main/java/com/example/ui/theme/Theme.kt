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

private val DarkColorScheme =
  darkColorScheme(
    primary = VlcOrange,
    secondary = VlcOrangeLight,
    tertiary = VlcAccent,
    background = VlcBlack,
    surface = VlcCharcoal,
    onPrimary = VlcBlack,
    onSecondary = VlcBlack,
    onTertiary = VlcBlack,
    onBackground = VlcWhite,
    onSurface = VlcWhite,
    surfaceVariant = VlcSurfaceVariant,
    onSurfaceVariant = VlcWhite
  )

private val LightColorScheme = DarkColorScheme // Always sleek dark theme as requested!

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force dark theme for the sleek VLC media player clone
  dynamicColor: Boolean = false, // Disable dynamic colors to keep VLC brand styling
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
