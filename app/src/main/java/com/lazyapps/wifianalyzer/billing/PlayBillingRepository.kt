package com.lazyapps.wifianalyzer.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class PlayBillingRepository(context: Context) : BillingRepository, PurchasesUpdatedListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableSnapshot = MutableStateFlow(BillingSnapshot())
    override val snapshot = mutableSnapshot.asStateFlow()
    private var productDetails: ProductDetails? = null
    private var lastConfirmedPro = false
    private var connecting: Boolean = false

    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    override suspend fun connectAndRefresh(force: Boolean) {
        if (!client.isReady) {
            if (connecting) return
            connecting = true
            mutableSnapshot.value = mutableSnapshot.value.copy(connection = BillingConnectionState.CONNECTING)
            val connected = connect()
            connecting = false
            if (!connected) {
                mutableSnapshot.value = mutableSnapshot.value.copy(
                    connection = BillingConnectionState.DISCONNECTED,
                    entitlement = if (lastConfirmedPro) ProEntitlementState.Error(retainedPro = true) else ProEntitlementState.Unavailable(),
                )
                return
            }
        }
        refreshInternal()
    }

    override suspend fun restore() {
        mutableSnapshot.value = mutableSnapshot.value.copy(restoring = true)
        connectAndRefresh(force = true)
        mutableSnapshot.value = mutableSnapshot.value.copy(restoring = false)
    }

    override fun launchPurchase(activity: Activity): Boolean {
        val details = productDetails ?: return false
        if (!client.isReady || mutableSnapshot.value.purchasing) return false
        mutableSnapshot.value = mutableSnapshot.value.copy(purchasing = true)
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .build()
        val result = client.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(productParams)).build(),
        )
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            mutableSnapshot.value = mutableSnapshot.value.copy(purchasing = false, entitlement = safeFailure())
            return false
        }
        return true
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        mutableSnapshot.value = mutableSnapshot.value.copy(purchasing = false)
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> scope.launch { refreshInternal() }
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> mutableSnapshot.value = mutableSnapshot.value.copy(entitlement = safeFailure())
        }
    }

    private suspend fun connect(): Boolean = suspendCancellableCoroutine { continuation ->
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (continuation.isActive) continuation.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
            }
            override fun onBillingServiceDisconnected() {
                mutableSnapshot.value = mutableSnapshot.value.copy(connection = BillingConnectionState.DISCONNECTED)
            }
        })
    }

    private suspend fun refreshInternal() {
        mutableSnapshot.value = mutableSnapshot.value.copy(connection = BillingConnectionState.CONNECTED)
        val product = queryProduct()
        val purchases = queryPurchases()
        if (purchases == null) {
            mutableSnapshot.value = mutableSnapshot.value.copy(product = product, entitlement = safeFailure())
            return
        }
        val matching = purchases.filter { BillingProducts.PRO in it.products }
        val purchased = matching.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        purchased.filterNot { it.isAcknowledged }.forEach { acknowledge(it) }
        val entitlement = when {
            purchased.isNotEmpty() -> ProEntitlementState.Pro
            matching.any { it.purchaseState == Purchase.PurchaseState.PENDING } -> ProEntitlementState.Pending
            else -> ProEntitlementState.Free
        }
        lastConfirmedPro = entitlement == ProEntitlementState.Pro
        mutableSnapshot.value = mutableSnapshot.value.copy(
            product = product,
            entitlement = entitlement,
            lastQueryAt = System.currentTimeMillis(),
        )
    }

    private suspend fun queryProduct(): ProProduct? = suspendCancellableCoroutine { continuation ->
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(QueryProductDetailsParams.Product.newBuilder()
                .setProductId(BillingProducts.PRO)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()),
        ).build()
        client.queryProductDetailsAsync(params) { result, queryResult ->
            val details = if (result.responseCode == BillingClient.BillingResponseCode.OK) queryResult.productDetailsList.firstOrNull() else null
            productDetails = details
            continuation.resume(details?.oneTimePurchaseOfferDetails?.formattedPrice?.let { ProProduct(formattedPrice = it) })
        }
    }

    private suspend fun queryPurchases(): List<Purchase>? = suspendCancellableCoroutine { continuation ->
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
        ) { result, purchases ->
            continuation.resume(if (result.responseCode == BillingClient.BillingResponseCode.OK) purchases else null)
        }
    }

    private suspend fun acknowledge(purchase: Purchase) = suspendCancellableCoroutine { continuation ->
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        client.acknowledgePurchase(params) { continuation.resume(Unit) }
    }

    private fun safeFailure(): ProEntitlementState = ProEntitlementState.Error(retainedPro = lastConfirmedPro)

    override fun close() = client.endConnection()
}
