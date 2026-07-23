package com.lazyapps.wifianalyzer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import com.lazyapps.wifianalyzer.ui.WifiAnalyzerApp
import androidx.lifecycle.lifecycleScope
import com.lazyapps.wifianalyzer.review.ReviewHistoryRepository
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            lifecycleScope.launch { ReviewHistoryRepository(applicationContext).recordLaunch() }
        }
        setContent {
            WifiAnalyzerApp()
        }
    }
}
