package com.lazyapps.wifianalyzer.billing

import android.app.Activity
import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.util.concurrent.atomic.AtomicBoolean

internal class BillingCloseGuard(
    private val cancelScope: () -> Unit,
    private val endConnection: () -> Unit,
) {
    private val didClose = AtomicBoolean(false)
    val closed: Boolean get() = didClose.get()

    fun close() {
        if (!didClose.compareAndSet(false, true)) return
        cancelScope()
        endConnection()
    }
}

class PlayBillingRepository(context: Context) : BillingRepository, PurchasesUpdatedListener {
    private val scopeJob = SupervisorJob()
    private val scope = CoroutineScope(scopeJob + Dispatchers.Main.immediate)
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
    private val closeGuard = BillingCloseGuard(
        cancelScope = scope::cancel,
        endConnection = client::endConnection,
    )

    override suspend fun connectAndRefresh(force: Boolean) {
        if (closeGuard.closed) return
        if (!client.isReady) {
            if (connecting) return
            connecting = true
            mutableSnapshot.value = mutableSnapshot.value.copy(connection = BillingConnectionState.CONNECTING)
            val connected = connect()
            connecting = false
            if (closeGuard.closed) return
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
        if (closeGuard.closed) return
        mutableSnapshot.value = mutableSnapshot.value.copy(restoring = true)
        connectAndRefresh(force = true)
        mutableSnapshot.value = mutableSnapshot.value.copy(restoring = false)
    }

    override fun launchPurchase(activity: Activity): Boolean {
        if (closeGuard.closed) return false
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
        if (closeGuard.closed) return
        mutableSnapshot.value = mutableSnapshot.value.copy(purchasing = false)
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> scope.launch { refreshInternal() }
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> mutableSnapshot.value = mutableSnapshot.value.copy(entitlement = safeFailure())
        }
    }

    private suspend fun connect(): Boolean = suspendCancellableCoroutine { continuation ->
        if (closeGuard.closed) {
            continuation.resume(false)
            return@suspendCancellableCoroutine
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (continuation.isActive) {
                    continuation.resume(
                        !closeGuard.closed && result.responseCode == BillingClient.BillingResponseCode.OK,
                    )
                }
            }
            override fun onBillingServiceDisconnected() {
                if (closeGuard.closed) return
                mutableSnapshot.value = mutableSnapshot.value.copy(connection = BillingConnectionState.DISCONNECTED)
            }
        })
    }

    private suspend fun refreshInternal() {
        if (closeGuard.closed) return
        mutableSnapshot.value = mutableSnapshot.value.copy(connection = BillingConnectionState.CONNECTED)
        val product = queryProduct()
        val purchases = queryPurchases()
        if (purchases == null) {
            mutableSnapshot.value = mutableSnapshot.value.copy(product = product, entitlement = safeFailure())
            return
        }
        val matching = purchases.filter { BillingProducts.PRO in it.products }
        val purchased = matching.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        purchased.forEach {
            when (val result = acknowledge(it)) {
                PurchaseAcknowledgementResult.Success,
                PurchaseAcknowledgementResult.AlreadyAcknowledged -> Unit
                is PurchaseAcknowledgementResult.RetryableFailure ->
                    debugLog("purchase acknowledgement retryable responseCode=${result.responseCode}")
                is PurchaseAcknowledgementResult.PermanentFailure ->
                    debugLog("purchase acknowledgement permanent responseCode=${result.responseCode}")
            }
        }
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

    private suspend fun acknowledge(purchase: Purchase): PurchaseAcknowledgementResult {
        if (purchase.isAcknowledged) return PurchaseAcknowledgementResult.AlreadyAcknowledged
        return suspendCancellableCoroutine { continuation ->
            val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
            client.acknowledgePurchase(params) { result ->
                if (continuation.isActive) {
                    continuation.resume(PurchaseAcknowledgementPolicy.classify(result.responseCode))
                }
            }
        }
    }

    private fun safeFailure(): ProEntitlementState = ProEntitlementState.Error(retainedPro = lastConfirmedPro)

    private fun debugLog(message: String) {
        if (com.lazyapps.wifianalyzer.BuildConfig.DEBUG) Log.d(TAG, message)
    }

    override fun close() {
        connecting = false
        closeGuard.close()
    }

    private companion object {
        const val TAG = "PlayBillingRepository"
    }
}
