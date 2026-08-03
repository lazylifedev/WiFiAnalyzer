package com.lazyapps.wifianalyzer

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.os.LocaleListCompat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LanguageApplicationLocaleTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @After fun restoreSystemDefault() {
        rule.activity.runOnUiThread { AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList()) }
    }

    @Test fun englishAndJapaneseSelectionsReachThePlatformLocaleManager() {
        setAndAwait("en")
        setAndAwait("ja")
        setAndAwait("")
    }

    private fun setAndAwait(tag: String) {
        rule.activity.runOnUiThread {
            AppCompatDelegate.setApplicationLocales(
                if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag),
            )
        }
        rule.waitUntil(10_000) { AppCompatDelegate.getApplicationLocales().toLanguageTags() == tag }
        assertEquals(tag, AppCompatDelegate.getApplicationLocales().toLanguageTags())
    }
}
