package com.barati.razi.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * رنگ‌های بیمارستان ولیعصر (docs: design-system.md).
 * سبز-آبی رنگ اقدام است؛ قرمز برند فقط برای هویت (نوار زیر هدر، لوگو، نشان «جدید») و هرگز برای دکمه یا خطا.
 */
object ValiasrColors {
    val Primary = Color(0xFF1F7A72)
    val PrimaryHover = Color(0xFF19665F)
    val PrimaryPressed = Color(0xFF194D48)
    val Calm = Color(0xFFE6F3F1)
    val Focus = Color(0xFF64D8CE)

    val Brand = Color(0xFFB3262E)
    val BrandSoft = Color(0xFFFBEDEE)

    val Gold = Color(0xFF8C6A2B)
    val GoldDeco = Color(0xFFC9A15A)
    val Cream = Color(0xFFFBF6EC)

    val Bg = Color(0xFFF6F8F7)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceVariant = Color(0xFFECEEEE)
    val Border = Color(0xFFD6DCDB)
    val Text = Color(0xFF1E2A2B)
    val Text2 = Color(0xFF5B6B6A)
    val Disabled = Color(0xFF9AA8A7)

    val Success = Color(0xFF2E7D3E)
    val SuccessSoft = Color(0xFFE7F3EA)
    val Error = Color(0xFFB9341C)
    val ErrorSoft = Color(0xFFFBECE8)
    val Warning = Color(0xFFD08A11)
    val WarningText = Color(0xFF8A4B08)
    val WarningSoft = Color(0xFFFDF3E3)
    val Info = Color(0xFF2F6DB5)
    val InfoSoft = Color(0xFFE8F0FA)
}

/** نسخه تیره برای شیفت شب: رنگ‌ها روشن‌تر و کم‌اشباع‌تر. */
object ValiasrDarkColors {
    val Primary = Color(0xFF5CC8BC)
    val OnPrimary = Color(0xFF0F1A1A)
    val Calm = Color(0xFF173130)
    val Brand = Color(0xFFE5747A)
    val BrandSoft = Color(0xFF3A1F22)
    val Gold = Color(0xFFC9A15A)
    val Cream = Color(0xFF2A2418)
    val Bg = Color(0xFF121A1B)
    val Surface = Color(0xFF1B2526)
    val SurfaceVariant = Color(0xFF243031)
    val Border = Color(0xFF2C3A3B)
    val Text = Color(0xFFE6EDEC)
    val Text2 = Color(0xFFA9B7B6)
    val Success = Color(0xFF6CC17E)
    val Error = Color(0xFFF08A72)
    val Warning = Color(0xFFE8B04A)
    val Info = Color(0xFF7FB0EA)
}

/** رنگ قرمز برند متناسب با حالت روشن یا تیره (همان tertiary در colorScheme). */
val brandColor: Color
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.tertiary
