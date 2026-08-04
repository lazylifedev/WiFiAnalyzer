package com.lazyapps.wifianalyzer.ui.components

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.lazyapps.wifianalyzer.R
import com.lazyapps.wifianalyzer.ui.scan.ScanUiState

val HomeScanProgressFraction = SemanticsPropertyKey<Float>("HomeScanProgressFraction")
var SemanticsPropertyReceiver.homeScanProgressFraction by HomeScanProgressFraction

internal fun refreshProgressAt(nowMillis: Long, cycleStartMillis: Long, intervalMillis: Long): Float =
    ((nowMillis - cycleStartMillis).coerceAtLeast(0L).toFloat() / intervalMillis).coerceIn(0f, 1f)

@Composable
fun SmoothScanProgressIndicator(
    state: ScanUiState,
    modifier: Modifier = Modifier,
    elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
    progressTag: String = "home_scan_progress",
    waitingContentDescription: Int = R.string.home_refresh_progress_waiting,
    refreshingContentDescription: Int = R.string.home_refresh_progress_refreshing,
) {
    val progress = remember { Animatable(0f) }
    val cycleStart = state.refreshCycleStartedElapsedMillis
    val intervalMillis = state.refreshIntervalMillis
    LaunchedEffect(cycleStart, intervalMillis, state.isRefreshing) {
        if (cycleStart == null || state.isRefreshing) return@LaunchedEffect
        val elapsed = (elapsedRealtime() - cycleStart).coerceAtLeast(0L)
        val currentProgress = refreshProgressAt(elapsedRealtime(), cycleStart, intervalMillis)
        progress.snapTo(currentProgress)
        val remainingMillis = (intervalMillis - elapsed).coerceAtLeast(0L)
        if (remainingMillis > 0L) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(remainingMillis.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), easing = LinearEasing),
            )
        }
    }
    Column(modifier.fillMaxWidth()) {
        val progressDescription = if (state.isRefreshing) stringResource(refreshingContentDescription)
            else stringResource(waitingContentDescription)
        val progressModifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .testTag(progressTag)
            .clearAndSetSemantics {
                contentDescription = progressDescription
                homeScanProgressFraction = if (cycleStart == null) 0f else progress.value
            }
        if (state.isRefreshing) LinearProgressIndicator(
                modifier = progressModifier,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
        ) else LinearProgressIndicator(
                progress = { if (cycleStart == null) 0f else progress.value },
                modifier = progressModifier,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
fun RefreshProgress(
    state: ScanUiState,
    modifier: Modifier = Modifier,
    elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) = SmoothScanProgressIndicator(state, modifier, elapsedRealtime)
