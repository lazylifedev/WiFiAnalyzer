package com.lazyapps.wifianalyzer.billing

object BillingProducts {
    const val PRO = "wifi_analyzer_pro"
}

enum class BillingConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

data class ProProduct(
    val id: String = BillingProducts.PRO,
    val formattedPrice: String,
)

sealed interface ProEntitlementState {
    data object Unknown : ProEntitlementState
    data object Free : ProEntitlementState
    data object Pending : ProEntitlementState
    data object Pro : ProEntitlementState
    data class Unavailable(val retryable: Boolean = true) : ProEntitlementState
    data class Error(val retryable: Boolean = true, val retainedPro: Boolean = false) : ProEntitlementState
}

data class BillingSnapshot(
    val connection: BillingConnectionState = BillingConnectionState.DISCONNECTED,
    val product: ProProduct? = null,
    val entitlement: ProEntitlementState = ProEntitlementState.Unknown,
    val lastQueryAt: Long? = null,
    val restoring: Boolean = false,
    val purchasing: Boolean = false,
)

data class PurchaseRecord(
    val productIds: Set<String>,
    val state: PurchaseState,
    val acknowledged: Boolean,
    internal val token: String,
)

enum class PurchaseState { PURCHASED, PENDING }

object BillingEntitlementResolver {
    fun resolve(records: List<PurchaseRecord>, previouslyConfirmedPro: Boolean = false, querySucceeded: Boolean = true): ProEntitlementState {
        if (!querySucceeded) return ProEntitlementState.Error(retainedPro = previouslyConfirmedPro)
        val matching = records.filter { BillingProducts.PRO in it.productIds }
        return when {
            matching.any { it.state == PurchaseState.PURCHASED } -> ProEntitlementState.Pro
            matching.any { it.state == PurchaseState.PENDING } -> ProEntitlementState.Pending
            else -> ProEntitlementState.Free
        }
    }

    fun requiresAcknowledgement(record: PurchaseRecord): Boolean =
        BillingProducts.PRO in record.productIds && record.state == PurchaseState.PURCHASED && !record.acknowledged
}

sealed interface BillingQueryResult {
    data class Success(val purchases: List<PurchaseRecord>, val product: ProProduct?) : BillingQueryResult
    data object Unavailable : BillingQueryResult
    data object Failure : BillingQueryResult
}

sealed interface PurchaseAcknowledgementResult {
    data object Success : PurchaseAcknowledgementResult
    data object AlreadyAcknowledged : PurchaseAcknowledgementResult
    data class RetryableFailure(val responseCode: Int) : PurchaseAcknowledgementResult
    data class PermanentFailure(val responseCode: Int) : PurchaseAcknowledgementResult
}

object PurchaseAcknowledgementPolicy {
    fun classify(responseCode: Int): PurchaseAcknowledgementResult = when (responseCode) {
        com.android.billingclient.api.BillingClient.BillingResponseCode.OK ->
            PurchaseAcknowledgementResult.Success
        com.android.billingclient.api.BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
        com.android.billingclient.api.BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
        com.android.billingclient.api.BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
        com.android.billingclient.api.BillingClient.BillingResponseCode.NETWORK_ERROR,
        com.android.billingclient.api.BillingClient.BillingResponseCode.ERROR ->
            PurchaseAcknowledgementResult.RetryableFailure(responseCode)
        else -> PurchaseAcknowledgementResult.PermanentFailure(responseCode)
    }
}

data class BillingUiState(
    val connection: BillingConnectionState = BillingConnectionState.DISCONNECTED,
    val product: ProProduct? = null,
    val entitlement: ProEntitlementState = ProEntitlementState.Unknown,
    val lastQueryAt: Long? = null,
    val restoring: Boolean = false,
    val purchasing: Boolean = false,
) {
    val isPro: Boolean get() = entitlement == ProEntitlementState.Pro ||
        (entitlement is ProEntitlementState.Error && entitlement.retainedPro)
    val canPurchase: Boolean get() = product != null && !purchasing && !restoring && !isPro
}

fun BillingSnapshot.toUiState() = BillingUiState(connection, product, entitlement, lastQueryAt, restoring, purchasing)
