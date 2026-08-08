package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val OledColorScheme = darkColorScheme(
  primary = PureWhite,
  onPrimary = OledBlack,
  primaryContainer = DarkSurfaceVariant,
  onPrimaryContainer = OffWhite,
  secondary = MutedGray,
  onSecondary = OledBlack,
  background = OledBlack,
  onBackground = PureWhite,
  surface = OledBlack,
  onSurface = OffWhite,
  surfaceVariant = DarkSurface,
  onSurfaceVariant = MutedGray,
  outline = DimGray
)

@Composable
fun FocusBlackTheme(
  content: @Composable () -> Unit
) {
  MaterialTheme(
    colorScheme = OledColorScheme,
    typography = Typography,
    content = content
  )
}
