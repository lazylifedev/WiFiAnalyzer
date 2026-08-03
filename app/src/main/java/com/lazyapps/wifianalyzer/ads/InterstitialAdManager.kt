package com.lazyapps.wifianalyzer.ads

import android.app.Activity
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.lazyapps.wifianalyzer.billing.ProEntitlementState

object InterstitialAdManager {
    private var loadedAd: InterstitialAd? = null
    private var loading = false
    private var showing = false
    private var isPro = true
    private var candidates = 0
    private var shown = 0
    private var lastShownAt = 0L

    fun updateEntitlement(entitlement: ProEntitlementState, debugForcePro: Boolean = false) {
        isPro = debugForcePro || entitlement == ProEntitlementState.Pro ||
            (entitlement is ProEntitlementState.Error && entitlement.retainedPro)
        if (isPro) discardLoadedAd()
    }

    fun prepare() {
        if (isPro || !AdMobManager.canRequestAds.value || loading || loadedAd != null) return
        loading = true
        debugLog("interstitial load start")
        val context = AdMobManager.applicationContext ?: run { loading = false; return }
        InterstitialAd.load(context, AdConfiguration.interstitialUnitId, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                loading = false
                if (isPro) { ad.fullScreenContentCallback = null; return }
                loadedAd = ad
                debugLog("interstitial load success")
            }
            override fun onAdFailedToLoad(error: LoadAdError) {
                loading = false
                debugLog("interstitial load failed")
            }
        })
    }

    fun showAfterSuccessfulOperation(activity: Activity) {
        candidates++
        val lifecycleOwner = activity as? androidx.lifecycle.LifecycleOwner
        if (isPro || !AdMobManager.canRequestAds.value || lifecycleOwner == null || !lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return
        if (candidates <= AdConfiguration.initialCandidateSkipCount || shown >= AdConfiguration.interstitialSessionLimit || showing) return
        val now = System.currentTimeMillis()
        if (now - lastShownAt < AdConfiguration.interstitialMinimumIntervalMillis) return
        val ad = loadedAd ?: run { prepare(); return }
        loadedAd = null
        showing = true
        shown++
        lastShownAt = now
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() = debugLog("interstitial shown")
            override fun onAdDismissedFullScreenContent() { showing = false; debugLog("interstitial dismissed"); prepare() }
            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) { showing = false; debugLog("interstitial show failed"); prepare() }
        }
        ad.show(activity)
    }

    fun discardLoadedAd() {
        loadedAd?.fullScreenContentCallback = null
        loadedAd = null
        loading = false
    }

    private fun debugLog(message: String) { if (com.lazyapps.wifianalyzer.BuildConfig.DEBUG) Log.d("AdManager", message) }
}
