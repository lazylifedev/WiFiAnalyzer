package com.lazyapps.wifianalyzer

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AppNavigationTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun leaveFirstRunGuideIfNeeded() {
        composeRule.waitForIdle()
        if (composeRule.onAllNodes(hasTestTag("onboarding_skip")).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag("onboarding_skip").performClick()
            composeRule.waitForIdle()
        }
    }

    @Test
    fun bottomNavigationOpensAllMajorScreens() {
        listOf(
            "nav_home" to "home_screen",
            "nav_channel" to "channel_screen",
            "nav_monitor" to "monitor_screen",
            "nav_devices" to "devices_screen",
            "nav_settings" to "settings_screen",
        ).forEach { (navigationTag, screenTag) ->
            composeRule.onNodeWithTag(navigationTag).performClick()
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodes(hasTestTag(screenTag)).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(screenTag).assertIsDisplayed()
        }
    }

    @Test
    fun themeModesCanBeSelected() {
        composeRule.onNodeWithTag("nav_settings").performClick()
        listOf("LIGHT", "DARK", "SYSTEM").forEach { mode ->
            val tag = "theme_$mode"
            composeRule.onNodeWithTag(tag).performClick()
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodes(hasTestTag(tag).and(isSelected())).fetchSemanticsNodes().size == 1
            }
            composeRule.onNodeWithTag(tag).assertIsSelected()
        }
    }
}
