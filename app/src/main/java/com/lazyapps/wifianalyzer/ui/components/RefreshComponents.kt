package com.lazyapps.wifianalyzer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.lazyapps.wifianalyzer.R
import com.lazyapps.wifianalyzer.ui.scan.ScanUiState

@Composable
fun RefreshProgress(state: ScanUiState, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        if (state.refreshSecondsRemaining != null || state.isRefreshing) {
            val progressDescription = if (state.isRefreshing) stringResource(R.string.requesting_wifi_scan)
                else stringResource(R.string.next_scan_request_seconds, state.refreshSecondsRemaining ?: 0)
            val progressModifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .semantics {
                        contentDescription = progressDescription
                    }
            if (state.isRefreshing) LinearProgressIndicator(
                modifier = progressModifier,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            ) else LinearProgressIndicator(
                progress = { state.refreshProgress },
                modifier = progressModifier,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}
