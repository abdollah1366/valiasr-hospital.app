package com.barati.razi.core.data

import com.barati.razi.core.i18n.AppLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** زبان بخش مراجعان که روی گوشی ذخیره می‌شود؛ پیش‌فرض فارسی. */
class LanguageRepository(private val store: KeyValueStore, scope: CoroutineScope) {
    val language: StateFlow<AppLanguage> = store.observe(Keys.PATIENT_LANGUAGE)
        .map(AppLanguage::fromCode)
        .stateIn(scope, SharingStarted.Eagerly, AppLanguage.DEFAULT)

    suspend fun set(language: AppLanguage) = store.put(Keys.PATIENT_LANGUAGE, language.code)
}
