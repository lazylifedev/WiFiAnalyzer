package com.lazyapps.wifianalyzer.ads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.AdListener

@Composable
fun AdBanner(modifier: Modifier = Modifier) {
    if (!AdMobManager.canRequestAds.value || !AdMobManager.mobileAdsInitialized.value) return
    val context = LocalContext.current
    var loaded by remember { androidx.compose.runtime.mutableStateOf(false) }
    val adView = remember { AdView(context).apply {
        setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, 360))
        adUnitId = if (com.lazyapps.wifianalyzer.BuildConfig.DEBUG)
            "ca-app-pub-3940256099942544/9214589741"
        else "ca-app-pub-2834345829449590/6989135044"
    } }
    DisposableEffect(adView) {
        adView.adListener = object : AdListener() {
            override fun onAdLoaded() { loaded = true }
            override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) { loaded = false }
        }
        adView.loadAd(AdRequest.Builder().build())
        onDispose { adView.destroy() }
    }
    if (!loaded) return
    Box(modifier.fillMaxWidth().testTag("ad_banner")) {
        AndroidView(factory = { adView }, modifier = Modifier.fillMaxWidth())
    }
}
