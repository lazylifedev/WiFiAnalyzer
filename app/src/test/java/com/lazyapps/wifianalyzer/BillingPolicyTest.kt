package com.lazyapps.wifianalyzer

import com.lazyapps.wifianalyzer.billing.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class BillingPolicyTest {
    private fun record(state: PurchaseState, acknowledged: Boolean = true) = PurchaseRecord(
        setOf(BillingProducts.PRO), state, acknowledged, "redacted-test-token",
    )

    @Test fun noPurchaseResolvesUnknownToFree() = assertEquals(ProEntitlementState.Free, BillingEntitlementResolver.resolve(emptyList()))
    @Test fun purchasedResolvesUnknownToPro() = assertEquals(ProEntitlementState.Pro, BillingEntitlementResolver.resolve(listOf(record(PurchaseState.PURCHASED))))
    @Test fun pendingIsNeverPro() = assertEquals(ProEntitlementState.Pending, BillingEntitlementResolver.resolve(listOf(record(PurchaseState.PENDING))))
    @Test fun unacknowledgedPurchaseRequiresAcknowledgement() = assertTrue(BillingEntitlementResolver.requiresAcknowledgement(record(PurchaseState.PURCHASED, false)))
    @Test fun acknowledgedPurchaseDoesNotRequireAcknowledgement() = assertFalse(BillingEntitlementResolver.requiresAcknowledgement(record(PurchaseState.PURCHASED, true)))
    @Test fun temporaryFailureRetainsPreviouslyConfirmedPro() {
        assertEquals(ProEntitlementState.Error(retainedPro = true), BillingEntitlementResolver.resolve(emptyList(), true, false))
        assertFalse((BillingEntitlementResolver.resolve(emptyList(), false, false) as ProEntitlementState.Error).retainedPro)
    }
    @Test fun proEnablesKintoneAndRemovesAds() {
        val access = FeatureAccessPolicy.from(ProEntitlementState.Pro)
        assertTrue(access.canUseKintone)
        assertTrue(access.canRemoveAds)
        assertFalse(AdVisibilityPolicy(access).canShow(AdPlacement.HOME))
    }
    @Test fun freeKeepsCurrentFeaturesAndMayShowAds() {
        val access = FeatureAccessPolicy.from(ProEntitlementState.Free)
        assertFalse(access.canUseKintone)
        assertTrue(access.canUseCsvImport)
        assertEquals(1, access.maxWorkspaceCount)
        assertEquals(AccessRestriction.SavedDeviceLimitReached, access.restrictionForDeviceCount(5))
        assertNull(access.restrictionForDeviceCount(4))
        assertEquals(AccessRestriction.WorkspaceLimitReached, access.restrictionForWorkspaceCount(1))
        assertEquals(AccessRestriction.DevicePhotoLimitReached, access.restrictionForPhotoCount(1))
        assertFalse(access.canExportCsv)
        assertFalse(access.canExportPdf)
        assertFalse(access.canBackup)
        assertFalse(access.canRestore)
        assertTrue(AdVisibilityPolicy(access).canShow(AdPlacement.HOME))
    }
    @Test fun acknowledgementResponseSeparatesSuccessRetryableAndPermanentFailures() {
        assertEquals(
            PurchaseAcknowledgementResult.Success,
            PurchaseAcknowledgementPolicy.classify(com.android.billingclient.api.BillingClient.BillingResponseCode.OK),
        )
        assertTrue(
            PurchaseAcknowledgementPolicy.classify(
                com.android.billingclient.api.BillingClient.BillingResponseCode.NETWORK_ERROR,
            ) is PurchaseAcknowledgementResult.RetryableFailure,
        )
        assertTrue(
            PurchaseAcknowledgementPolicy.classify(
                com.android.billingclient.api.BillingClient.BillingResponseCode.DEVELOPER_ERROR,
            ) is PurchaseAcknowledgementResult.PermanentFailure,
        )
    }
    @Test fun billingCloseCancelsScopeAndEndsConnectionOnce() {
        var scopeCancelCount = 0
        var endConnectionCount = 0
        val guard = BillingCloseGuard(
            cancelScope = { scopeCancelCount++ },
            endConnection = { endConnectionCount++ },
        )

        guard.close()
        guard.close()

        assertTrue(guard.closed)
        assertEquals(1, scopeCancelCount)
        assertEquals(1, endConnectionCount)
    }
    @Test fun entitlementRepositoryForwardsCloseToItsOwnedBillingRepository() {
        var closeCount = 0
        val billing = object : BillingRepository {
            override val snapshot = MutableStateFlow(BillingSnapshot())
            override suspend fun connectAndRefresh(force: Boolean) = Unit
            override suspend fun restore() = Unit
            override fun launchPurchase(activity: android.app.Activity) = false
            override fun close() { closeCount++ }
        }

        DefaultEntitlementRepository(billing).close()

        assertEquals(1, closeCount)
    }
    @Test fun debugForceProOverridesFreeEntitlement() {
        val access = FeatureAccessPolicy.from(ProEntitlementState.Free, debugForcePro = true)
        assertTrue(access.canUseKintone)
        assertTrue(access.canRemoveAds)
    }
    @Test fun normalFreeEntitlementDoesNotBecomePro() {
        assertFalse(FeatureAccessPolicy.from(ProEntitlementState.Free, debugForcePro = false).isPro)
    }
}
