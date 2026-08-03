package com.lazyapps.wifianalyzer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import com.lazyapps.wifianalyzer.ui.WifiAnalyzerApp
import androidx.lifecycle.lifecycleScope
import com.lazyapps.wifianalyzer.review.ReviewHistoryRepository
import kotlinx.coroutines.launch
import com.lazyapps.wifianalyzer.ads.AdMobManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AdMobManager.initialize(this)
        if (savedInstanceState == null) {
            lifecycleScope.launch { ReviewHistoryRepository(applicationContext).recordLaunch() }
        }
        setContent {
            WifiAnalyzerApp()
        }
    }
}
