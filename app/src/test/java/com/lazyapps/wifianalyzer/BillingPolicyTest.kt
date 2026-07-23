package com.lazyapps.wifianalyzer

import com.lazyapps.wifianalyzer.billing.*
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
        assertNull(access.maxWorkspaceCount)
        assertTrue(AdVisibilityPolicy(access).canShow(AdPlacement.HOME))
    }
}
