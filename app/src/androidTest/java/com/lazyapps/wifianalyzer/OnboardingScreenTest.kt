package com.lazyapps.wifianalyzer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.lazyapps.wifianalyzer.ui.onboarding.OnboardingScreen
import com.lazyapps.wifianalyzer.ui.theme.WifiAnalyzerTheme
import com.lazyapps.wifianalyzer.ui.theme.AccentColor
import com.lazyapps.wifianalyzer.ui.theme.ThemeMode
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import androidx.test.platform.app.InstrumentationRegistry

class OnboardingScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun nextBackSkipAndCompleteAreReachable() {
        var completed = false
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent { WifiAnalyzerTheme { OnboardingScreen { completed = true } } }
        composeRule.onNodeWithText(context.getString(R.string.onboarding_wifi_title)).assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_next").performClick()
        composeRule.onNodeWithText(context.getString(R.string.onboarding_devices_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.back)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.onboarding_wifi_title)).assertIsDisplayed()
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

    @Test fun allPagesAreVisibleInLightDarkSystemAndEveryAccent() {
        var selectedMode by mutableStateOf(ThemeMode.SYSTEM)
        var selectedAccent by mutableStateOf(AccentColor.BLUE)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent { key(selectedMode, selectedAccent) { WifiAnalyzerTheme(selectedMode, selectedAccent) { OnboardingScreen {} } } }
        ThemeMode.entries.forEach { mode -> AccentColor.entries.forEach { accent ->
            composeRule.runOnIdle { selectedMode = mode; selectedAccent = accent }
            listOf(R.string.onboarding_wifi_title, R.string.onboarding_devices_title, R.string.onboarding_data_title, R.string.onboarding_privacy_title).map(context::getString).forEachIndexed { index, title ->
                composeRule.onNodeWithText(title).assertIsDisplayed()
                if (index < 3) composeRule.onNodeWithTag("onboarding_next").performClick()
            }
        } }
    }

    @Test fun themeTokensKeepTextAndButtonContrastForEveryAccent() {
        var selectedMode by mutableStateOf(ThemeMode.LIGHT)
        var selectedAccent by mutableStateOf(AccentColor.BLUE)
        var background = Color.Unspecified; var title = Color.Unspecified; var body = Color.Unspecified
        var button = Color.Unspecified; var buttonText = Color.Unspecified
        composeRule.setContent { WifiAnalyzerTheme(selectedMode, selectedAccent) {
            background = MaterialTheme.colorScheme.background; title = MaterialTheme.colorScheme.onBackground
            body = MaterialTheme.colorScheme.onSurfaceVariant; button = MaterialTheme.colorScheme.primary
            buttonText = MaterialTheme.colorScheme.onPrimary; OnboardingScreen {}
        } }
        listOf(ThemeMode.LIGHT, ThemeMode.DARK).forEach { mode -> AccentColor.entries.forEach { accent ->
            composeRule.runOnIdle {
                selectedMode = mode; selectedAccent = accent
            }
            composeRule.runOnIdle {
                assertTrue(contrast(title, background) >= 4.5f)
                assertTrue(contrast(body, background) >= 4.5f)
                assertTrue(contrast(buttonText, button) >= 4.5f)
            }
        } }
    }

    private fun contrast(a: Color, b: Color): Float {
        fun luminance(c: Color): Float {
            fun channel(v: Float) = if (v <= .03928f) v / 12.92f else Math.pow(((v + .055f) / 1.055f).toDouble(), 2.4).toFloat()
            return .2126f * channel(c.red) + .7152f * channel(c.green) + .0722f * channel(c.blue)
        }
        val first = luminance(a); val second = luminance(b)
        return (maxOf(first, second) + .05f) / (minOf(first, second) + .05f)
    }
}
