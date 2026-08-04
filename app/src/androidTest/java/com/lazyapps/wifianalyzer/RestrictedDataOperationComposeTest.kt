package com.lazyapps.wifianalyzer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lazyapps.wifianalyzer.billing.AccessRestriction
import com.lazyapps.wifianalyzer.ui.pro.ProRestrictionDialog
import com.lazyapps.wifianalyzer.ui.theme.WifiAnalyzerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RestrictedDataOperationComposeTest {
    @get:Rule val rule = createComposeRule()

    @Test fun commonDialogProvidesOneProActionAndOneDismissAction() {
        var opens = 0
        var dismisses = 0
        rule.setContent { WifiAnalyzerTheme { ProRestrictionDialog(AccessRestriction.CsvRequiresPro, { opens++ }, { dismisses++ }) } }
        rule.onNodeWithTag("pro_view").assertIsDisplayed().performClick()
        assertEquals(1, opens)
        rule.setContent { WifiAnalyzerTheme { ProRestrictionDialog(AccessRestriction.RestoreRequiresPro, { opens++ }, { dismisses++ }) } }
        rule.onNodeWithTag("pro_close").assertIsDisplayed().performClick()
        assertEquals(1, dismisses)
    }
}
