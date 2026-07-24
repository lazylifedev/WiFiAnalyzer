package com.lazyapps.wifianalyzer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.mutableStateOf
import com.lazyapps.wifianalyzer.billing.*
import com.lazyapps.wifianalyzer.ui.pro.KintoneScreen
import com.lazyapps.wifianalyzer.ui.pro.ProScreen
import com.lazyapps.wifianalyzer.ui.theme.WifiAnalyzerTheme
import com.lazyapps.wifianalyzer.kintone.KintoneConnectionSummary
import com.lazyapps.wifianalyzer.ui.kintone.KintoneUiState
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
        rule.setContent { WifiAnalyzerTheme { KintoneScreen(FeatureAccessPolicy(true), {}, {}, {}) } }
        rule.onNodeWithTag("kintone_scan_qr").assertIsDisplayed()
    }

    private fun connectedState() = KintoneUiState(
        workspaceId = 1, workspaceName = "Main",
        connection = KintoneConnectionSummary(1, "example.cybozu.com", 10, "1", 1, 1, 1, 1, "CONNECTED"),
        canUseKintone = true,
    )

    @Test fun connectedProShowsManualSyncEvenWithoutDevices() {
        rule.setContent { WifiAnalyzerTheme { KintoneScreen(FeatureAccessPolicy(true), {}, {}, {}, state = connectedState()) } }
        rule.onNodeWithText("今すぐ同期").assertIsDisplayed()
    }

    @Test fun connectedProShowsAutoSyncSwitch() {
        rule.setContent { WifiAnalyzerTheme { KintoneScreen(FeatureAccessPolicy(true), {}, {}, {}, state = connectedState()) } }
        rule.onNodeWithText("登録機器を自動同期").assertIsDisplayed()
        rule.onNodeWithTag("kintone_auto_sync").assertIsDisplayed()
    }

    @Test fun enablingAutoSyncRequiresConfirmation() {
        rule.setContent { WifiAnalyzerTheme { KintoneScreen(FeatureAccessPolicy(true), {}, {}, {}, state = connectedState()) } }
        rule.onNodeWithTag("kintone_auto_sync_switch").performClick()
        rule.onNodeWithText("自動同期を有効にしますか").assertIsDisplayed()
    }
}
