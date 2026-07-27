package com.lazyapps.wifianalyzer

import com.lazyapps.wifianalyzer.data.registry.DevicePhotoEntity
import com.lazyapps.wifianalyzer.kintone.KintonePhotoCandidate
import com.lazyapps.wifianalyzer.kintone.KintonePhotoFingerprint
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class KintonePhotoFingerprintTest {
    private fun candidate(id: Long, order: Int, hash: String = "hash", caption: String = "", primary: Boolean = false) = KintonePhotoCandidate(
        DevicePhotoEntity(id, 1, 2, "$id.jpg", "image/jpeg", 1, 1, 1, order, caption, primary, 1, 1),
        File("$id.jpg"), hash,
    )

    @Test fun sameCompositionIsStableAndCaptionIsIgnored() {
        assertEquals(KintonePhotoFingerprint.create(listOf(candidate(1, 0, caption = "a"))), KintonePhotoFingerprint.create(listOf(candidate(1, 0, caption = "b"))))
    }

    @Test fun addDeleteReorderContentAndPrimaryChangesAreDetected() {
        val base = KintonePhotoFingerprint.create(listOf(candidate(1, 0), candidate(2, 1)))
        assertNotEquals(base, KintonePhotoFingerprint.create(listOf(candidate(1, 0))))
        assertNotEquals(base, KintonePhotoFingerprint.create(listOf(candidate(1, 1), candidate(2, 0))))
        assertNotEquals(base, KintonePhotoFingerprint.create(listOf(candidate(1, 0, "changed"), candidate(2, 1))))
        assertNotEquals(base, KintonePhotoFingerprint.create(listOf(candidate(1, 0, primary = true), candidate(2, 1))))
    }

    @Test fun emptyCompositionIsStable() = assertEquals(KintonePhotoFingerprint.create(emptyList()), KintonePhotoFingerprint.create(emptyList()))
}
