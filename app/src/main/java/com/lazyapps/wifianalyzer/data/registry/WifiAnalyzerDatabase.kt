package com.lazyapps.wifianalyzer.data.registry

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [RegisteredWifiDeviceEntity::class, WifiDeviceBssidEntity::class, WifiDeviceGroupEntity::class],
    version = WifiAnalyzerDatabase.VERSION,
    exportSchema = true,
)
abstract class WifiAnalyzerDatabase : RoomDatabase() {
    abstract fun registryDao(): RegistryDao

    companion object {
        const val VERSION = 1

        @Volatile private var instance: WifiAnalyzerDatabase? = null

        fun get(context: Context): WifiAnalyzerDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                WifiAnalyzerDatabase::class.java,
                "wifi_analyzer.db",
            ).build().also { instance = it }
        }
    }
}
