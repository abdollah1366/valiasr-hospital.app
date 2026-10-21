package com.barati.razi

import com.barati.razi.core.data.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {
    @Test
    fun defaultsToLightEvenWithoutSavedChoice() {
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromCode(null))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromCode("junk"))
    }

    @Test
    fun savedChoiceIsRestored() {
        assertEquals(ThemeMode.DARK, ThemeMode.fromCode("dark"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromCode("system"))
    }
}
