package com.lazyapps.wifianalyzer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.runtime.mutableStateOf
import com.lazyapps.wifianalyzer.billing.*
import com.lazyapps.wifianalyzer.ui.pro.KintoneScreen
import com.lazyapps.wifianalyzer.ui.pro.ProScreen
import com.lazyapps.wifianalyzer.ui.theme.WifiAnalyzerTheme
import com.lazyapps.wifianalyzer.kintone.KintoneConnectionSummary
import com.lazyapps.wifianalyzer.kintone.KintoneAutoSyncState
import com.lazyapps.wifianalyzer.kintone.KintoneSyncStatus
import com.lazyapps.wifianalyzer.ui.kintone.KintoneUiState
import com.lazyapps.wifianalyzer.ui.kintone.KintoneFailureContext
import com.lazyapps.wifianalyzer.kintone.KintoneErrorCode
import com.lazyapps.wifianalyzer.kintone.KintoneWorkspaceOption
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ProScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun freeScreenShowsPlayPriceAndPreventsDoublePurchase() {
        var purchases = 0
        val state = mutableStateOf(BillingUiState(product = ProProduct(formattedPrice = "¥1,000"), entitlement = ProEntitlementState.Free))
        rule.setContent { WifiAnalyzerTheme { ProScreen(state.value, {}, { purchases++ }, {}) } }
        rule.onNodeWithText("価格: ¥1,000").assertIsDisplayed()
        rule.onNodeWithTag("purchase_pro").performClick()
        assertEquals(1, purchases)
        rule.runOnIdle { state.value = state.value.copy(purchasing = true) }
        rule.onNodeWithTag("purchase_pro").performClick()
        assertEquals(1, purchases)
    }

    @Test fun proAndPendingStatesAreExplicit() {
        val state = mutableStateOf(BillingUiState(entitlement = ProEntitlementState.Pro))
        rule.setContent { WifiAnalyzerTheme { ProScreen(state.value, {}, {}, {}) } }
        rule.onNodeWithText("Pro版をご利用中です").assertIsDisplayed()
        rule.runOnIdle { state.value = BillingUiState(entitlement = ProEntitlementState.Pending) }
        rule.onNodeWithText("購入手続きが保留されています。").assertIsDisplayed()
    }

    @Test fun kintoneIsLockedForFreeAndHasNoManualCredentialFields() {
        rule.setContent { WifiAnalyzerTheme { KintoneScreen(FeatureAccessPolicy(false), {}, {}, {}) } }
        rule.onNodeWithText("Pro版で利用できます。").assertIsDisplayed()
        rule.onNodeWithText("ドメイン").assertDoesNotExist()
        rule.onNodeWithText("APIトークン").assertDoesNotExist()
        rule.onNodeWithText("アプリID").assertDoesNotExist()
    }

    @Test fun kintoneProShowsQrEntry() {
        rule.setContent { WifiAnalyzerTheme { KintoneScreen(FeatureAccessPolicy(true), {}, {}, {}, state = disconnectedState()) } }
        rule.onNodeWithTag("kintone_connection_summary").performClick()
        rule.onNodeWithText("接続").assertIsDisplayed()
    }

    private fun connectedState() = KintoneUiState(
        workspaceId = 1, workspaceName = "Main",
        workspaces = listOf(KintoneWorkspaceOption(1, "Main", 1, true, false)),
        selectedWorkspaceIds = setOf(1),
        connection = KintoneConnectionSummary(1, "example.cybozu.com", 10, "1", 1, 1, 1, 1, "CONNECTED"),
        canUseKintone = true,
    )

    private fun disconnectedState() = KintoneUiState(workspaceId = 1, workspaceName = "Main", workspaces = listOf(KintoneWorkspaceOption(1, "Main", 0, false, false)), selectedWorkspaceIds = setOf(1), canUseKintone = true)

    @Test fun connectedProShowsManualSyncEvenWithoutDevices() {
        rule.setContent { WifiAnalyzerTheme { KintoneScreen(FeatureAccessPolicy(true), {}, {}, {}, state = connectedState()) } }
        rule.onNodeWithText("今すぐ同期").assertIsDisplayed()
    }

    @Test fun noTargetsUsesJapaneseNonErrorMessageAndStatus() {
        val state = connectedState().copy(message = "同期する登録機器がありません", autoSync = KintoneAutoSyncState(status = KintoneSyncStatus.NO_TARGETS, lastFinishedAt = 1))
        rule.setContent { WifiAnalyzerTheme { KintoneScreen(FeatureAccessPolicy(true), {}, {}, {}, state = state) } }
        rule.onNodeWithText("前回の同期：対象なし").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("接続できませんでした", substring = true).assertDoesNotExist()
    }

    @Test fun connectionQrAndSyncFailuresUseDifferentMessagesWithoutCodes() {
        val state = mutableStateOf(connectedState().copy(errorCode = KintoneErrorCode.KINTONE_TIMEOUT, failureContext = KintoneFailureContext.CONNECTION))
        rule.setContent { WifiAnalyzerTheme { KintoneScreen(FeatureAccessPolicy(true), {}, {}, {}, state = state.value) } }
        rule.onNodeWithText("状態").assertIsDisplayed()
        rule.onNodeWithText("KINTONE_TIMEOUT", substring = true).assertDoesNotExist()
        rule.runOnIdle { state.value = state.value.copy(failureContext = KintoneFailureContext.SYNC) }
        rule.onNodeWithText("状態").assertIsDisplayed()
        rule.runOnIdle { state.value = state.value.copy(failureContext = KintoneFailureContext.QR) }
        rule.onNodeWithText("状態").assertIsDisplayed()
    }

    @Test fun connectedProShowsAutoSyncSwitch() {
        rule.setContent { WifiAnalyzerTheme { KintoneScreen(FeatureAccessPolicy(true), {}, {}, {}, state = connectedState()) } }
        rule.onNodeWithTag("kintone_auto_sync_summary").assertIsDisplayed().performClick()
        rule.onNodeWithText("機器の自動同期").assertIsDisplayed()
    }

    @Test fun workspaceSelectorShowsCountsConnectionAutoSyncAndChangesOnlySyncTarget() {
        var selected: Set<Long> = emptySet()
        val state = connectedState().copy(
            appWorkspaceId = 1,
            workspaces = listOf(
                KintoneWorkspaceOption(1, "本社", 2, true, true),
                KintoneWorkspaceOption(2, "支社", 4, false, false),
            ),
        )
        rule.setContent { WifiAnalyzerTheme { KintoneScreen(FeatureAccessPolicy(true), {}, {}, {}, state = state, onManualWorkspacesSelected = { selected = it }) } }
        rule.onNodeWithTag("kintone_workspace_selector").performClick()
        rule.onNodeWithText("登録機器 2台・接続済み・自動同期ON・写真OFF").assertIsDisplayed()
        rule.onNodeWithText("登録機器 4台・未接続・自動同期OFF・写真OFF").assertIsDisplayed()
        rule.onNodeWithTag("kintone_workspace_2").performClick()
        rule.onNodeWithTag("kintone_workspace_confirm").performClick()
        assertEquals(setOf(1L, 2L), selected)
    }

    @Test fun photoAutoSyncDefaultsOffAndRequiresDeviceAutoSync() {
        rule.setContent { WifiAnalyzerTheme { KintoneScreen(FeatureAccessPolicy(true), {}, {}, {}, state = connectedState()) } }
        rule.onNodeWithTag("kintone_auto_sync_summary").performClick()
        rule.onNodeWithTag("kintone_photo_auto_sync_switch").assertIsNotEnabled()
    }

    @Test fun enablingAutoSyncRequiresConfirmation() {
        rule.setContent { WifiAnalyzerTheme { KintoneScreen(FeatureAccessPolicy(true), {}, {}, {}, state = connectedState()) } }
        rule.onNodeWithTag("kintone_auto_sync_summary").performClick()
        rule.onNodeWithTag("kintone_auto_sync_switch").performClick()
        rule.onNodeWithText("自動同期を有効にしますか").assertIsDisplayed()
    }

    @Test fun failedSyncShowsOnlySafeJapaneseDetailsAndRecoveryActions() {
        val state = connectedState().copy(autoSync = KintoneAutoSyncState(
            status = KintoneSyncStatus.FAILED, targetCount = 11, failureCount = 11, lastFinishedAt = 1,
            lastErrorCategory = "VALIDATION", lastHttpStatus = 400, lastKintoneErrorCode = "CB_VA01",
            lastUserMessage = "送信内容がkintoneのフィールド仕様と一致しません。", requiresAttention = true,
        ))
        rule.setContent { WifiAnalyzerTheme { KintoneScreen(FeatureAccessPolicy(true), {}, {}, {}, state = state) } }
        rule.onNodeWithText("前回の同期：同期失敗").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("APIトークン", substring = true).assertDoesNotExist()
        rule.onNodeWithTag("kintone_sync").assertIsEnabled()
    }

    @Test fun manualSyncIsDisabledOnlyForNoTargetsOrCurrentOperation() {
        val state = mutableStateOf(connectedState())
        rule.setContent { WifiAnalyzerTheme { KintoneScreen(FeatureAccessPolicy(true), {}, {}, {}, state = state.value) } }
        rule.onNodeWithTag("kintone_sync").assertIsEnabled()
        rule.runOnIdle {
            state.value = state.value.copy(workspaces = listOf(KintoneWorkspaceOption(1, "Main", 0, false, false)))
        }
        rule.onNodeWithTag("kintone_sync").assertIsNotEnabled()
        rule.runOnIdle {
            state.value = state.value.copy(
                workspaces = listOf(KintoneWorkspaceOption(1, "Main", 1, true, false)),
                operation = com.lazyapps.wifianalyzer.ui.operation.OperationState.Running(com.lazyapps.wifianalyzer.R.string.kintone_verifying),
            )
        }
        rule.onNodeWithTag("kintone_sync").assertDoesNotExist()
    }
}
