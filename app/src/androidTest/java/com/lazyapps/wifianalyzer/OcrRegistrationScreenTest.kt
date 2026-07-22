package com.lazyapps.wifianalyzer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performScrollTo
import androidx.compose.runtime.mutableStateOf
import com.lazyapps.wifianalyzer.domain.ocr.CandidateKind
import com.lazyapps.wifianalyzer.domain.ocr.ConfidenceLevel
import com.lazyapps.wifianalyzer.domain.ocr.ParsedDeviceLabel
import com.lazyapps.wifianalyzer.domain.ocr.ParsedFieldCandidate
import com.lazyapps.wifianalyzer.ui.screens.devices.PermissionExplanation
import com.lazyapps.wifianalyzer.ui.screens.devices.PermanentPermissionDenial
import com.lazyapps.wifianalyzer.ui.screens.devices.ResultConfirmation
import com.lazyapps.wifianalyzer.ui.theme.ThemeMode
import com.lazyapps.wifianalyzer.ui.theme.WifiAnalyzerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OcrRegistrationScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun permissionRequestAndManualFallbackAreExplicit() {
        var requested = false
        var manual = false
        composeRule.setContent { WifiAnalyzerTheme { PermissionExplanation(false, { requested = true }, { manual = true }) } }
        composeRule.onNodeWithText("カメラ権限が必要です").assertIsDisplayed()
        composeRule.onNodeWithTag("request_camera_permission").performClick()
        assertTrue(requested)
        composeRule.onNodeWithTag("ocr_manual").performClick()
        assertTrue(manual)
    }

    @Test fun permanentDenialOffersSettingsInsteadOfAnotherRequest() {
        var settings = false
        composeRule.setContent { WifiAnalyzerTheme { PermanentPermissionDenial({ settings = true }, {}) } }
        composeRule.onNodeWithTag("open_camera_settings").performClick()
        assertTrue(settings)
    }

    @Test fun resultCandidateCanBeRejectedEditedRetakenAndContinued() {
        var candidate = ParsedFieldCandidate("Lab-5", CandidateKind.SSID, "SSID 5G", ConfidenceLevel.HIGH)
        var retake = false
        var continued = false
        composeRule.setContent {
            WifiAnalyzerTheme {
                ResultConfirmation(
                    result = ParsedDeviceLabel(ssidCandidates = listOf(candidate), rawText = "SSID 5G: Lab-5"),
                    onUpdate = { _, updated -> candidate = updated },
                    onRetake = { retake = true },
                    onContinue = { continued = true },
                    onManual = {},
                )
            }
        }
        composeRule.onNodeWithTag("candidate_SSID_0").performClick()
        assertEquals(false, candidate.selected)
        composeRule.onNodeWithText("Lab-5").performTextReplacement("Lab-6")
        assertEquals("Lab-6", candidate.value)
        composeRule.onNodeWithTag("retake").performScrollTo().performClick()
        composeRule.onNodeWithTag("use_ocr_result").performScrollTo().performClick()
        assertTrue(retake)
        assertTrue(continued)
    }

    @Test fun resultRendersInLightAndDarkThemes() {
        val result = ParsedDeviceLabel(modelCandidates = listOf(ParsedFieldCandidate("TEST-100", CandidateKind.MODEL, "MODEL", ConfidenceLevel.HIGH)), rawText = "MODEL: TEST-100")
        val mode = mutableStateOf(ThemeMode.LIGHT)
        composeRule.setContent { WifiAnalyzerTheme(mode = mode.value) { ResultConfirmation(result, { _, _ -> }, {}, {}, {}) } }
        composeRule.onNodeWithText("TEST-100").assertIsDisplayed()
        composeRule.runOnUiThread { mode.value = ThemeMode.DARK }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("TEST-100").assertIsDisplayed()
    }
}
