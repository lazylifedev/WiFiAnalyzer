package com.lazyapps.wifianalyzer.model

enum class WifiBand(val label: String) { BAND_24("2.4 GHz"), BAND_5("5 GHz"), BAND_6("6 GHz") }

data class WifiNetwork(
    val ssid: String,
    val bssid: String,
    val dbm: Int,
    val distance: String,
    val channel: Int,
    val frequencyMhz: Int,
    val security: String,
    val registered: Boolean = false,
)

data class ChannelUsage(
    val channel: Int,
    val frequencyMhz: Int,
    val occupancy: Float,
    val networks: List<WifiNetwork>,
)

data class RegisteredDevice(
    val name: String,
    val address: String,
    val group: String,
    val lastSeen: String,
    val detected: Boolean,
    val signalLevel: Int,
)
