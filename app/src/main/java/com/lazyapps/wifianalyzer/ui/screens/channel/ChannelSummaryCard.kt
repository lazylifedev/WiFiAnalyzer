package com.lazyapps.wifianalyzer.ui.screens.channel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .45f)),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        FlowRow(
            Modifier.fillMaxWidth().padding(AppSpacing.medium),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.small),
        ) {
            SummaryMetric("検出", "$detectedCount", Modifier.weight(1f).widthIn(min = 80.dp))
            SummaryMetric("空いている候補", candidate?.let { "CH ${it.channel}" } ?: "候補なし", Modifier.weight(1f).widthIn(min = 120.dp))
            SummaryMetric("混雑度", congestion, Modifier.weight(1f).widthIn(min = 80.dp))
        }
    }
}

@Composable
private fun SummaryMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}
