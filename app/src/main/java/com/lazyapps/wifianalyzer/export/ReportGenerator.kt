package com.lazyapps.wifianalyzer.export

data class ReportPhoto(val caption: String, val dataUri: String?)
data class ReportDevice(val workspace: String, val group: String?, val name: String, val manufacturer: String, val model: String, val serial: String, val ssid: String, val bssids: List<String>, val location: String, val notes: String, val detected: String, val rssi: String?, val distance: String?, val lastSeen: String?, val photos: List<ReportPhoto>)

object ReportGenerator {
    fun generate(title: String, createdAt: String, devices: Sequence<ReportDevice>): String = buildString {
        append("<!doctype html><html lang=\"ja\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width\"><style>")
        append("@page{size:A4 portrait;margin:14mm}body{font-family:sans-serif;color:#202124;background:#fff;line-height:1.5}h1{font-size:22px}article{break-inside:avoid;border-top:1px solid #aaa;padding:12px 0}dl{display:grid;grid-template-columns:9em 1fr;gap:4px 12px}dt{font-weight:700}dd{margin:0;overflow-wrap:anywhere}.photos{display:flex;flex-wrap:wrap;gap:8px}.photos img{max-width:240px;max-height:180px;object-fit:contain}small{color:#555}@media print{article{break-inside:avoid-page}}")
        append("</style></head><body><h1>").append(escape(title)).append("</h1><p><small>作成日時: ").append(escape(createdAt)).append("</small></p>")
        var count = 0
        devices.forEach { d -> count++ ; append("<article><h2>").append(escape(d.name)).append("</h2><dl>")
            field("ワークスペース", d.workspace); field("グループ", d.group.orEmpty()); field("メーカー", d.manufacturer); field("型番", d.model); field("シリアル番号", d.serial)
            field("SSID", d.ssid); field("BSSID一覧", d.bssids.joinToString("; ")); field("設置場所", d.location); field("メモ", d.notes); field("検出状態", d.detected)
            field("RSSI", d.rssi.orEmpty()); field("推定距離", d.distance.orEmpty()); field("最終検出", d.lastSeen.orEmpty()); append("</dl>")
            if (d.photos.isNotEmpty()) { append("<div class=\"photos\">"); d.photos.forEach { p -> p.dataUri?.let { append("<figure><img alt=\"").append(escape(p.caption)).append("\" src=\"").append(it).append("\"><figcaption>").append(escape(p.caption)).append("</figcaption></figure>") } }; append("</div>") }
            append("</article>")
        }
        append("<p>機器件数: ").append(count).append("</p></body></html>")
    }
    fun escape(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
    private fun StringBuilder.field(label: String, value: String) { append("<dt>").append(label).append("</dt><dd>").append(escape(value)).append("</dd>") }
}
