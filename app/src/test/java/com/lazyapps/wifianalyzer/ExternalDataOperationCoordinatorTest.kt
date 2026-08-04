package com.lazyapps.wifianalyzer

import android.net.Uri
import com.lazyapps.wifianalyzer.billing.FeatureAccessPolicy
import com.lazyapps.wifianalyzer.ui.operation.ExternalDataOperationCoordinator
import com.lazyapps.wifianalyzer.ui.operation.ExternalDataOperations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ExternalDataOperationCoordinatorTest {
    private class Fake : ExternalDataOperations {
        var create = 0; var open = 0; var share = 0; var inputs = 0; var outputs = 0; var temp = 0
        override fun launchCreateDocument(name: String) { create++ }
        override fun launchOpenDocument() { open++ }
        override fun launchShareIntent(file: File) { share++ }
        override fun openInputStream(uri: Uri) = ByteArrayInputStream(byteArrayOf(1)).also { inputs++ }
        override fun openOutputStream(uri: Uri) = ByteArrayOutputStream().also { outputs++ }
        override fun createTemporaryFile(name: String) = File(name).also { temp++ }
    }

    @Test fun freeCsvPdfBackupRestoreHaveNoExternalSideEffects() {
        val fake = Fake(); val coordinator = ExternalDataOperationCoordinator({ FeatureAccessPolicy(false) }, fake); val uri = Uri.parse("content://test")
        coordinator.createCsv("x", uri) { }; coordinator.shareCsv(File("x")); coordinator.createPdf(uri) { }; coordinator.sharePdf(File("x")); coordinator.backup("x", uri) { }; coordinator.restorePicker(); coordinator.restoreUri(uri) { }
        assertEquals(listOf(0, 0, 0, 0, 0, 0), listOf(fake.create, fake.open, fake.share, fake.inputs, fake.outputs, fake.temp))
    }

    @Test fun proUsesEachRequestedBoundaryOnce() {
        val fake = Fake(); val coordinator = ExternalDataOperationCoordinator({ FeatureAccessPolicy(true) }, fake); val uri = Uri.parse("content://test")
        coordinator.createCsv("x"); coordinator.createCsv("x", uri) { }; coordinator.shareCsv(File("x")); coordinator.createPdf(uri) { }; coordinator.sharePdf(File("x")); coordinator.backup("x"); coordinator.backup("x", uri) { }; coordinator.restorePicker(); coordinator.restoreUri(uri) { }
        assertEquals(2, fake.create); assertEquals(1, fake.open); assertEquals(2, fake.share); assertEquals(1, fake.inputs); assertEquals(3, fake.outputs); assertEquals(0, fake.temp)
    }

    @Test fun entitlementIsReadAgainBeforeRestoreInput() {
        var pro = true; val fake = Fake(); val coordinator = ExternalDataOperationCoordinator({ FeatureAccessPolicy(pro) }, fake); val uri = Uri.parse("content://test")
        pro = false; assertTrue(coordinator.restoreUri(uri) { }.isFailure); assertEquals(0, fake.inputs)
    }
}
