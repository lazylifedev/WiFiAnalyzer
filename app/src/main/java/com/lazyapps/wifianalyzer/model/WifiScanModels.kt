package com.lazyapps.wifianalyzer.model

enum class SignalQuality(val label: String) {
    EXCELLENT("非常に良好"),
    GOOD("良好"),
    FAIR("普通"),
    WEAK("弱い"),
}

enum class SecurityType(val label: String) {
    OPEN("オープン"),
    WEP("WEP"),
    WPA("WPA"),
    WPA2("WPA2"),
    WPA3("WPA3"),
    OWE("Enhanced Open"),
    ENTERPRISE("Enterprise"),
    UNKNOWN("不明"),
}

enum class WifiStandard(val label: String) {
    LEGACY("Legacy"),
    WIFI_4("Wi-Fi 4"),
    WIFI_5("Wi-Fi 5"),
    WIFI_6("Wi-Fi 6"),
    WIFI_6E("Wi-Fi 6E"),
    WIFI_7("Wi-Fi 7"),
    UNKNOWN("不明"),
}

enum class DistanceRange(val label: String) {
    ONE_TO_THREE("約1～3m"),
    THREE_TO_EIGHT("約3～8m"),
    EIGHT_TO_TWENTY("約8～20m"),
    TWENTY_PLUS("約20m以上"),
}

data class WifiAccessPoint(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val frequencyMhz: Int,
    val channel: Int,
    val channelWidthMhz: Int,
    val capabilities: String,
    val timestampMicros: Long,
    val band: WifiBand,
    val signalQuality: SignalQuality,
    val securityType: SecurityType,
    val wifiStandard: WifiStandard,
    val distanceRange: DistanceRange,
    val observedAtMillis: Long,
    val isRegistered: Boolean = false,
    val registeredDeviceId: Long? = null,
    val registeredDeviceName: String? = null,
    val registeredGroupName: String? = null,
)

enum class ScanState {
    PERMISSION_REQUIRED,
    PERMISSION_DENIED,
    PERMISSION_PERMANENTLY_DENIED,
    LOCATION_DISABLED,
    WIFI_DISABLED,
    SCANNING,
    THROTTLED,
    EMPTY,
    READY,
    ERROR,
}

data class SignalSample(val timestampMillis: Long, val rssi: Int)

data class ChannelOccupancy(
    val channel: Int,
    val frequencyMhz: Int,
    val estimatedCongestion: Float,
    val accessPoints: List<WifiAccessPoint>,
)
