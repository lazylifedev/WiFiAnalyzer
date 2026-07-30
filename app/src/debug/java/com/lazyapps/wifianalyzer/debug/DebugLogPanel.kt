package com.lazyapps.wifianalyzer.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DebugLogPanel(
    store: DebugLogStore,
    modifier: Modifier = Modifier,
) {
    val entries by store.entries.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(true) }
    val context = LocalContext.current
    Surface(
        modifier = modifier.fillMaxWidth().testTag("debug_log_panel"),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 6.dp,
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("DEBUG LOG (${entries.size})", style = MaterialTheme.typography.labelSmall)
                Row {
                    TextButton(
                        modifier = Modifier.testTag("debug_log_copy"),
                        onClick = { context.copyDebugLog(entries) },
                    ) { Text("コピー") }
                    TextButton(
                        modifier = Modifier.testTag("debug_log_clear"),
                        onClick = store::clear,
                    ) { Text("クリア") }
                    TextButton(
                        modifier = Modifier.testTag("debug_log_toggle"),
                        onClick = { expanded = !expanded },
                    ) { Text(if (expanded) "折りたたむ" else "展開") }
                }
            }
            if (expanded) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .testTag("debug_log_entries"),
                    reverseLayout = true,
                ) {
                    items(entries.asReversed(), key = { it.id }) { entry ->
                        Text(
                            text = entry.displayText(),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                        )
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
