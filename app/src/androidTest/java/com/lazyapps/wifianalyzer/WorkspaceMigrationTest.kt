package com.lazyapps.wifianalyzer

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lazyapps.wifianalyzer.data.registry.WifiAnalyzerDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceMigrationTest {
    @Test fun migrate1To2PreservesRegistryAndAssignsDefault() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "workspace-migration.db"; context.deleteDatabase(name)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            db.execSQL("PRAGMA foreign_keys=ON")
            db.execSQL("CREATE TABLE wifi_device_groups (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,name TEXT NOT NULL,normalized_name TEXT NOT NULL,sort_order INTEGER NOT NULL,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)")
            db.execSQL("CREATE UNIQUE INDEX index_wifi_device_groups_normalized_name ON wifi_device_groups(normalized_name)")
            db.execSQL("CREATE TABLE registered_wifi_devices (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,display_name TEXT NOT NULL,manufacturer TEXT NOT NULL,model TEXT NOT NULL,serial_number TEXT NOT NULL,primary_bssid TEXT NOT NULL,ssid TEXT NOT NULL,group_id INTEGER,location TEXT NOT NULL,notes TEXT NOT NULL,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL,last_seen_at INTEGER,last_seen_rssi INTEGER,is_enabled INTEGER NOT NULL,FOREIGN KEY(group_id) REFERENCES wifi_device_groups(id) ON DELETE SET NULL)")
            db.execSQL("CREATE INDEX index_registered_wifi_devices_group_id ON registered_wifi_devices(group_id)")
            db.execSQL("CREATE INDEX index_registered_wifi_devices_display_name ON registered_wifi_devices(display_name)")
            db.execSQL("CREATE TABLE wifi_device_bssids (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,device_id INTEGER NOT NULL,bssid TEXT NOT NULL,band TEXT NOT NULL,label TEXT NOT NULL,created_at INTEGER NOT NULL,FOREIGN KEY(device_id) REFERENCES registered_wifi_devices(id) ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX index_wifi_device_bssids_device_id ON wifi_device_bssids(device_id)")
            db.execSQL("CREATE UNIQUE INDEX index_wifi_device_bssids_bssid ON wifi_device_bssids(bssid)")
            db.execSQL("INSERT INTO wifi_device_groups VALUES (1,'Office','office',0,10,11)")
            db.execSQL("INSERT INTO registered_wifi_devices VALUES (1,'Router','','','','AA:BB:CC:DD:EE:FF','Net',1,'','',10,11,12,-55,1)")
            db.execSQL("INSERT INTO wifi_device_bssids VALUES (1,1,'AA:BB:CC:DD:EE:FF','5 GHz','',10)")
            db.version = 1
        }
        val room = Room.databaseBuilder(context, WifiAnalyzerDatabase::class.java, name).addMigrations(WifiAnalyzerDatabase.MIGRATION_1_2).build()
        val dao = room.registryDao(); val device = dao.getDevice(1)!!; val bssid = dao.getBssids(1).single()
        assertEquals(1L, device.workspaceId); assertEquals(1L, device.groupId); assertEquals(12L, device.lastSeenAt); assertEquals(-55, device.lastSeenRssi)
        assertEquals(1L, bssid.workspaceId); assertEquals("AA:BB:CC:DD:EE:FF", bssid.bssid); assertEquals("default", dao.getWorkspace(1)?.name)
        room.close(); context.deleteDatabase(name); Unit
    }
}
