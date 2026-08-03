package com.lazyapps.wifianalyzer.export

data class ReportPhoto(val caption: String, val dataUri: String?)
data class ReportDevice(val workspace: String, val group: String?, val name: String, val manufacturer: String, val model: String, val serial: String, val ssid: String, val bssids: List<String>, val location: String, val notes: String, val detected: String, val rssi: String?, val distance: String?, val lastSeen: String?, val photos: List<ReportPhoto>)
data class ReportLabels(
    val languageTag: String, val createdAt: String, val workspace: String, val group: String,
    val manufacturer: String, val model: String, val serial: String, val bssids: String,
    val location: String, val notes: String, val detected: String, val distance: String,
    val lastSeen: String, val deviceCount: String,
)

object ReportGenerator {
    fun generate(title: String, createdAt: String, labels: ReportLabels, devices: Sequence<ReportDevice>): String = buildString {
        append("<!doctype html><html lang=\"").append(escape(labels.languageTag)).append("\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width\"><style>")
        append("@page{size:A4 portrait;margin:14mm}body{font-family:sans-serif;color:#202124;background:#fff;line-height:1.5;overflow-wrap:anywhere}h1{font-size:22px}article{break-inside:avoid;border-top:1px solid #aaa;padding:12px 0}dl{display:grid;grid-template-columns:minmax(7em,11em) minmax(0,1fr);gap:4px 12px}dt{font-weight:700}dd{margin:0;overflow-wrap:anywhere}.photos{display:flex;flex-wrap:wrap;gap:8px}.photos img{max-width:240px;max-height:180px;object-fit:contain}small{color:#555}@media(max-width:600px){dl{grid-template-columns:minmax(6em,40%) minmax(0,1fr)}}@media print{article{break-inside:avoid-page}}")
        append("</style></head><body><h1>").append(escape(title)).append("</h1><p><small>").append(escape(labels.createdAt)).append(": ").append(escape(createdAt)).append("</small></p>")
        var count = 0
        devices.forEach { d -> count++ ; append("<article><h2>").append(escape(d.name)).append("</h2><dl>")
            field(labels.workspace, d.workspace); field(labels.group, d.group.orEmpty()); field(labels.manufacturer, d.manufacturer); field(labels.model, d.model); field(labels.serial, d.serial)
            field("SSID", d.ssid); field(labels.bssids, d.bssids.joinToString("; ")); field(labels.location, d.location); field(labels.notes, d.notes); field(labels.detected, d.detected)
            field("RSSI", d.rssi.orEmpty()); field(labels.distance, d.distance.orEmpty()); field(labels.lastSeen, d.lastSeen.orEmpty()); append("</dl>")
            if (d.photos.isNotEmpty()) { append("<div class=\"photos\">"); d.photos.forEach { p -> p.dataUri?.let { append("<figure><img alt=\"").append(escape(p.caption)).append("\" src=\"").append(it).append("\"><figcaption>").append(escape(p.caption)).append("</figcaption></figure>") } }; append("</div>") }
            append("</article>")
        }
        append("<p>").append(escape(labels.deviceCount)).append(": ").append(count).append("</p></body></html>")
    }
    fun escape(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
    private fun StringBuilder.field(label: String, value: String) { append("<dt>").append(label).append("</dt><dd>").append(escape(value)).append("</dd>") }
}
