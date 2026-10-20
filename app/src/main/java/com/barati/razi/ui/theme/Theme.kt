package com.barati.razi.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import com.barati.razi.R

val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

private val Colors = lightColorScheme(
    primary = Color(0xFF0B6E79),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEBEE),
    onPrimaryContainer = Color(0xFF002024),
    secondary = Color(0xFF4A6366),
    secondaryContainer = Color(0xFFCCE8EA),
    tertiary = Color(0xFF8A5100),
    tertiaryContainer = Color(0xFFFFDDB8),
    error = Color(0xFFB3261E),
    background = Color(0xFFF6FAFA),
    surface = Color(0xFFF6FAFA),
    surfaceVariant = Color(0xFFDAE4E5),
)

private fun TextStyle.withFont() = copy(fontFamily = Vazirmatn)

private val Base = Typography()
private val RaziTypography = Typography(
    displayLarge = Base.displayLarge.withFont(),
    displayMedium = Base.displayMedium.withFont(),
    displaySmall = Base.displaySmall.withFont(),
    headlineLarge = Base.headlineLarge.withFont(),
    headlineMedium = Base.headlineMedium.withFont(),
    headlineSmall = Base.headlineSmall.withFont(),
    titleLarge = Base.titleLarge.withFont(),
    titleMedium = Base.titleMedium.withFont(),
    titleSmall = Base.titleSmall.withFont(),
    bodyLarge = Base.bodyLarge.withFont(),
    bodyMedium = Base.bodyMedium.withFont(),
    bodySmall = Base.bodySmall.withFont(),
    labelLarge = Base.labelLarge.withFont(),
    labelMedium = Base.labelMedium.withFont(),
    labelSmall = Base.labelSmall.withFont(),
)

/** تم اپ؛ کل رابط کاربری راست‌چین است، صرف‌نظر از زبان گوشی. */
@Composable
fun RaziTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = Colors, typography = RaziTypography, content = content)
    }
}
