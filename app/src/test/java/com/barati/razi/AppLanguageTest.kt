package com.barati.razi

import com.barati.razi.core.i18n.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test
    fun languageFromPhonePrefix() {
        assertEquals(AppLanguage.FA, AppLanguage.forPhone("+989121234567"))
        assertEquals(AppLanguage.FA, AppLanguage.forPhone("09121234567"))
        assertEquals(AppLanguage.AZ, AppLanguage.forPhone("+994 50 123 45 67"))
        assertEquals(AppLanguage.AZ, AppLanguage.forPhone("0099450123456"))
        assertEquals(AppLanguage.TR, AppLanguage.forPhone("+905321234567"))
        assertEquals(AppLanguage.AR, AppLanguage.forPhone("+9647701234567"))
        assertEquals(AppLanguage.AR, AppLanguage.forPhone("+201001234567"))
        assertEquals(AppLanguage.EN, AppLanguage.forPhone("+37491123456"))
        assertEquals(AppLanguage.EN, AppLanguage.forPhone("+4915112345678"))
    }

    @Test
    fun unknownFallsBackToPersian() {
        assertEquals(AppLanguage.FA, AppLanguage.forPhone(""))
        assertEquals(AppLanguage.FA, AppLanguage.forPhone("+12"))
        assertEquals(AppLanguage.FA, AppLanguage.fromCode(null))
        assertEquals(AppLanguage.FA, AppLanguage.fromCode("de"))
        assertEquals(AppLanguage.TR, AppLanguage.fromCode("tr"))
    }
}
