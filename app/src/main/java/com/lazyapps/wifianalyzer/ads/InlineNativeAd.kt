package com.lazyapps.wifianalyzer.ads

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.lazyapps.wifianalyzer.BuildConfig
import com.lazyapps.wifianalyzer.R

fun interface NativeAdLoader {
    fun load(onLoaded: (NativeAd) -> Unit, onFailed: (LoadAdError) -> Unit)
}

private class GoogleNativeAdLoader(context: android.content.Context, unitId: String) : NativeAdLoader {
    private val loader = AdLoader.Builder(context.applicationContext, unitId)
        .forNativeAd { pendingLoaded?.invoke(it) ?: it.destroy() }
        .withNativeAdOptions(NativeAdOptions.Builder().setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT).build())
        .withAdListener(object : com.google.android.gms.ads.AdListener() {
            override fun onAdFailedToLoad(error: LoadAdError) { pendingFailed?.invoke(error) }
        }).build()
    private var pendingLoaded: ((NativeAd) -> Unit)? = null
    private var pendingFailed: ((LoadAdError) -> Unit)? = null

    override fun load(onLoaded: (NativeAd) -> Unit, onFailed: (LoadAdError) -> Unit) {
        pendingLoaded = onLoaded
        pendingFailed = onFailed
        loader.loadAd(AdRequest.Builder().build())
    }
}

private class NativeAdLoadLifetime(var isActive: Boolean = true)

@Composable
fun InlineNativeAd(
    unitId: String,
    testTag: String,
    modifier: Modifier = Modifier,
    loaderFactory: ((android.content.Context, String) -> NativeAdLoader)? = null,
) {
    val nativeAd = rememberInlineNativeAd(
        unitId = unitId,
        enabled = true,
        requestEligible = true,
        debugPlacement = testTag,
        loaderFactory = loaderFactory,
    )
    InlineNativeAdContent(nativeAd, testTag, modifier)
}

@Composable
fun rememberInlineNativeAd(
    unitId: String,
    enabled: Boolean,
    requestEligible: Boolean,
    debugPlacement: String,
    loaderFactory: ((android.content.Context, String) -> NativeAdLoader)? = null,
): NativeAd? {
    val context = LocalContext.current
    val requestAllowed = enabled && AdMobManager.canRequestAds.value &&
        AdMobManager.mobileAdsInitialized.value && unitId.isNotBlank()
    val loader = remember(unitId, requestAllowed) {
        if (requestAllowed) loaderFactory?.invoke(context, unitId) ?: GoogleNativeAdLoader(context, unitId) else null
    }
    var nativeAd by remember(unitId, loader) { mutableStateOf<NativeAd?>(null) }
    var requested by remember(unitId, loader) { mutableStateOf(false) }
    val lifetime = remember(loader) { NativeAdLoadLifetime() }

    DisposableEffect(loader, lifetime) {
        onDispose {
            lifetime.isActive = false
            nativeAd?.destroy()
            nativeAd = null
        }
    }
    LaunchedEffect(loader, requestEligible) {
        if (loader == null || !requestEligible || requested) return@LaunchedEffect
        requested = true
        if (BuildConfig.DEBUG) Log.d("InlineNativeAd", "Native ad request: placement=$debugPlacement count=1")
        loader.load(
            onLoaded = { loaded ->
                if (!lifetime.isActive) loaded.destroy() else {
                    nativeAd?.destroy()
                    nativeAd = loaded
                }
            },
            onFailed = { error ->
                nativeAd?.destroy()
                nativeAd = null
                if (BuildConfig.DEBUG) Log.d("InlineNativeAd", "Native ad load failed: placement=$debugPlacement code=${error.code}")
            },
        )
    }
    return nativeAd
}

@Composable
fun InlineNativeAdContent(
    nativeAd: NativeAd?,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val loadedAd = nativeAd ?: return
    val label = stringResource(R.string.ad_label)
    val background = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant.toArgb()
    val border = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant.toArgb()
    val primary = androidx.compose.material3.MaterialTheme.colorScheme.primary.toArgb()
    val onSurface = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.toArgb()
    val onVariant = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    AndroidView(
        modifier = modifier.fillMaxWidth().testTag(testTag),
        factory = { viewContext -> createNativeAdView(viewContext, label, background, border, primary, onSurface, onVariant) },
        update = {
            updateNativeAdViewAppearance(it, label, background, border, primary, onSurface, onVariant)
            bindNativeAd(it, loadedAd)
        },
    )
}

private data class NativeAdViewRefs(
    val adLabel: TextView,
    val headline: TextView,
    val body: TextView,
    val advertiser: TextView,
)

private fun createNativeAdView(
    context: android.content.Context,
    label: String,
    background: Int,
    border: Int,
    primary: Int,
    onSurface: Int,
    onVariant: Int,
): NativeAdView {
    val density = context.resources.displayMetrics.density
    fun dp(value: Int) = (value * density).toInt()
    fun text(size: Float, color: Int) = TextView(context).apply { textSize = size; setTextColor(color) }
    val root = NativeAdView(context)
    root.background = GradientDrawable().apply {
        setColor(background); setStroke(dp(1), border); cornerRadius = dp(12).toFloat()
    }
    root.setPadding(dp(12), dp(10), dp(12), dp(10))
    val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 0, dp(28), 0)
    }
    val icon = ImageView(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(10) }
        contentDescription = null
    }
    val copy = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
    val adLabel = text(11f, primary).apply { this.text = label; setTypeface(typeface, android.graphics.Typeface.BOLD) }
    val headline = text(16f, onSurface).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; setTypeface(typeface, android.graphics.Typeface.BOLD) }
    val body = text(13f, onVariant).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END }
    val advertiser = text(12f, onVariant).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
    val cta = Button(context).apply { isAllCaps = false; minWidth = 0; minimumWidth = 0; maxLines = 2 }
    copy.addView(adLabel); copy.addView(headline); copy.addView(advertiser); copy.addView(body)
    row.addView(icon); row.addView(copy); row.addView(cta, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8) })
    root.addView(row, android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
    root.iconView = icon; root.headlineView = headline; root.bodyView = body; root.advertiserView = advertiser; root.callToActionView = cta
    root.tag = NativeAdViewRefs(adLabel, headline, body, advertiser)
    return root
}

private fun updateNativeAdViewAppearance(
    view: NativeAdView,
    label: String,
    background: Int,
    border: Int,
    primary: Int,
    onSurface: Int,
    onVariant: Int,
) {
    val density = view.resources.displayMetrics.density
    view.background = GradientDrawable().apply {
        setColor(background)
        setStroke((density).toInt().coerceAtLeast(1), border)
        cornerRadius = 12 * density
    }
    (view.tag as? NativeAdViewRefs)?.let { refs ->
        refs.adLabel.text = label
        refs.adLabel.setTextColor(primary)
        refs.headline.setTextColor(onSurface)
        refs.body.setTextColor(onVariant)
        refs.advertiser.setTextColor(onVariant)
    }
}

private fun bindNativeAd(view: NativeAdView, ad: NativeAd) {
    (view.headlineView as TextView).text = ad.headline
    (view.bodyView as TextView).apply { text = ad.body; visibility = if (ad.body == null) View.GONE else View.VISIBLE }
    (view.advertiserView as TextView).apply { text = ad.advertiser; visibility = if (ad.advertiser == null) View.GONE else View.VISIBLE }
    (view.callToActionView as Button).apply { text = ad.callToAction; visibility = if (ad.callToAction == null) View.GONE else View.VISIBLE }
    (view.iconView as ImageView).apply { setImageDrawable(ad.icon?.drawable); visibility = if (ad.icon == null) View.GONE else View.VISIBLE }
    view.setNativeAd(ad)
}
