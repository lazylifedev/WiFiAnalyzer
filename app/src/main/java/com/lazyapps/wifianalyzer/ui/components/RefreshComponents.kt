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
import com.lazyapps.wifianalyzer.ui.scan.ScanUiState

@Composable
fun RefreshProgress(state: ScanUiState, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        if (state.refreshSecondsRemaining != null || state.isRefreshing) {
            val progressModifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .semantics {
                        contentDescription = if (state.isRefreshing) {
                            "Wi-Fiスキャンを要求中"
                        } else {
                            state.refreshSecondsRemaining?.let { "次のスキャン要求まであと${it}秒" }.orEmpty()
                        }
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
