package com.lazyapps.wifianalyzer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lazyapps.wifianalyzer.billing.AccessRestriction
import com.lazyapps.wifianalyzer.ui.pro.ProRestrictionDialog
import com.lazyapps.wifianalyzer.ui.theme.WifiAnalyzerTheme
import com.lazyapps.wifianalyzer.billing.FeatureAccessPolicy
import com.lazyapps.wifianalyzer.ui.operation.ExternalDataOperationCoordinator
import com.lazyapps.wifianalyzer.ui.operation.ExternalDataOperations
import android.net.Uri
import java.io.File
import java.io.InputStream
import java.io.OutputStream
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
        val reason = mutableStateOf(AccessRestriction.CsvRequiresPro)
        rule.setContent { WifiAnalyzerTheme { ProRestrictionDialog(reason.value, { opens++ }, { dismisses++ }) } }
        rule.onNodeWithTag("pro_view").assertIsDisplayed().performClick()
        assertEquals(1, opens)
        rule.runOnIdle { reason.value = AccessRestriction.RestoreRequiresPro }
        rule.onNodeWithTag("pro_close").assertIsDisplayed().performClick()
        assertEquals(2, dismisses)
    }
    @Test fun freeExternalOperationButtonDoesNotCallFakeBoundary() {
        var calls = 0
        val fake = object : ExternalDataOperations {
            override fun launchCreateDocument(name: String) { calls++ }; override fun launchOpenDocument() { calls++ }
            override fun launchShareIntent(file: File) { calls++ }; override fun openInputStream(uri: Uri): InputStream? { calls++; return null }
            override fun openOutputStream(uri: Uri): OutputStream? { calls++; return null }; override fun createTemporaryFile(name: String): File { calls++; return File(name) }
        }
        val coordinator = ExternalDataOperationCoordinator({ FeatureAccessPolicy(false) }, fake)
        rule.setContent { WifiAnalyzerTheme { Button(onClick = { coordinator.createCsv("x") }) { Text("CSV") } } }
        rule.onNodeWithText("CSV").performClick()
        assertEquals(0, calls)
    }
}
