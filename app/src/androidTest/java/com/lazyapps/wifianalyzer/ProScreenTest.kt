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
import androidx.test.platform.app.InstrumentationRegistry

class ProScreenTest {
    @get:Rule val rule = createComposeRule()
    private fun s(id: Int, vararg args: Any) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    @Test fun freeScreenShowsPlayPriceAndPreventsDoublePurchase() {
        var purchases = 0
        val state = mutableStateOf(BillingUiState(product = ProProduct(formattedPrice = "¥1,000"), entitlement = ProEntitlementState.Free))
        rule.setContent { WifiAnalyzerTheme { ProScreen(state.value, {}, { purchases++ }, {}) } }
        rule.onNodeWithText(s(R.string.pro_price, "¥1,000")).assertIsDisplayed()
        rule.onNodeWithTag("purchase_pro").performClick()
        assertEquals(1, purchases)
        rule.runOnIdle { state.value = state.value.copy(purchasing = true) }
        rule.onNodeWithTag("purchase_pro").performClick()
        assertEquals(1, purchases)
    }

    @Test fun proAndPendingStatesAreExplicit() {
        val state = mutableStateOf(BillingUiState(entitlement = ProEntitlementState.Pro))
        rule.setContent { WifiAnalyzerTheme { ProScreen(state.value, {}, {}, {}) } }
        rule.onNodeWithText(s(R.string.pro_active)).assertIsDisplayed()
        rule.runOnIdle { state.value = BillingUiState(entitlement = ProEntitlementState.Pending) }
        rule.onNodeWithText(s(R.string.pro_purchase_pending)).assertIsDisplayed()
    }

    @Test fun kintoneIsLockedForFreeAndHasNoManualCredentialFields() {
        rule.setContent { WifiAnalyzerTheme { KintoneScreen(FeatureAccessPolicy(false), {}, {}, {}) } }
        rule.onNodeWithText(s(R.string.kintone_pro_required)).assertIsDisplayed()
        rule.onNodeWithText("ドメイン").assertDoesNotExist()
        rule.onNodeWithText("APIトークン").assertDoesNotExist()
        rule.onNodeWithText("アプリID").assertDoesNotExist()
    }

    @Test fun kintoneProShowsQrEntry() {
        rule.setContent { WifiAnalyzerTheme { KintoneScreen(FeatureAccessPolicy(true), {}, {}, {}) } }
        rule.onNodeWithTag("kintone_scan_qr").assertIsDisplayed()
        rule.onNodeWithText(s(R.string.kintone_not_connected)).assertIsDisplayed()
    }

    @Test fun disconnectedScreenOpensBoothAndKeepsSpecificationsCollapsed() {
        var boothClicks = 0
        rule.setContent { WifiAnalyzerTheme { KintoneScreen(FeatureAccessPolicy(true), {}, {}, {}, onOpenBooth = { boothClicks++ }) } }
        rule.onNodeWithTag("kintone_open_booth").assertIsDisplayed().performClick()
        assertEquals(1, boothClicks)
        rule.onNodeWithText(s(R.string.kintone_sync_note_deletion)).assertDoesNotExist()
    }

    private fun connectedState() = KintoneUiState(
        workspaceId = 1, workspaceName = "Main",
        connection = KintoneConnectionSummary(1, "example.cybozu.com", 10, "1", 1, 1, 1, 1, "CONNECTED"),
        canUseKintone = true,
    )

    @Test fun connectedProShowsManualSyncEvenWithoutDevices() {
        rule.setContent { WifiAnalyzerTheme { KintoneScreen(FeatureAccessPolicy(true), {}, {}, {}, state = connectedState()) } }
        rule.onNodeWithTag("kintone_sync").performScrollTo().assertIsDisplayed()
    }

    @Test fun noTargetsUsesJapaneseNonErrorMessageAndStatus() {
        val state = connectedState().copy(message = s(R.string.kintone_no_registered_devices), autoSync = KintoneAutoSyncState(status = KintoneSyncStatus.NO_TARGETS))
        rule.setContent { WifiAnalyzerTheme { KintoneScreen(FeatureAccessPolicy(true), {}, {}, {}, state = state) } }
        rule.onNodeWithText(s(R.string.kintone_no_registered_devices)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.kintone_no_targets)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.kintone_connection_failed), substring = true).assertDoesNotExist()
    }

    @Test fun connectionQrAndSyncFailuresUseDifferentMessagesWithoutCodes() {
        val state = mutableStateOf(connectedState().copy(errorCode = KintoneErrorCode.KINTONE_TIMEOUT, failureContext = KintoneFailureContext.CONNECTION))
        rule.setContent { WifiAnalyzerTheme { KintoneScreen(FeatureAccessPolicy(true), {}, {}, {}, state = state.value) } }
        rule.onNodeWithText(s(R.string.kintone_connection_failed)).assertIsDisplayed()
        rule.onNodeWithText("KINTONE_TIMEOUT", substring = true).assertDoesNotExist()
        rule.runOnIdle { state.value = state.value.copy(failureContext = KintoneFailureContext.SYNC) }
        rule.onNodeWithText(s(R.string.kintone_sync_error)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.kintone_connection_failed)).assertDoesNotExist()
        rule.runOnIdle { state.value = state.value.copy(failureContext = KintoneFailureContext.QR) }
        rule.onNodeWithText(s(R.string.kintone_qr_error)).assertIsDisplayed()
    }

    @Test fun connectedProShowsAutoSyncSwitch() {
        rule.setContent { WifiAnalyzerTheme { KintoneScreen(FeatureAccessPolicy(true), {}, {}, {}, state = connectedState()) } }
        rule.onNodeWithText(s(R.string.kintone_automatic_sync)).assertIsDisplayed()
        rule.onNodeWithTag("kintone_auto_sync").assertIsDisplayed()
    }

    @Test fun workspaceSelectorShowsCountsConnectionAutoSyncAndChangesOnlySyncTarget() {
        var selected = 0L
        val state = connectedState().copy(
            appWorkspaceId = 1,
            workspaces = listOf(
                KintoneWorkspaceOption(1, "本社", 2, true, true),
                KintoneWorkspaceOption(2, "支社", 4, false, false),
            ),
        )
        rule.setContent { WifiAnalyzerTheme { KintoneScreen(FeatureAccessPolicy(true), {}, {}, {}, state = state, onWorkspaceSelected = { selected = it.id }) } }
        rule.onNodeWithTag("kintone_workspace_selector").performClick()
        rule.onNodeWithText(s(R.string.kintone_workspace_summary_auto, 2, s(R.string.kintone_status_connected))).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.kintone_workspace_summary, 4, s(R.string.kintone_not_connected))).assertIsDisplayed()
        rule.onNodeWithTag("kintone_workspace_2").performClick()
        assertEquals(2L, selected)
    }

    @Test fun photoAutoSyncDefaultsOffAndRequiresDeviceAutoSync() {
        rule.setContent { WifiAnalyzerTheme { KintoneScreen(FeatureAccessPolicy(true), {}, {}, {}, state = connectedState()) } }
        rule.onNodeWithTag("kintone_photo_auto_sync_switch").assertIsNotEnabled()
    }

    @Test fun enablingAutoSyncRequiresConfirmation() {
        rule.setContent { WifiAnalyzerTheme { KintoneScreen(FeatureAccessPolicy(true), {}, {}, {}, state = connectedState()) } }
        rule.onNodeWithTag("kintone_auto_sync_switch").performClick()
        rule.onNodeWithText(s(R.string.kintone_enable_auto_sync_title)).assertIsDisplayed()
    }

    @Test fun failedSyncShowsOnlySafeJapaneseDetailsAndRecoveryActions() {
        val state = connectedState().copy(autoSync = KintoneAutoSyncState(
            status = KintoneSyncStatus.FAILED, targetCount = 11, failureCount = 11,
            lastErrorCategory = "VALIDATION", lastHttpStatus = 400, lastKintoneErrorCode = "CB_VA01",
            lastUserMessage = com.lazyapps.wifianalyzer.kintone.KintoneErrorMessages.FIELD_MISMATCH, requiresAttention = true,
        ))
        rule.setContent { WifiAnalyzerTheme { KintoneScreen(FeatureAccessPolicy(true), {}, {}, {}, state = state) } }
        rule.onNodeWithText(s(R.string.kintone_sync_failed)).assertIsDisplayed()
        rule.onNodeWithTag("kintone_error_details").performScrollTo().assertIsDisplayed().performClick()
        rule.onNodeWithTag("kintone_safe_error_message").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("CB_VA01", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("kintone_retry_failed").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("kintone_check_connection").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("APIトークン", substring = true).assertDoesNotExist()
        rule.onNodeWithTag("kintone_sync").performScrollTo().assertIsEnabled()
    }

    @Test fun manualSyncIsDisabledOnlyForNoTargetsOrCurrentOperation() {
        val state = mutableStateOf(connectedState().copy(
            syncPreview = com.lazyapps.wifianalyzer.kintone.KintoneSyncPreview(11, 11, emptyList(), emptyList()),
        ))
        rule.setContent { WifiAnalyzerTheme { KintoneScreen(FeatureAccessPolicy(true), {}, {}, {}, state = state.value) } }
        rule.onNodeWithTag("kintone_sync").assertIsEnabled()
        rule.runOnIdle {
            state.value = state.value.copy(syncPreview = com.lazyapps.wifianalyzer.kintone.KintoneSyncPreview(0, 0, emptyList(), emptyList()))
        }
        rule.onNodeWithTag("kintone_sync").assertIsNotEnabled()
        rule.runOnIdle {
            state.value = state.value.copy(
                syncPreview = com.lazyapps.wifianalyzer.kintone.KintoneSyncPreview(11, 11, emptyList(), emptyList()),
                operation = com.lazyapps.wifianalyzer.ui.operation.OperationState.Running(com.lazyapps.wifianalyzer.R.string.kintone_verifying),
            )
        }
        rule.onNodeWithTag("kintone_sync").assertIsNotEnabled()
    }

    @Test fun syncSpecificationsAreHiddenUntilRequested() {
        rule.setContent { WifiAnalyzerTheme { KintoneScreen(FeatureAccessPolicy(true), {}, {}, {}, state = connectedState()) } }
        rule.onNodeWithText(s(R.string.kintone_sync_note_deletion)).assertDoesNotExist()
        rule.onNodeWithTag("kintone_sync_notes").performScrollTo().performClick()
        rule.onNodeWithText(s(R.string.kintone_sync_note_deletion)).performScrollTo().assertIsDisplayed()
    }
}
