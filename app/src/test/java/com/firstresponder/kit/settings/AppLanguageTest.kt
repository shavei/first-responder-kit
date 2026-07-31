package com.firstresponder.kit.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLanguageTest {

    @Test
    fun `unknown and missing stored values fall back to following the device`() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromStorageName(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromStorageName("klingon"))
    }

    @Test
    fun `every language round-trips through its stored name`() {
        AppLanguage.entries.forEach { language ->
            assertEquals(language, AppLanguage.fromStorageName(language.storageName))
        }
    }

    @Test
    fun `following the device means no locale of our own`() {
        assertNull(AppLanguage.SYSTEM.locale)
    }

    @Test
    fun `each translated language carries the tag its resources are keyed by`() {
        // Hebrew is tagged "he" here and its resources live in values-iw: Android reports
        // Hebrew locales under the historical code, which is why AndroidX ships its own
        // Hebrew strings there too. The tags below are what the locales are built from, so
        // a typo in one is a language that silently falls back to English.
        assertEquals("he", AppLanguage.HEBREW.languageTag)
        assertEquals("en", AppLanguage.ENGLISH.languageTag)
        assertEquals("he", AppLanguage.HEBREW.locale?.toLanguageTag())
    }
}
