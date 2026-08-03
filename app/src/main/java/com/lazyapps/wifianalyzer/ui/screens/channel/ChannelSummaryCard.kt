package com.lazyapps.wifianalyzer.ui.screens.channel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.lazyapps.wifianalyzer.R
import com.lazyapps.wifianalyzer.domain.ChannelCandidate
import com.lazyapps.wifianalyzer.ui.theme.AppSpacing

@Composable
fun ChannelSummaryCard(
    detectedCount: Int,
    candidate: ChannelCandidate?,
    modifier: Modifier = Modifier,
) {
    val congestion = candidate?.congestion?.let {
        when {
            it < .3f -> stringResource(R.string.congestion_low)
            it < .6f -> stringResource(R.string.congestion_moderate)
            else -> stringResource(R.string.congestion_high)
        }
    } ?: stringResource(R.string.congestion_unknown)
    FlowRow(
        modifier = modifier.fillMaxWidth().padding(vertical = AppSpacing.xSmall),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xSmall),
    ) {
        Text(pluralStringResource(R.plurals.network_count, detectedCount, detectedCount), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.available_candidate), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            candidate?.let { stringResource(R.string.channel_format, it.channel) } ?: stringResource(R.string.no_candidate),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(stringResource(R.string.congestion_format, congestion), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
