package com.lazyapps.wifianalyzer.debug

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class DebugDisplayPreferences(@Suppress("UNUSED_PARAMETER") context: Context) {
    val enabled: Flow<Boolean> = flowOf(false)
    suspend fun setEnabled(@Suppress("UNUSED_PARAMETER") enabled: Boolean) = Unit
}
