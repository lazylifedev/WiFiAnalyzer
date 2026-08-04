package com.lazyapps.wifianalyzer.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.lazyapps.wifianalyzer.R

object LegalLinks {
    const val PRIVACY_POLICY_URL = "https://lazylifedev.com/wifianalyzer-privacy-policy/"
    const val TERMS_OF_SERVICE_URL = "https://lazylifedev.com/wifianalyzer-terms-of-service/"

    fun open(context: Context, url: String): Boolean {
        val uri = Uri.parse(url)
        if (uri.scheme != "https" || uri.host != "lazylifedev.com" ||
            url !in setOf(PRIVACY_POLICY_URL, TERMS_OF_SERVICE_URL)) return false
        return try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            true
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.external_link_unavailable, Toast.LENGTH_SHORT).show()
            false
        }
    }
}
