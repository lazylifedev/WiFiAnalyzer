package com.lazyapps.wifianalyzer.ui.screens.channel

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lazyapps.wifianalyzer.domain.ChannelCandidate
import com.lazyapps.wifianalyzer.model.WifiAccessPoint
import com.lazyapps.wifianalyzer.model.WifiBand

@Composable
fun ChannelGraph(
    band: WifiBand,
    accessPoints: List<WifiAccessPoint>,
    selectedBssid: String?,
    candidate: ChannelCandidate?,
    onSelect: (String) -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val surface = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val outline = MaterialTheme.colorScheme.outline
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val animatedRssi = accessPoints.associate { ap ->
        val value by animateFloatAsState(ap.rssi.toFloat(), tween(500), label = "rssi_${ap.bssid}")
        ap.bssid to value
    }
    val range = remember(band, accessPoints.map { it.frequencyMhz to it.channelWidthMhz }) {
        ChannelGraphGeometry.rangeFor(band, accessPoints)
    }
    val left = with(density) { 48.dp.toPx() }
    val rightPadding = with(density) { 10.dp.toPx() }
    val top = with(density) { 16.dp.toPx() }
    val bottomPadding = with(density) { 34.dp.toPx() }
    val mountains = remember(accessPoints, animatedRssi, range, canvasSize) {
        if (canvasSize == IntSize.Zero) emptyList() else accessPoints.map { ap ->
            ChannelGraphGeometry.mountain(
                ap.copy(rssi = animatedRssi[ap.bssid]?.toInt() ?: ap.rssi),
                range,
                left,
                canvasSize.width - rightPadding,
                top,
                canvasSize.height - bottomPadding,
            )
        }
    }
    val strongest = accessPoints.maxByOrNull { it.rssi }
    val summary = buildString {
        append("${band.label}チャンネルグラフ。${accessPoints.size}ネットワークを検出。")
        strongest?.let { append("最も強いネットワークは${it.registeredDeviceName ?: it.ssid}、チャンネル${it.channel}、マイナス${-it.rssi}dBm。") }
        candidate?.let { append("空いている候補はチャンネル${it.channel}。") }
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val graphHeight = if (maxWidth >= 600.dp) 440.dp else 320.dp
        Canvas(
            Modifier.fillMaxWidth().height(graphHeight).testTag("channel_graph")
                .onSizeChanged { canvasSize = it }
                .semantics {
                    contentDescription = summary
                    role = Role.Button
                    onClick("最も強いネットワークを選択") {
                        strongest?.let { onSelect(it.bssid) }
                        strongest != null
                    }
                }
                .pointerInput(mountains) {
                    detectTapGestures { tap ->
                        val hit = ChannelGraphGeometry.hitTest(mountains, GraphPoint(tap.x, tap.y), 72.dp.toPx())
                        if (hit == null) onClearSelection() else onSelect(hit.bssid)
                    }
                },
        ) {
            val graphBottom = size.height - bottomPadding
            val graphRight = size.width - rightPadding
            listOf(-30, -50, -70, -90).forEach { rssi ->
                val y = ChannelGraphGeometry.rssiToY(rssi.toFloat(), top, graphBottom)
                drawLine(outline.copy(alpha = .22f), Offset(left, y), Offset(graphRight, y), 1.dp.toPx())
                drawText(
                    textMeasurer,
                    "$rssi",
                    Offset(2.dp.toPx(), y - 7.dp.toPx()),
                    TextStyle(onSurfaceVariant, 10.sp),
                )
            }
            drawText(textMeasurer, "dBm", Offset(2.dp.toPx(), graphBottom + 2.dp.toPx()), TextStyle(onSurfaceVariant, 9.sp))

            axisChannels(band, range, accessPoints).forEach { (channel, frequency) ->
                val x = ChannelGraphGeometry.frequencyToX(frequency.toFloat(), range, left, graphRight)
                drawLine(outline.copy(alpha = .12f), Offset(x, top), Offset(x, graphBottom), 1.dp.toPx())
                val label = textMeasurer.measure(channel.toString(), TextStyle(onSurfaceVariant, 10.sp))
                drawText(label, topLeft = Offset((x - label.size.width / 2).coerceIn(left, graphRight - label.size.width), graphBottom + 7.dp.toPx()))
            }

            val ordered = mountains.sortedWith(compareBy<GraphMountain> { it.accessPoint.bssid == selectedBssid }.thenBy { it.accessPoint.rssi })
            ordered.forEach { mountain ->
                val ap = mountain.accessPoint
                val selected = ap.bssid == selectedBssid
                val dimmed = selectedBssid != null && !selected
                val variation = ((ap.bssid.hashCode() and Int.MAX_VALUE) % 100) / 100f
                val color = lerp(primary, tertiary, variation * .35f)
                val path = Path().apply {
                    moveTo(mountain.leftX, graphBottom)
                    cubicTo(
                        mountain.leftX + (mountain.peak.x - mountain.leftX) * .22f, graphBottom,
                        mountain.peak.x - (mountain.peak.x - mountain.leftX) * .32f, mountain.peak.y,
                        mountain.peak.x, mountain.peak.y,
                    )
                    cubicTo(
                        mountain.peak.x + (mountain.rightX - mountain.peak.x) * .32f, mountain.peak.y,
                        mountain.rightX - (mountain.rightX - mountain.peak.x) * .22f, graphBottom,
                        mountain.rightX, graphBottom,
                    )
                    close()
                }
                val alpha = if (dimmed) .07f else if (selected) .30f else .14f
                drawPath(path, color.copy(alpha = alpha), style = Fill)
                drawPath(path, color.copy(alpha = if (dimmed) .28f else .82f), style = Stroke(if (selected) 3.dp.toPx() else if (ap.isRegistered) 2.dp.toPx() else 1.35.dp.toPx()))
                if (selected) drawCircle(color, 5.dp.toPx(), Offset(mountain.peak.x, mountain.peak.y))
                if (ap.isRegistered) {
                    drawCircle(surface, 7.dp.toPx(), Offset(mountain.peak.x + 9.dp.toPx(), mountain.peak.y - 3.dp.toPx()))
                    drawCircle(color, 5.dp.toPx(), Offset(mountain.peak.x + 9.dp.toPx(), mountain.peak.y - 3.dp.toPx()))
                }
            }

            val labelCandidates = mountains
                .sortedWith(compareByDescending<GraphMountain> { it.accessPoint.bssid == selectedBssid }
                    .thenByDescending { it.accessPoint.isRegistered }
                    .thenByDescending { it.accessPoint.rssi })
                .filterIndexed { index, item -> index < 5 || item.accessPoint.isRegistered || item.accessPoint.bssid == selectedBssid }
                .map { mountain ->
                    val ap = mountain.accessPoint
                    val text = "${(ap.registeredDeviceName ?: ap.ssid).take(18)}  ${accessPoints.first { it.bssid == ap.bssid }.rssi} dBm"
                    val layout = textMeasurer.measure(text, TextStyle(onSurface, 10.sp))
                    val width = layout.size.width + 12.dp.toPx()
                    val height = layout.size.height + 8.dp.toPx()
                    val leftX = mountain.peak.x - width / 2
                    val topY = mountain.peak.y - height - 8.dp.toPx()
                    Triple(ap.bssid, GraphLabelRect(leftX, topY, leftX + width, topY + height), text to layout)
                }
            val placed = ChannelGraphGeometry.placeLabels(
                labelCandidates.map { it.first to it.second },
                GraphLabelRect(left, top, graphRight, graphBottom),
            ).associate { it.first to it.second }
            labelCandidates.forEach { (bssid, _, content) ->
                placed[bssid]?.let { rect ->
                    drawRoundRect(
                        surface.copy(alpha = .94f),
                        Offset(rect.left, rect.top),
                        androidx.compose.ui.geometry.Size(rect.right - rect.left, rect.bottom - rect.top),
                        androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
                    )
                    drawText(content.second, topLeft = Offset(rect.left + 6.dp.toPx(), rect.top + 4.dp.toPx()))
                }
            }
        }
    }
}

private fun axisChannels(
    band: WifiBand,
    range: GraphRange,
    accessPoints: List<WifiAccessPoint>,
): List<Pair<Int, Int>> = when (band) {
    WifiBand.BAND_24 -> listOf(1 to 2412, 3 to 2422, 6 to 2437, 9 to 2452, 11 to 2462, 14 to 2484)
    WifiBand.BAND_5, WifiBand.BAND_6 -> accessPoints
        .filter { it.channel > 0 && it.frequencyMhz in range.minFrequencyMhz.toInt()..range.maxFrequencyMhz.toInt() }
        .sortedBy { it.frequencyMhz }
        .distinctBy { it.channel }
        .filterIndexed { index, _ -> index % 2 == 0 }
        .map { it.channel to it.frequencyMhz }
}
