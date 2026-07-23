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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollToIndex
import org.junit.Rule
import org.junit.Test
import org.junit.Before

class AppNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before fun leaveFirstRunGuideIfNeeded() {
        composeRule.waitForIdle()
        if (composeRule.onAllNodes(hasTestTag("onboarding_skip")).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag("onboarding_skip").performClick()
            composeRule.waitForIdle()
        }
    }

    @Test
    fun bottomNavigationAndRegistrationFlowOpenAllMajorScreens() {
        composeRule.onNodeWithTag("nav_channel").performClick()
        composeRule.onNodeWithText("チャネル占有状況").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_monitor").performClick()
        composeRule.onNodeWithText("シグナルモニター").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_devices").performClick()
        composeRule.onNodeWithTag("add_device").performClick()
        composeRule.onNodeWithTag("add_manually").performClick()
        composeRule.onNodeWithText("機器を登録").assertIsDisplayed()
        composeRule.onNodeWithTag("device_name").performTextInput("UIテスト機器")
        composeRule.onNodeWithTag("registration_list").performScrollToIndex(5)
        composeRule.onNodeWithTag("bssid_0").performTextInput("02:00:00:00:00:01")
        composeRule.onNodeWithTag("registration_list").performScrollToIndex(6)
        composeRule.onNodeWithTag("save_device_bottom").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasTestTag("save_device_bottom")).fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithText("UIテスト機器").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("戻る").performClick()
        composeRule.onNodeWithText("登録済み機器").assertIsDisplayed()
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

    @Test
    fun backupAndRestoreScreenOpensFromSettings() {
        composeRule.onNodeWithTag("nav_settings").performClick()
        composeRule.onNodeWithText("バックアップと復元").performScrollTo().performClick()
        composeRule.onNodeWithText("全データをバックアップ").assertIsDisplayed()
        composeRule.onNodeWithText("ワークスペースをバックアップ").assertIsDisplayed()
        composeRule.onNodeWithText("バックアップから復元").assertIsDisplayed()
        composeRule.onNodeWithText("暗号化されません。", substring = true).assertIsDisplayed()
    }

    @Test
    fun exportScreenOpensAndOffersCsvColumnsAndReport() {
        composeRule.onNodeWithTag("nav_settings").performClick()
        composeRule.onNodeWithText("データのエクスポート").performScrollTo().performClick()
        composeRule.onNodeWithTag("export_screen").assertIsDisplayed()
        composeRule.onNodeWithText("登録機器CSV").assertIsDisplayed()
        composeRule.onNodeWithTag("column_settings").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("全選択").assertIsDisplayed()
        composeRule.onNodeWithText("完了").performClick()
        composeRule.onNodeWithText("簡易レポート").performClick()
        composeRule.onNodeWithText("メイン写真だけ").assertIsDisplayed()
        composeRule.onNodeWithTag("generate_report").assertIsDisplayed()
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
