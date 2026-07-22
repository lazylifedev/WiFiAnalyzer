package com.lazyapps.wifianalyzer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import org.junit.Rule
import org.junit.Test

class RegistryFlowTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun createGroupRegisterValidateDuplicateEditAndDelete() {
        val suffix = (System.currentTimeMillis() % 0xFFFF).toString(16).uppercase().padStart(4, '0')
        val groupName = "UIグループ$suffix"
        val deviceName = "登録機器$suffix"
        val editedName = "編集済み$suffix"
        val firstBssid = "02:10:20:30:${suffix.take(2)}:${suffix.takeLast(2)}"
        val secondBssid = "02:10:20:31:${suffix.take(2)}:${suffix.takeLast(2)}"

        composeRule.onNodeWithTag("nav_devices").performClick()
        composeRule.onNodeWithContentDescription("グループ管理").performClick()
        composeRule.onNodeWithTag("group_name_input").performTextInput(groupName)
        composeRule.onNodeWithTag("group_create").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(groupName).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("閉じる").performClick()

        composeRule.onNodeWithTag("add_device").performClick()
        composeRule.onNodeWithTag("add_manually").performClick()
        composeRule.onNodeWithTag("device_name").performTextInput(deviceName)
        composeRule.onNodeWithTag("registration_list").performScrollToIndex(2)
        composeRule.onNodeWithText(groupName).performClick()
        composeRule.onNodeWithTag("registration_list").performScrollToIndex(5)
        composeRule.onNodeWithTag("bssid_0").performTextInput(firstBssid)
        composeRule.onNodeWithTag("add_bssid").performClick()
        composeRule.onNodeWithTag("registration_list").performScrollToIndex(6)
        composeRule.onNodeWithTag("bssid_1").performTextInput(firstBssid.lowercase().replace(':', '-'))
        composeRule.onNodeWithTag("registration_list").performScrollToIndex(7)
        composeRule.onNodeWithTag("save_device_bottom").performClick()
        composeRule.onNodeWithTag("registration_list").performScrollToIndex(1)
        composeRule.onNodeWithText("同じBSSIDが複数入力されています").assertIsDisplayed()

        composeRule.onNodeWithTag("registration_list").performScrollToIndex(7)
        composeRule.onNodeWithTag("bssid_1").performTextReplacement(secondBssid)
        composeRule.onNodeWithTag("registration_list").performScrollToIndex(8)
        composeRule.onNodeWithTag("save_device_bottom").performClick()
        composeRule.waitUntil(5_000) {
            runCatching { composeRule.onNodeWithTag("edit_device").fetchSemanticsNode() }.isSuccess
        }
        composeRule.onNodeWithText(deviceName).assertIsDisplayed()
        composeRule.onNodeWithText(groupName).assertIsDisplayed()
        composeRule.activityRule.scenario.recreate()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText(deviceName).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText(deviceName).assertIsDisplayed()

        composeRule.onNodeWithTag("edit_device").performClick()
        composeRule.onNodeWithTag("device_name").performTextReplacement(editedName)
        composeRule.onNodeWithTag("save_device").performClick()
        composeRule.waitUntil(5_000) {
            runCatching { composeRule.onNodeWithText(editedName).fetchSemanticsNode() }.isSuccess
        }
        composeRule.onNodeWithTag("delete_device").performClick()
        composeRule.onNodeWithText("機器を削除しますか？").assertIsDisplayed()
        composeRule.onNodeWithText("削除").performClick()
        composeRule.waitUntil(5_000) {
            runCatching { composeRule.onNodeWithText("登録済みデバイス").fetchSemanticsNode() }.isSuccess
        }
        composeRule.onNodeWithText(editedName).assertDoesNotExist()
    }
}
