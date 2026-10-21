package com.barati.razi.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * رنگ‌های بیمارستان ولیعصر (docs: design-system.md).
 * رنگ اصلی و اقدام: آبی نفتی (برای متن و دکمه، کنتراست کافی)؛ رنگ برند سبز وردانت (نوار زیر هدر، لوگو، نشان‌ها) و هرگز برای متن یا خطا.
 * منبع: کیت رسمی برند بیمارستان (VALIASR_BRAND_REFERENCE.md، صفحه ۲۶ و ۳۰).
 */
object ValiasrColors {
    val Primary = Color(0xFF01687F)
    val PrimaryHover = Color(0xFF015569)
    val PrimaryPressed = Color(0xFF014354)
    val Calm = Color(0xFFE0F2F0)
    val Focus = Color(0xFF64D8CE)

    val Brand = Color(0xFF019681)
    val BrandSoft = Color(0xFFE0F2F0)
    /** سبز تیره‌تر برای پیوند و متن کوچک روی سفید (کنتراست ۵٫۲۵:۱). */
    val Link = Color(0xFF017A6B)
    /** شیب صفحه آغاز و کارت اصلی خانه: سبز برند به آبی نفتی و سرمه‌ای. */
    val GradientStart = Color(0xFF019681)
    val GradientEnd = Color(0xFF013A48)

    val Gold = Color(0xFF361E58)
    val GoldDeco = Color(0xFFFFDC6F)
    val Cream = Color(0xFFFBF6EC)

    val Bg = Color(0xFFEAF2F2)
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
    val Brand = Color(0xFF4FC3B2)
    val BrandSoft = Color(0xFF173130)
    val Gold = Color(0xFFFFDC6F)
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

/** رنگ برند (سبز وردانت) متناسب با حالت روشن یا تیره (همان tertiary در colorScheme). */
val brandColor: Color
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.tertiary
