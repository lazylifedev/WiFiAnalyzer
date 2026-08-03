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
import org.junit.Before
import androidx.compose.ui.test.hasTestTag

class RegistryFlowTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Before fun leaveFirstRunGuideIfNeeded() {
        composeRule.waitForIdle()
        if (composeRule.onAllNodes(hasTestTag("onboarding_skip")).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag("onboarding_skip").performClick()
            composeRule.waitForIdle()
        }
    }

    @Test fun createGroupRegisterValidateDuplicateEditAndDelete() {
        val duplicateBssidMessage = composeRule.activity.getString(R.string.error_duplicate_bssid_input)
        val deleteTitle = composeRule.activity.getString(R.string.delete_device_title)
        val deleteLabel = composeRule.activity.getString(R.string.delete)
        val devicesTitle = composeRule.activity.getString(R.string.screen_devices)
        val suffix = (System.currentTimeMillis() % 0xFFFF).toString(16).uppercase().padStart(4, '0')
        val groupName = "UIグループ$suffix"
        val deviceName = "登録機器$suffix"
        val editedName = "編集済み$suffix"
        val firstBssid = "02:10:20:30:${suffix.take(2)}:${suffix.takeLast(2)}"
        val secondBssid = "02:10:20:31:${suffix.take(2)}:${suffix.takeLast(2)}"

        val context = composeRule.activity.applicationContext
        val database = com.lazyapps.wifianalyzer.data.registry.WifiAnalyzerDatabase.get(context)
        kotlinx.coroutines.runBlocking {
            com.lazyapps.wifianalyzer.data.registry.DeviceRegistryRepository(
                context,
                database,
                com.lazyapps.wifianalyzer.data.registry.WorkspaceRepository(context, database),
            ).createGroup(groupName)
        }

        composeRule.onNodeWithTag("nav_devices").performClick()
        composeRule.onNodeWithTag("add_device").performClick()
        composeRule.onNodeWithTag("add_manually").performClick()
        composeRule.onNodeWithTag("device_name").performTextInput(deviceName)
        composeRule.onNodeWithTag("registration_list").performScrollToIndex(2)
        composeRule.onNodeWithTag("group_picker").performClick()
        composeRule.onNodeWithText(groupName).performClick()
        composeRule.onNodeWithTag("registration_list").performScrollToIndex(5)
        composeRule.onNodeWithTag("bssid_0").performTextInput(firstBssid)
        composeRule.onNodeWithTag("add_bssid").performClick()
        composeRule.onNodeWithTag("registration_list").performScrollToIndex(6)
        composeRule.onNodeWithTag("bssid_1").performTextInput(firstBssid.lowercase().replace(':', '-'))
        composeRule.onNodeWithTag("registration_list").performScrollToIndex(7)
        composeRule.onNodeWithTag("save_device_bottom").performClick()
        composeRule.onNodeWithTag("registration_list").performScrollToIndex(1)
        composeRule.onNodeWithText(duplicateBssidMessage).assertIsDisplayed()

        composeRule.onNodeWithTag("registration_list").performScrollToIndex(7)
        composeRule.onNodeWithTag("bssid_1").performTextReplacement(secondBssid)
        composeRule.onNodeWithTag("registration_list").performScrollToIndex(8)
        composeRule.onNodeWithTag("save_device_bottom").performClick()
        composeRule.waitUntil(5_000) {
            runCatching { composeRule.onNodeWithTag("edit_device").fetchSemanticsNode() }.isSuccess
        }
        composeRule.onNodeWithText(deviceName).assertIsDisplayed()
        kotlinx.coroutines.runBlocking {
            val saved = database.registryDao().getAllDevices().single { it.displayName == deviceName }
            val savedGroup = database.registryDao().getAllGroups().single { it.id == saved.groupId }
            org.junit.Assert.assertEquals(groupName, savedGroup.name)
        }
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
        composeRule.onNodeWithText(deleteTitle).assertIsDisplayed()
        composeRule.onNodeWithText(deleteLabel).performClick()
        composeRule.waitUntil(5_000) {
            runCatching { composeRule.onNodeWithText(devicesTitle).fetchSemanticsNode() }.isSuccess
        }
        composeRule.onNodeWithText(editedName).assertDoesNotExist()
    }
}
