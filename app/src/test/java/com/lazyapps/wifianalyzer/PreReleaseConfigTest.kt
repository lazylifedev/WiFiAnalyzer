package com.lazyapps.wifianalyzer

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreReleaseConfigTest {
    @Test
    fun manifestDeclaresInternetWithoutUnusedNetworkStatePermission() {
        val manifest = source("src/main/AndroidManifest.xml")
        assertTrue(manifest.contains("android.permission.INTERNET"))
        assertFalse(manifest.contains("android.permission.ACCESS_NETWORK_STATE"))
    }

    @Test
    fun devicePhotosAreExcludedFromCloudBackupAndDeviceTransfer() {
        val legacy = source("src/main/res/xml/backup_rules.xml")
        val extraction = source("src/main/res/xml/data_extraction_rules.xml")
        val photoExclusion = """<exclude domain="file" path="devices/"/>"""

        assertTrue(legacy.contains(photoExclusion))
        assertTrue(
            extraction.substringAfter("<cloud-backup>").substringBefore("</cloud-backup>")
                .contains(photoExclusion),
        )
        assertTrue(
            extraction.substringAfter("<device-transfer>").substringBefore("</device-transfer>")
                .contains(photoExclusion),
        )
    }

    private fun source(relativePath: String): String {
        val fromModule = File(relativePath)
        val fromRoot = File("app", relativePath)
        return when {
            fromModule.isFile -> fromModule.readText()
            fromRoot.isFile -> fromRoot.readText()
            else -> error("Missing source file: $relativePath")
        }
    }
}
