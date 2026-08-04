package com.lazyapps.wifianalyzer

import android.net.Uri
import com.lazyapps.wifianalyzer.ui.screens.settings.LegalLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LegalLinksTest {
    @Test fun privacyPolicyLaunchesExactViewUriOnce() {
        val launched = mutableListOf<Uri>()
        val result = LegalLinks.open(LegalLinks.PRIVACY_POLICY_URL, launched::add) { }
        assertTrue(result)
        assertEquals(listOf(Uri.parse(LegalLinks.PRIVACY_POLICY_URL)), launched)
        assertFalse(launched.single().toString() == LegalLinks.TERMS_OF_SERVICE_URL)
    }

    @Test fun termsLaunchesExactViewUriOnce() {
        val launched = mutableListOf<Uri>()
        val result = LegalLinks.open(LegalLinks.TERMS_OF_SERVICE_URL, launched::add) { }
        assertTrue(result)
        assertEquals(listOf(Uri.parse(LegalLinks.TERMS_OF_SERVICE_URL)), launched)
        assertFalse(launched.single().toString() == LegalLinks.PRIVACY_POLICY_URL)
        assertEquals("https", launched.single().scheme)
        assertEquals("lazylifedev.com", launched.single().host)
    }

    @Test fun repeatedLaunchesRemainSafeAndIndependent() {
        val launched = mutableListOf<Uri>()
        repeat(2) { LegalLinks.open(LegalLinks.TERMS_OF_SERVICE_URL, launched::add) { } }
        assertEquals(2, launched.size)
    }

    @Test fun activityNotFoundNotifiesOnceWithoutThrowing() {
        val notifications = mutableListOf<Int>()
        val result = LegalLinks.open(LegalLinks.TERMS_OF_SERVICE_URL, { throw android.content.ActivityNotFoundException() }, notifications::add)
        assertFalse(result)
        assertEquals(listOf(R.string.external_link_unavailable), notifications)
    }

    @Test fun securityFailureNotifiesOnceWithoutThrowing() {
        val notifications = mutableListOf<Int>()
        val result = LegalLinks.open(LegalLinks.PRIVACY_POLICY_URL, { throw SecurityException() }, notifications::add)
        assertFalse(result)
        assertEquals(listOf(R.string.external_link_unavailable), notifications)
    }

    @Test fun unapprovedUrlIsRejectedWithoutNotification() {
        val launched = mutableListOf<Uri>()
        val notifications = mutableListOf<Int>()
        assertFalse(LegalLinks.open("https://example.com/", launched::add, notifications::add))
        assertTrue(launched.isEmpty())
        assertTrue(notifications.isEmpty())
    }
}
