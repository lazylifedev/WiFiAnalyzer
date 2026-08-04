package com.lazyapps.wifianalyzer.ads

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

object AdMobManager {
    val canRequestAds = mutableStateOf(false)
    val mobileAdsInitialized = mutableStateOf(false)
    val privacyOptionsRequired = mutableStateOf(false)
    private var initialized = false
    var applicationContext: Context? = null
        private set

    fun initialize(activity: Activity) {
        applicationContext = activity.applicationContext
        val info = UserMessagingPlatform.getConsentInformation(activity)
        val params = ConsentRequestParameters.Builder().build()
        info.requestConsentInfoUpdate(activity, params, {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                updateAndInitialize(activity, info)
            }
        }, {
            updateAndInitialize(activity, info)
        })
        updateAndInitialize(activity, info)
    }

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { }
    }

    private fun updateAndInitialize(context: Context, info: ConsentInformation) {
        canRequestAds.value = info.canRequestAds()
        privacyOptionsRequired.value = info.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        if (canRequestAds.value && !initialized) {
            initialized = true
            MobileAds.initialize(context.applicationContext) {
                mobileAdsInitialized.value = true
            }
        }
    }
}
