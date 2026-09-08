package com.zai.chat.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ── Layer 0: Canvas (Emissive Off & Midnight) ──────────────────────────────
val CanvasPureBlack = Color(0xFF000000)
val MidnightObsidian = Color(0xFF08080C)
val TrueBlack = CanvasPureBlack
val ObsidianBase = MidnightObsidian

// ── Layer 1: Structural Surfaces & Elevation Cards ──────────────────────────
val SurfaceBase = Color(0xFF0F0F16)
val SurfaceRaised = Color(0xFF151520)
val SurfaceActive = Color(0xFF1D1D2C)
val BorderAmbient = Color(0xFF232334)

// Legacy alias compatibility
val SurfaceContainerDark = SurfaceBase
val SurfaceContainerHighDark = SurfaceRaised
val SurfaceContainerHighestDark = SurfaceActive
val BorderSubtleDark = BorderAmbient

// ── Layer 2: Glassmorphism & Specular Highlights ───────────────────────────
val GlassIslandBackground = Color(0xE012121C)
val GlassIslandBorder = Color(0x33FFFFFF)
val ModalSheet = Color(0xF50E0E17)

val SpecularEdgeTop = Color(0x1FFFFFFF)
val SpecularEdgeBottom = Color(0x05FFFFFF)
val SpecularGradientBrush = Brush.verticalGradient(
    colors = listOf(SpecularEdgeTop, SpecularEdgeBottom)
)

// ── Layer 3: Quantum Accents (Zero-Muted Pigments) ──────────────────────────
val RadiantAmber = Color(0xFFF59E0B)
val RadiantAmberGlow = Color(0x33F59E0B)
val RadiantAmberContainer = Color(0xFF2E1C06)

val QuantumCyan = Color(0xFF06B6D4)
val QuantumCyanGlow = Color(0x3306B6D4)
val QuantumCyanContainer = Color(0xFF062833)

val EmeraldPulse = Color(0xFF10B981)
val EmeraldPulseGlow = Color(0x3310B981)
val CrimsonFlare = Color(0xFFEF4444)
val CrimsonFlareGlow = Color(0x33EF4444)

// Claude & Kimi color aliases
val ClaudePeach = RadiantAmber
val ClaudePeachDark = Color(0xFFD97706)
val ClaudePeachSubtle = Color(0xFF26180B)
val ClaudePeachContainerDark = RadiantAmberContainer
val ClaudePeachOnContainerDark = Color(0xFFFFE8D6)
val ClaudePeachContainerLight = Color(0xFFFFEDE6)
val ClaudePeachOnContainerLight = Color(0xFF421508)

val KimiCyan = QuantumCyan
val KimiCyanSubtle = QuantumCyanContainer

// ── Layer 4: Editorial Typography (Alabaster Contrast) ─────────────────────
val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)
val TextTertiary = Color(0xFF64748B)

val TextPrimaryDark = TextPrimary
val TextSecondaryDark = TextSecondary
val TextTertiaryDark = TextTertiary

val UserBubbleFillDark = SurfaceRaised
val ThinkingBackgroundDark = SurfaceBase
val ThinkingBorderDark = BorderAmbient

// ── Light Theme Tokens ─────────────────────────────────────────────────────
val PureWhite = Color(0xFFFFFFFF)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceContainerLight = Color(0xFFF1F5F9)
val TextPrimaryLight = Color(0xFF0F172A)
val TextSecondaryLight = Color(0xFF475569)
val TextTertiaryLight = Color(0xFF94A3B8)
val UserBubbleFillLight = Color(0xFFE2E8F0)
val BorderSubtleLight = Color(0xFFCBD5E1)

// ── Tokyo Night Storm Syntax Highlighting Palette ──────────────────────────
val SyntaxKeyword = Color(0xFFBB9AF7)
val SyntaxFunction = Color(0xFF7AA2F7)
val SyntaxString = Color(0xFF9ECE6A)
val SyntaxType = Color(0xFF2AC3DE)
val SyntaxNumber = Color(0xFFFF9E64)
val SyntaxComment = Color(0xFF565F89)
val SyntaxBackground = Color(0xFF0A0A10)
