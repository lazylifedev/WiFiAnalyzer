package com.lazyapps.wifianalyzer

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import com.lazyapps.wifianalyzer.ui.screens.settings.SettingsScreen
import com.lazyapps.wifianalyzer.ui.theme.ThemeUiState
import org.junit.Rule
import org.junit.Test

class LanguageSettingsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun languageDialogOffersOnlySupportedChoices() {
        rule.setContent { MaterialTheme { SettingsScreen(ThemeUiState(), {}, {}, {}) } }
        rule.onNodeWithTag("settings_screen").performScrollToIndex(7)
        rule.onNodeWithTag("app_language").performClick()
        rule.onNodeWithTag("app_language_system").fetchSemanticsNode()
        rule.onNodeWithTag("app_language_ja").fetchSemanticsNode()
        rule.onNodeWithTag("app_language_en").fetchSemanticsNode()
    }
}
