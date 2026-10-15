package com.captainduckay.dictator.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** The captains-chest.com palette: night sea, wood boards, brass and moss (ADR 0009). */
internal object Chest {
    val Sea = Color(0xFF071918)
    val SeaDeep = Color(0xFF04090A)
    val SeaGlow = Color(0x59286E50)
    val Ink = Color(0xFF050807)
    val Cream = Color(0xFFF4E6C8)
    val CreamBright = Color(0xFFFFF3D6)
    val Muted = Color(0xFFD6BD8A)
    val Eyebrow = Color(0xFFB9E891)
    val Accent = Color(0xFFB6E08A)
    val MossTop = Color(0xFF487639)
    val Moss = Color(0xFF3F6A31)
    val MossBottom = Color(0xFF355C2D)
    val BoardTop = Color(0xFF4D341D)
    val BoardBottom = Color(0xFF33210F)
    val PlankTop = Color(0xFF61432A)
    val PlankMid = Color(0xFF3F2915)
    val PlankBottom = Color(0xFF2D1D0F)
    val CoinLight = Color(0xFFF6DC9C)
    val Coin = Color(0xFFC49A4E)
    val CoinDark = Color(0xFF8A6326)
    val Gold = Color(0xFFE7C06A)
    val Brown = Color(0xFF2A1A0A)
    val Rivet = Color(0xFFECD093)
    val Parchment = Color(0xFFF6E6C0)
    val ParchmentDark = Color(0xFFEAD2A0)
    val ParchmentInk = Color(0xFF3A2814)
    val ParchmentHint = Color(0xFF8A6A3E)
    val Slate = Color(0xFF1F1B15)
    val SlateDark = Color(0xFF14110D)
    val Rust = Color(0xFFE08A5A)
}

private val colors =
    darkColorScheme(
        // Material controls (dialog buttons, radios, checkboxes, progress) use the bright moss accent;
        // the big buttons are drawn in deep moss by ChestButton.
        primary = Chest.Accent,
        onPrimary = Chest.Ink,
        primaryContainer = Chest.MossBottom,
        onPrimaryContainer = Chest.Cream,
        secondary = Chest.Gold,
        onSecondary = Chest.Brown,
        tertiary = Chest.Accent,
        onTertiary = Chest.Ink,
        background = Chest.Sea,
        onBackground = Chest.Cream,
        surface = Chest.Sea,
        onSurface = Chest.Cream,
        surfaceVariant = Chest.BoardBottom,
        onSurfaceVariant = Chest.Muted,
        surfaceContainerHigh = Chest.PlankBottom,
        surfaceContainerHighest = Chest.PlankMid,
        outline = Chest.Ink,
        outlineVariant = Chest.CoinDark,
        error = Chest.Rust,
        onError = Chest.Ink,
    )

/** A serif for names and titles, like the site's Iowan Old Style; the system sans for everything else. */
internal val Serif = FontFamily.Serif

private val type =
    Typography().run {
        copy(
            headlineSmall = headlineSmall.copy(fontFamily = Serif, fontWeight = FontWeight.Bold),
            titleLarge = titleLarge.copy(fontFamily = Serif, fontWeight = FontWeight.Bold),
            titleMedium = titleMedium.copy(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 17.sp),
            titleSmall = titleSmall.copy(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 16.sp),
            labelSmall =
                TextStyle(fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.6.sp, lineHeight = 14.sp),
        )
    }

/** One fixed look in light and dark mode: the night sea of captains-chest.com (ADR 0009). */
@Composable
fun DictatorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = type, content = content)
}
