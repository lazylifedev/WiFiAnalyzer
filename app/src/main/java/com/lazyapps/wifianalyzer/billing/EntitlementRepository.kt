package com.lazyapps.wifianalyzer.billing

import kotlinx.coroutines.flow.StateFlow

interface BillingRepository {
    val snapshot: StateFlow<BillingSnapshot>
    suspend fun connectAndRefresh(force: Boolean = false)
    suspend fun restore()
    fun launchPurchase(activity: android.app.Activity): Boolean
    fun close()
}

interface EntitlementRepository {
    val snapshot: StateFlow<BillingSnapshot>
    suspend fun refresh(force: Boolean = false)
    suspend fun restore()
    fun purchase(activity: android.app.Activity): Boolean
}

class DefaultEntitlementRepository(private val billing: BillingRepository) : EntitlementRepository {
    override val snapshot = billing.snapshot
    override suspend fun refresh(force: Boolean) = billing.connectAndRefresh(force)
    override suspend fun restore() = billing.restore()
    override fun purchase(activity: android.app.Activity) = billing.launchPurchase(activity)
}
