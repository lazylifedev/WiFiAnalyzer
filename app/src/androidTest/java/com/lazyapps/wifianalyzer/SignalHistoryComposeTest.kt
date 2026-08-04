package com.lazyapps.wifianalyzer

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lazyapps.wifianalyzer.model.SignalSample
import com.lazyapps.wifianalyzer.ui.theme.WifiAnalyzerTheme
import com.lazyapps.wifianalyzer.ui.screens.monitor.SignalChart
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SignalHistoryComposeTest {
    @get:Rule val rule = createComposeRule()

    @Test fun chartShowsHistoryRangeChoicesForPersistedSamples() {
        rule.setContent { WifiAnalyzerTheme { SignalChart((0..3).map { SignalSample(it * 10_000L, -55 - it) }) } }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        rule.onNodeWithText(context.getString(R.string.signal_history)).fetchSemanticsNode()
        rule.onNodeWithText(context.getString(R.string.duration_30_seconds)).fetchSemanticsNode()
        rule.onNodeWithText(context.getString(R.string.duration_5_minutes)).fetchSemanticsNode()
    }
}
