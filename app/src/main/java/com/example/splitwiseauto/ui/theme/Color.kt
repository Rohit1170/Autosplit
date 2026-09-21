package com.example.splitwiseauto.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// ---- Ocean Teal — light ----
val TealPrimaryLight = Color(0xFF0E9488)
val TealOnPrimaryLight = Color(0xFFFFFFFF)
val TealPrimaryContainerLight = Color(0xFFCFF1EC)
val TealOnPrimaryContainerLight = Color(0xFF00332E)
val TealSecondaryLight = Color(0xFF4F7A73)
val TealSecondaryContainerLight = Color(0xFFE7F6F4)
val TealOnSecondaryContainerLight = Color(0xFF12312C)
val TealBackgroundLight = Color(0xFFF5FAF9)
val TealOnBackgroundLight = Color(0xFF12312C)
val TealSurfaceLight = Color(0xFFFBFDFC)
val TealOnSurfaceLight = Color(0xFF12312C)
val TealSurfaceVariantLight = Color(0xFFE3F1EE)
val TealOnSurfaceVariantLight = Color(0xFF4F7A73)
val TealOutlineVariantLight = Color(0xFFC7DEDA)
val TealErrorLight = Color(0xFFE4572E)
val TealOnErrorLight = Color(0xFFFFFFFF)
val TealErrorContainerLight = Color(0xFFFBE4DB)
val TealOnErrorContainerLight = Color(0xFF5C2110)

// ---- Ocean Teal — dark ----
val TealPrimaryDark = Color(0xFF5FD1C0)
val TealOnPrimaryDark = Color(0xFF00332D)
val TealPrimaryContainerDark = Color(0xFF0E5C53)
val TealOnPrimaryContainerDark = Color(0xFFCFF1EC)
val TealSecondaryDark = Color(0xFF9DC2BB)
val TealSecondaryContainerDark = Color(0xFF24413C)
val TealOnSecondaryContainerDark = Color(0xFFD7ECE8)
val TealBackgroundDark = Color(0xFF0F1614)
val TealOnBackgroundDark = Color(0xFFE3EFEC)
val TealSurfaceDark = Color(0xFF141C1A)
val TealOnSurfaceDark = Color(0xFFE3EFEC)
val TealSurfaceVariantDark = Color(0xFF1E2A27)
val TealOnSurfaceVariantDark = Color(0xFFA9C2BD)
val TealOutlineVariantDark = Color(0xFF2E3D3A)
val TealErrorDark = Color(0xFFFF8A65)
val TealOnErrorDark = Color(0xFF4A1B0A)
val TealErrorContainerDark = Color(0xFF5C2110)
val TealOnErrorContainerDark = Color(0xFFFFDBCB)

// ---- Semantic "positive" (settled / spending down) — kept separate from the
// teal accent so it still reads as its own signal, per app color scheme. ----
val PositiveGreenLight = Color(0xFF2E9E5B)
val PositiveContainerLight = Color(0xFFD8F5E6)
val PositiveGreenDark = Color(0xFF6FDB93)
val PositiveContainerDark = Color(0xFF1C4430)

/** Theme-aware "money coming back / good" green — use instead of a hardcoded hex. */
@Composable
@ReadOnlyComposable
fun positiveColor(): Color = if (isSystemInDarkTheme()) PositiveGreenDark else PositiveGreenLight

@Composable
@ReadOnlyComposable
fun positiveContainerColor(): Color = if (isSystemInDarkTheme()) PositiveContainerDark else PositiveContainerLight

// ---- Glass-card surface tokens (Insights). Real backdrop blur only applies on
// API 31+; below that this still reads fine as a plain translucent card. ----
@Composable
@ReadOnlyComposable
fun glassCardColor(): Color = if (isSystemInDarkTheme()) {
    Color(0xFF1B2624).copy(alpha = 0.72f)
} else {
    Color(0xFFFFFFFF).copy(alpha = 0.68f)
}

@Composable
@ReadOnlyComposable
fun glassBorderColor(): Color = if (isSystemInDarkTheme()) {
    Color(0xFF3A4B47).copy(alpha = 0.6f)
} else {
    Color(0xFFFFFFFF).copy(alpha = 0.85f)
}
