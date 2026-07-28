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
            it < .3f -> "低い"
            it < .6f -> "普通"
            else -> "高い"
        }
    } ?: "判定できません"
    FlowRow(
        modifier = modifier.fillMaxWidth().padding(vertical = AppSpacing.xSmall),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xSmall),
    ) {
        Text("${detectedCount}件", style = MaterialTheme.typography.bodyMedium)
        Text("空いている候補", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            candidate?.let { "CH ${it.channel}" } ?: "候補なし",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
        Text("混雑度 $congestion", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
