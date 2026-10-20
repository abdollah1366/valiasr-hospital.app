package com.barati.razi.ui.i18n

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.barati.razi.core.i18n.AppLanguage
import com.barati.razi.ui.theme.LatinTypography
import java.util.Locale

/** زبان محتوای فعلی؛ بیرون از بخش مراجعان همیشه فارسی است. */
val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.FA }

/**
 * محتوا را به زبان داده‌شده نشان می‌دهد: منابع رشته (stringResource)، جهت متن و فونت.
 * زبان گوشی اثری ندارد؛ کل اپ با FA پیچیده می‌شود و فقط صفحه‌های مراجعان زبان انتخابی را می‌گیرند.
 */
@Composable
fun LocalizedContent(language: AppLanguage, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val baseConfig = LocalConfiguration.current
    val localized = remember(base, baseConfig, language) { LocalizedContext(base, baseConfig, language) }
    CompositionLocalProvider(
        LocalContext provides localized,
        LocalConfiguration provides localized.resources.configuration,
        LocalLayoutDirection provides if (language.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
        LocalAppLanguage provides language,
    ) {
        if (language.usesPersianFont) {
            content()
        } else {
            MaterialTheme(colorScheme = MaterialTheme.colorScheme, typography = LatinTypography, content = content)
        }
    }
}

/** Context اصلی (Activity) حفظ می‌شود و فقط منابع به زبان دیگر عوض می‌شوند. */
private class LocalizedContext(base: Context, baseConfig: Configuration, language: AppLanguage) : ContextWrapper(base) {
    private val localizedResources: Resources = run {
        val locale = Locale.forLanguageTag(language.code)
        val config = Configuration(baseConfig).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        base.createConfigurationContext(config).resources
    }

    override fun getResources(): Resources = localizedResources
}
