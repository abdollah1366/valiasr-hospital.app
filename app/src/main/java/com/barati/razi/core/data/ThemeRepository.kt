package com.barati.razi.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** حالت نمایش اپ. پیش‌فرض روشن است، حتی اگر گوشی روی حالت تیره باشد. */
enum class ThemeMode(val code: String) {
    LIGHT("light"),
    DARK("dark"),
    SYSTEM("system"),
    ;

    companion object {
        val DEFAULT = LIGHT

        fun fromCode(code: String?): ThemeMode = entries.firstOrNull { it.code == code } ?: DEFAULT
    }
}

class ThemeRepository(private val store: KeyValueStore, scope: CoroutineScope) {
    val mode: StateFlow<ThemeMode> = store.observe(Keys.THEME_MODE)
        .map(ThemeMode::fromCode)
        .stateIn(scope, SharingStarted.Eagerly, ThemeMode.DEFAULT)

    suspend fun set(mode: ThemeMode) = store.put(Keys.THEME_MODE, mode.code)
}
