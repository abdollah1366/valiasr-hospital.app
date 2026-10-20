package com.barati.razi.core.i18n

/**
 * زبان‌های بخش مراجعان. بخش کارکنان همیشه فارسی است.
 * ترجمه‌ها پیش از انتشار باید به تأیید بیمارستان برسند.
 */
enum class AppLanguage(val code: String, val isRtl: Boolean, val nativeName: String) {
    FA("fa", true, "فارسی"),
    AZ("az", false, "Azərbaycan dili"),
    EN("en", false, "English"),
    AR("ar", true, "العربية"),
    TR("tr", false, "Türkçe"),
    ;

    /** فونت وزیرمتن برای فارسی و عربی؛ زبان‌های لاتین با فونت سیستم (حروف ə، ş، ğ، ı کامل‌اند). */
    val usesPersianFont: Boolean get() = isRtl

    companion object {
        val DEFAULT = FA

        fun fromCode(code: String?): AppLanguage = entries.firstOrNull { it.code == code } ?: DEFAULT

        private val arabicCountryCodes = listOf(
            "964", "966", "971", "965", "974", "973", "968", "962", "961", "963", "970",
            "20", "218", "216", "213", "212", "249", "967",
        )

        /**
         * زبان پیشنهادی از روی پیش‌شماره کشور (شماره با + یا 00 یا بدون آن).
         * ایران و شماره‌های داخلی (۰۹…) فارسی، آذربایجان و نخجوان آذربایجانی، ترکیه ترکی،
         * کشورهای عربی عربی، بقیه انگلیسی؛ شماره ناشناس فارسی.
         */
        fun forPhone(phone: String): AppLanguage {
            val raw = phone.trim()
            val digits = raw.filter(Char::isDigit)
            if (digits.isEmpty()) return DEFAULT
            val international = when {
                raw.startsWith("+") -> digits
                digits.startsWith("00") -> digits.drop(2)
                digits.startsWith("0") -> return FA
                else -> digits
            }
            return when {
                international.startsWith("98") -> FA
                international.startsWith("994") -> AZ
                international.startsWith("90") -> TR
                arabicCountryCodes.any { international.startsWith(it) } -> AR
                international.length < 7 -> DEFAULT
                else -> EN
            }
        }
    }
}
