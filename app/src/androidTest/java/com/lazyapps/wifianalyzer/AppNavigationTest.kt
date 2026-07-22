package com.lazyapps.wifianalyzer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class AppNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun bottomNavigationAndRegistrationFlowOpenAllMajorScreens() {
        composeRule.onNodeWithTag("nav_channel").performClick()
        composeRule.onNodeWithText("チャネル占有状況").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_monitor").performClick()
        composeRule.onNodeWithText("シグナルモニター").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_devices").performClick()
        composeRule.onNodeWithText("新規登録").performClick()
        composeRule.onNodeWithText("機器ラベルを読み取る").assertIsDisplayed()
        composeRule.onNodeWithTag("ocr_back").performClick()
        composeRule.onNodeWithText("登録済みデバイス").assertIsDisplayed()
    }

    @Test
    fun themeModesCanBeSelected() {
        composeRule.onNodeWithTag("nav_settings").performClick()
        selectTheme("LIGHT")
        selectTheme("DARK")
        selectTheme("SYSTEM")
        selectAccent("インディゴ")
        selectAccent("ブルー")
    }

    private fun selectTheme(mode: String) {
        val tag = "theme_$mode"
        composeRule.onNodeWithTag(tag).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasTestTag(tag).and(isSelected())).fetchSemanticsNodes().size == 1
        }
        composeRule.onNodeWithTag(tag).assertIsSelected()
    }

    private fun selectAccent(label: String) {
        composeRule.onNodeWithContentDescription(label).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasContentDescription(label).and(isSelected())).fetchSemanticsNodes().size == 1
        }
        composeRule.onNodeWithContentDescription(label).assertIsSelected()
    }
}
