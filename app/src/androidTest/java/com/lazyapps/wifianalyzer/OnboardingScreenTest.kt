package com.lazyapps.wifianalyzer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.lazyapps.wifianalyzer.ui.onboarding.OnboardingScreen
import com.lazyapps.wifianalyzer.ui.theme.WifiAnalyzerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnboardingScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun nextBackSkipAndCompleteAreReachable() {
        var completed = false
        composeRule.setContent { WifiAnalyzerTheme { OnboardingScreen { completed = true } } }
        composeRule.onNodeWithText("Wi-Fiの状態を見える化").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_next").performClick()
        composeRule.onNodeWithText("機器を登録して管理").assertIsDisplayed()
        composeRule.onNodeWithText("戻る").performClick()
        composeRule.onNodeWithText("Wi-Fiの状態を見える化").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_skip").performClick()
        assertTrue(completed)
    }

    @Test fun finalPageStartsUse() {
        var completed = false
        composeRule.setContent { WifiAnalyzerTheme { OnboardingScreen { completed = true } } }
        repeat(3) { composeRule.onNodeWithTag("onboarding_next").performClick() }
        composeRule.onNodeWithTag("onboarding_complete").assertIsDisplayed().performClick()
        assertTrue(completed)
    }
}
