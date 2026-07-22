package com.lazyapps.wifianalyzer.sampledata

import com.lazyapps.wifianalyzer.model.ChannelUsage
import com.lazyapps.wifianalyzer.model.RegisteredDevice
import com.lazyapps.wifianalyzer.model.WifiNetwork

object SampleData {
    val nearbyNetworks = listOf(
        WifiNetwork("Osaka_Metro_Wi-Fi", "38:45:3B:05:DD:C8", -58, "15〜25 m", 11, 2462, "WPA2", true),
        WifiNetwork("MR1_2D5PAKFH", "54:AF:97:2D:5A:10", -64, "3〜8 m", 6, 2437, "WPA2"),
        WifiNetwork("SEI-2F760", "80:61:5F:2F:76:00", -72, "25〜40 m", 11, 2462, "WPA2"),
        WifiNetwork("IPCAM-515234", "9C:8E:CD:51:52:34", -78, "40〜70 m", 1, 2412, "WPA2", true),
        WifiNetwork("BCW720J-459C8-G", "24:A4:3C:45:9C:8F", -84, "70〜100 m", 11, 2462, "WPA2"),
    )

    val channelUsage = listOf(
        ChannelUsage(1, 2412, .38f, listOf(nearbyNetworks[3])),
        ChannelUsage(6, 2437, .66f, listOf(nearbyNetworks[1], WifiNetwork("Guest_WiFi", "74:12:B3:99:01:22", -76, "30〜60 m", 6, 2437, "WPA2"))),
        ChannelUsage(11, 2462, .88f, listOf(nearbyNetworks[0], nearbyNetworks[2], nearbyNetworks[4])),
    )

    val devices = listOf(
        RegisteredDevice("Office-AP_5G", "38:45:3B:05:DD:C8", "本社", "たった今", true, 4),
        RegisteredDevice("Office-AP_2F", "38:45:3B:05:DD:D0", "本社", "3時間前", false, 1),
        RegisteredDevice("2F-AP-Main", "CC:2D:E0:11:22:33", "2階", "2分前", true, 4),
        RegisteredDevice("2F-Camera_01", "CC:2D:E0:11:22:44", "2階", "5分前", true, 3),
        RegisteredDevice("Meeting-Router", "A0:18:28:34:7C:91", "会議室", "12分前", false, 2),
        RegisteredDevice("Warehouse-AP", "64:16:7F:8A:90:01", "倉庫", "1分前", true, 3),
    )

    val signalHistory = listOf(-61, -60, -61, -68, -68, -68, -63, -67, -61, -68, -65, -68, -67, -67, -61, -69, -69, -61, -68, -63, -63, -70, -67, -67, -60, -60, -68, -68, -68, -68)
}
