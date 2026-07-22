package com.lazyapps.wifianalyzer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lazyapps.wifianalyzer.R
import com.lazyapps.wifianalyzer.model.WifiBand
import com.lazyapps.wifianalyzer.ui.theme.AppSpacing

@Composable
fun ScreenHeader(title: String, supporting: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.large, vertical = AppSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium),
    ) {
        Icon(
            imageVector = Icons.Rounded.Wifi,
            contentDescription = stringResource(R.string.content_wifi_signal),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(30.dp),
        )
        androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            supporting?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        action?.invoke()
    }
}

@Composable
fun BandSelector(selected: WifiBand, onSelected: (WifiBand) -> Unit, modifier: Modifier = Modifier, bands: Set<WifiBand> = WifiBand.entries.toSet()) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
        WifiBand.entries.filter { it in bands }.forEach { band ->
            FilterChip(
                selected = selected == band,
                onClick = { onSelected(band) },
                label = { Text(band.label, maxLines = 1) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun RegisteredBadge(modifier: Modifier = Modifier) {
    AssistChip(
        onClick = {},
        label = { Text(stringResource(R.string.registered), style = MaterialTheme.typography.labelSmall) },
        modifier = modifier,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        border = BorderStroke(0.dp, MaterialTheme.colorScheme.primaryContainer),
    )
}

@Composable
fun SignalBars(level: Int, modifier: Modifier = Modifier) {
    val signalDescription = stringResource(R.string.signal_strength, level)
    Row(
        modifier = modifier.semantics { contentDescription = signalDescription },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        repeat(4) { index ->
            Surface(
                modifier = Modifier.size(width = 4.dp, height = (7 + index * 4).dp),
                shape = CircleShape,
                color = if (index < level) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                content = {},
            )
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}
