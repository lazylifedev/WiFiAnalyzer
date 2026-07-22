package com.lazyapps.wifianalyzer

import com.lazyapps.wifianalyzer.ui.photos.isPhotoZoomed
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoGesturePolicyTest {
    @Test fun pagerIsEnabledAtOneAndWithinTolerance() {
        assertFalse(isPhotoZoomed(1f))
        assertFalse(isPhotoZoomed(1.01f))
    }

    @Test fun pagerIsDisabledAboveTolerance() {
        assertTrue(isPhotoZoomed(1.0101f))
    }
}
