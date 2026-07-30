package com.lazyapps.wifianalyzer.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal val DebugPanelBackground = Color(0xF211151A)
internal val DebugLogBackground = Color(0xFF080B0F)
internal val DebugPrimaryText = Color(0xFFF5F7FA)
internal val DebugSecondaryText = Color(0xFFB8C1CC)
internal val DebugActionColor = Color(0xFF80CBC4)
internal val DebugBorderColor = Color(0xFF58636F)

@Composable
fun DebugLogPanel(
    store: DebugLogStore,
    modifier: Modifier = Modifier,
) {
    val entries by store.entries.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(true) }
    val context = LocalContext.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = DebugBorderColor)
            .testTag("debug_log_panel"),
        color = DebugPanelBackground,
        contentColor = DebugPrimaryText,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "DEBUG LOG (${entries.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = DebugPrimaryText,
                )
                Row {
                    TextButton(
                        modifier = Modifier.testTag("debug_log_copy"),
                        onClick = { context.copyDebugLog(entries) },
                    ) { Text("コピー", color = DebugActionColor) }
                    TextButton(
                        modifier = Modifier.testTag("debug_log_clear"),
                        onClick = store::clear,
                    ) { Text("クリア", color = DebugActionColor) }
                    TextButton(
                        modifier = Modifier.testTag("debug_log_toggle"),
                        onClick = { expanded = !expanded },
                    ) {
                        Text(
                            text = if (expanded) "折りたたむ" else "展開",
                            color = DebugActionColor,
                        )
                    }
                }
            }
            if (expanded) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)
                        .background(DebugLogBackground)
                        .testTag("debug_log_entries"),
                    reverseLayout = true,
                ) {
                    items(entries.asReversed(), key = { it.id }) { entry ->
                        Column {
                            Text(
                                text = entry.displayText(),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = DebugPrimaryText,
                            )
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = DebugBorderColor.copy(alpha = 0.35f),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun DebugLogEntry.displayText(): String {
    val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(wallClockMillis))
    val repeat = if (repeated > 1) " repeated=$repeated" else ""
    val source = updateSource?.let { " source=$it" }.orEmpty()
    return "$time [$category] $message$source$repeat"
}

private fun Context.copyDebugLog(entries: List<DebugLogEntry>) {
    val clipboard = getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("Wi-Fi Analyzer debug log", entries.joinToString("\n") { it.displayText() }))
}
