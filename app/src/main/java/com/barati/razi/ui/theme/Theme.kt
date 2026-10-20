package com.barati.razi.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
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

/** فونت Raleway برای زبان‌های لاتین مراجعان (آذربایجانی، انگلیسی، ترکی). */
val Raleway = FontFamily(
    Font(R.font.raleway_regular, FontWeight.Normal),
    Font(R.font.raleway_medium, FontWeight.Medium),
    Font(R.font.raleway_bold, FontWeight.Bold),
)

private val LightColors = lightColorScheme(
    primary = ValiasrColors.Primary,
    onPrimary = Color.White,
    primaryContainer = ValiasrColors.Calm,
    onPrimaryContainer = ValiasrColors.PrimaryPressed,
    secondary = ValiasrColors.Gold,
    onSecondary = Color.White,
    secondaryContainer = ValiasrColors.Cream,
    onSecondaryContainer = Color(0xFF2A1640),
    tertiary = ValiasrColors.Brand,
    onTertiary = Color.White,
    tertiaryContainer = ValiasrColors.BrandSoft,
    onTertiaryContainer = Color(0xFF5C1217),
    error = ValiasrColors.Error,
    onError = Color.White,
    errorContainer = ValiasrColors.ErrorSoft,
    onErrorContainer = Color(0xFF5A1A0D),
    background = ValiasrColors.Bg,
    onBackground = ValiasrColors.Text,
    surface = ValiasrColors.Surface,
    onSurface = ValiasrColors.Text,
    surfaceVariant = ValiasrColors.SurfaceVariant,
    onSurfaceVariant = ValiasrColors.Text2,
    outline = ValiasrColors.Border,
    outlineVariant = ValiasrColors.Border,
    surfaceContainerLowest = ValiasrColors.Surface,
    surfaceContainerLow = ValiasrColors.Surface,
    surfaceContainer = ValiasrColors.Surface,
    surfaceContainerHigh = ValiasrColors.SurfaceVariant,
    surfaceContainerHighest = ValiasrColors.SurfaceVariant,
)

private val DarkColors = darkColorScheme(
    primary = ValiasrDarkColors.Primary,
    onPrimary = ValiasrDarkColors.OnPrimary,
    primaryContainer = ValiasrDarkColors.Calm,
    onPrimaryContainer = Color(0xFF95E4DD),
    secondary = ValiasrDarkColors.Gold,
    onSecondary = Color(0xFF1E1606),
    secondaryContainer = ValiasrDarkColors.Cream,
    onSecondaryContainer = Color(0xFFE6D8F0),
    tertiary = ValiasrDarkColors.Brand,
    onTertiary = Color(0xFF2A0A0C),
    tertiaryContainer = ValiasrDarkColors.BrandSoft,
    onTertiaryContainer = Color(0xFFF6C9CC),
    error = ValiasrDarkColors.Error,
    onError = Color(0xFF2A0D06),
    errorContainer = Color(0xFF3A2019),
    onErrorContainer = Color(0xFFF8C7BA),
    background = ValiasrDarkColors.Bg,
    onBackground = ValiasrDarkColors.Text,
    surface = ValiasrDarkColors.Surface,
    onSurface = ValiasrDarkColors.Text,
    surfaceVariant = ValiasrDarkColors.SurfaceVariant,
    onSurfaceVariant = ValiasrDarkColors.Text2,
    outline = ValiasrDarkColors.Border,
    outlineVariant = ValiasrDarkColors.Border,
    surfaceContainerLowest = ValiasrDarkColors.Bg,
    surfaceContainerLow = ValiasrDarkColors.Surface,
    surfaceContainer = ValiasrDarkColors.Surface,
    surfaceContainerHigh = ValiasrDarkColors.SurfaceVariant,
    surfaceContainerHighest = ValiasrDarkColors.SurfaceVariant,
)

private fun TextStyle.withFont() = copy(fontFamily = Vazirmatn)

private val Base = Typography()

/** تایپوگرافی Raleway برای زبان‌های لاتین بخش مراجعان. */
val LatinTypography: Typography = Typography(
    displayLarge = Base.displayLarge.copy(fontFamily = Raleway),
    displayMedium = Base.displayMedium.copy(fontFamily = Raleway),
    displaySmall = Base.displaySmall.copy(fontFamily = Raleway),
    headlineLarge = Base.headlineLarge.copy(fontFamily = Raleway),
    headlineMedium = Base.headlineMedium.copy(fontFamily = Raleway),
    headlineSmall = Base.headlineSmall.copy(fontFamily = Raleway),
    titleLarge = Base.titleLarge.copy(fontFamily = Raleway),
    titleMedium = Base.titleMedium.copy(fontFamily = Raleway),
    titleSmall = Base.titleSmall.copy(fontFamily = Raleway),
    bodyLarge = Base.bodyLarge.copy(fontFamily = Raleway),
    bodyMedium = Base.bodyMedium.copy(fontFamily = Raleway),
    bodySmall = Base.bodySmall.copy(fontFamily = Raleway),
    labelLarge = Base.labelLarge.copy(fontFamily = Raleway),
    labelMedium = Base.labelMedium.copy(fontFamily = Raleway),
    labelSmall = Base.labelSmall.copy(fontFamily = Raleway),
)
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

/** تم اپ؛ کل رابط کاربری راست‌چین است، صرف‌نظر از زبان گوشی. حالت تیره تابع تنظیم گوشی است. */
@Composable
fun RaziTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, typography = RaziTypography, content = content)
    }
}
