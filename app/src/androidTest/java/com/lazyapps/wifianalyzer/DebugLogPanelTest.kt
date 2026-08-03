package com.lazyapps.wifianalyzer

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import com.lazyapps.wifianalyzer.debug.DebugLogCategory
import com.lazyapps.wifianalyzer.debug.DebugActionColor
import com.lazyapps.wifianalyzer.debug.DebugLogBackground
import com.lazyapps.wifianalyzer.debug.DebugLogPanel
import com.lazyapps.wifianalyzer.debug.DebugLogStore
import com.lazyapps.wifianalyzer.debug.DebugPanelBackground
import com.lazyapps.wifianalyzer.debug.DebugPrimaryText
import com.lazyapps.wifianalyzer.ui.screens.settings.SettingsScreen
import com.lazyapps.wifianalyzer.ui.theme.AccentColor
import com.lazyapps.wifianalyzer.ui.theme.ThemeMode
import com.lazyapps.wifianalyzer.ui.theme.ThemeUiState
import com.lazyapps.wifianalyzer.ui.theme.WifiAnalyzerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DebugLogPanelTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun disabledHidesPanelAndEnabledShowsIt() {
        val enabled = mutableStateOf(false)
        val store = store()
        composeRule.setContent {
            MaterialTheme {
                if (enabled.value) DebugLogPanel(store)
            }
        }
        composeRule.onNodeWithTag("debug_log_panel").assertDoesNotExist()

        composeRule.runOnIdle { enabled.value = true }
        composeRule.onNodeWithTag("debug_log_panel").assertIsDisplayed()
    }

    @Test
    fun clearAndCollapseWork() {
        val store = store()
        store.add(DebugLogCategory.STATE, "READY")
        composeRule.setContent { MaterialTheme { DebugLogPanel(store) } }

        composeRule.onNodeWithText("READY", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("debug_log_toggle").performClick()
        composeRule.onNodeWithTag("debug_log_entries").assertDoesNotExist()
        composeRule.onNodeWithTag("debug_log_toggle").performClick()
        composeRule.onNodeWithTag("debug_log_clear").performClick()
        composeRule.onNodeWithText("DEBUG LOG (0)").assertIsDisplayed()
    }

    @Test
    fun panelContentIsVisibleInLightDarkAndEveryAccent() {
        val store = store()
        store.add(DebugLogCategory.STATE, "READABLE_LOG_ENTRY")
        val mode = mutableStateOf(ThemeMode.LIGHT)
        val accent = mutableStateOf(AccentColor.BLUE)
        composeRule.setContent {
            WifiAnalyzerTheme(mode = mode.value, accent = accent.value) {
                DebugLogPanel(store)
            }
        }

        listOf(ThemeMode.LIGHT, ThemeMode.DARK).forEach { targetMode ->
            AccentColor.entries.forEach { targetAccent ->
                composeRule.runOnIdle {
                    mode.value = targetMode
                    accent.value = targetAccent
                }
                composeRule.onNodeWithTag("debug_log_panel").assertIsDisplayed()
                composeRule.onNodeWithTag("debug_log_entries").assertIsDisplayed()
                composeRule.onNodeWithText("READABLE_LOG_ENTRY", substring = true).assertIsDisplayed()
                composeRule.onNodeWithTag("debug_log_copy").assertIsDisplayed()
                composeRule.onNodeWithTag("debug_log_clear").assertIsDisplayed()
                composeRule.onNodeWithTag("debug_log_toggle").assertIsDisplayed()
            }
        }
    }

    @Test
    fun fixedPaletteMeetsContrastAndOpacityRequirements() {
        assertEquals(1f, DebugLogBackground.alpha)
        assertTrue(DebugPanelBackground.alpha >= 0.94f)
        assertTrue(contrastRatio(DebugPrimaryText, DebugLogBackground) >= 4.5f)
        assertTrue(contrastRatio(DebugPrimaryText, DebugPanelBackground) >= 4.5f)
        assertTrue(contrastRatio(DebugActionColor, DebugPanelBackground) >= 3f)
    }

    @Test
    fun settingsDeveloperSwitchIsDebugOnlyContent() {
        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    state = ThemeUiState(),
                    onModeChange = {},
                    onAccentChange = {},
                    onAnimationChange = {},
                    debugDisplayEnabled = false,
                    onDebugDisplayEnabledChange = {},
                )
            }
        }

        composeRule.onNodeWithTag("settings_screen").performScrollToIndex(17)
        composeRule.onNodeWithTag("debug_display_switch").assertExists()
    }

    private fun store() = DebugLogStore(
        enabled = { true },
        wallClock = { 1L },
        elapsedRealtime = { 1L },
    )

    private fun contrastRatio(foreground: Color, background: Color): Float {
        val lighter = maxOf(foreground.luminance(), background.luminance())
        val darker = minOf(foreground.luminance(), background.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }
}
