package fr.virtualdiapo.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object VirtualDiapoColors {
    val DeepBlack = Color(0xFF080A0C)
    val Slate = Color(0xFF1A1C20)
    val WarmSlate = Color(0xFF2E3238)
    val Cream = Color(0xFFF5E7CF)
    val Champagne = Color(0xFFD5B078)
    val Amber = Color(0xFFE8A84C)
    val Copper = Color(0xFFC47444)
    val Error = Color(0xFFE29A82)
    val Success = Color(0xFF69C779)
}

@Composable
fun VirtualDiapoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = VirtualDiapoColors.Champagne,
            onPrimary = VirtualDiapoColors.DeepBlack,
            background = VirtualDiapoColors.DeepBlack,
            onBackground = VirtualDiapoColors.Cream,
            surface = VirtualDiapoColors.Slate,
            onSurface = VirtualDiapoColors.Cream,
            error = VirtualDiapoColors.Error,
        ),
        content = content,
    )
}
