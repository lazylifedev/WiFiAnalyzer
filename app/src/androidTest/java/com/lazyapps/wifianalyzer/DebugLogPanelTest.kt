package com.lazyapps.wifianalyzer

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import com.lazyapps.wifianalyzer.debug.DebugLogCategory
import com.lazyapps.wifianalyzer.debug.DebugLogPanel
import com.lazyapps.wifianalyzer.debug.DebugLogStore
import com.lazyapps.wifianalyzer.ui.screens.settings.SettingsScreen
import com.lazyapps.wifianalyzer.ui.theme.ThemeUiState
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

        composeRule.onNodeWithTag("settings_list").performScrollToIndex(17)
        composeRule.onNodeWithTag("debug_display_switch").assertExists()
        composeRule.onNodeWithText("デバッグ表示").assertExists()
    }

    private fun store() = DebugLogStore(
        enabled = { true },
        wallClock = { 1L },
        elapsedRealtime = { 1L },
    )
}
