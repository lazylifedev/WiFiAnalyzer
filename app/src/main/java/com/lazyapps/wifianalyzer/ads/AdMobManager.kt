package com.lazyapps.wifianalyzer.ads

import android.app.Activity
import androidx.compose.runtime.mutableStateOf
import com.lazyapps.wifianalyzer.BuildConfig
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

object AdMobManager {
    val canRequestAds = mutableStateOf(false)
    val mobileAdsInitialized = mutableStateOf(false)
    val privacyOptionsRequired = mutableStateOf(false)
    private var consentUpdateInFlight = false
    private var initialized = false

    @Synchronized
    fun initialize(activity: Activity) {
        if (!isUsable(activity) || consentUpdateInFlight || initialized) return
        consentUpdateInFlight = true
        val info = UserMessagingPlatform.getConsentInformation(activity)
        val paramsBuilder = ConsentRequestParameters.Builder()
        if (BuildConfig.DEBUG && BuildConfig.UMP_FORCE_EEA && BuildConfig.UMP_TEST_DEVICE_HASH.isNotBlank()) {
            val debugSettings = com.google.android.ump.ConsentDebugSettings.Builder(activity)
                .setDebugGeography(com.google.android.ump.ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                .addTestDeviceHashedId(BuildConfig.UMP_TEST_DEVICE_HASH)
                .build()
            paramsBuilder.setConsentDebugSettings(debugSettings)
        }
        val params = paramsBuilder.build()
        info.requestConsentInfoUpdate(activity, params, {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                finishConsentFlow(activity, info)
            }
        }, {
            finishConsentFlow(activity, info)
        })
    }

    @Synchronized
    fun showPrivacyOptions(activity: Activity) {
        if (!isUsable(activity) || !privacyOptionsRequired.value) return
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            if (error == null) {
                val info = UserMessagingPlatform.getConsentInformation(activity)
                updateState(info)
                if (info.canRequestAds() && !initialized) initializeMobileAds(activity)
            }
        }
    }

    @Synchronized
    private fun finishConsentFlow(activity: Activity, info: ConsentInformation) {
        consentUpdateInFlight = false
        updateState(info)
        if (canRequestAds.value) initializeMobileAds(activity)
    }

    private fun updateState(info: ConsentInformation) {
        canRequestAds.value = info.canRequestAds()
        privacyOptionsRequired.value = info.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    @Synchronized
    private fun initializeMobileAds(activity: Activity) {
        if (initialized || !isUsable(activity)) return
        initialized = true
        MobileAds.initialize(activity.applicationContext) {
            mobileAdsInitialized.value = true
        }
    }

    private fun isUsable(activity: Activity): Boolean =
        !activity.isFinishing && !(android.os.Build.VERSION.SDK_INT >= 17 && activity.isDestroyed)
}
