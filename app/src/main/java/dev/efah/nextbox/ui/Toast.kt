package dev.efah.nextbox.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** Shows a short message at the bottom of the screen. */
val LocalToast = staticCompositionLocalOf<(String) -> Unit> { {} }
