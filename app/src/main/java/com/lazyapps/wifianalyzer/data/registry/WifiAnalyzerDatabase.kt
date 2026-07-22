package com.lazyapps.wifianalyzer.data.registry

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [WorkspaceEntity::class, RegisteredWifiDeviceEntity::class, WifiDeviceBssidEntity::class, WifiDeviceGroupEntity::class, DevicePhotoEntity::class, PendingFileDeletionEntity::class],
    version = WifiAnalyzerDatabase.VERSION,
    exportSchema = true,
)
abstract class WifiAnalyzerDatabase : RoomDatabase() {
    abstract fun registryDao(): RegistryDao

    companion object {
        const val VERSION = 2

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val now = System.currentTimeMillis()
                db.execSQL("PRAGMA legacy_alter_table=OFF")
                db.execSQL("CREATE TABLE IF NOT EXISTS `workspaces` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `normalized_name` TEXT NOT NULL, `sort_order` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_workspaces_normalized_name` ON `workspaces` (`normalized_name`)")
                db.execSQL("INSERT INTO workspaces (id,name,normalized_name,sort_order,created_at,updated_at) VALUES (1,'default','default',0,$now,$now)")
                db.execSQL("CREATE TABLE `wifi_device_groups_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `normalized_name` TEXT NOT NULL, `sort_order` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `workspace_id` INTEGER NOT NULL, FOREIGN KEY(`workspace_id`) REFERENCES `workspaces`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("INSERT INTO wifi_device_groups_new SELECT id,name,normalized_name,sort_order,created_at,updated_at,1 FROM wifi_device_groups")
                db.execSQL("CREATE TABLE `registered_wifi_devices_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `display_name` TEXT NOT NULL, `manufacturer` TEXT NOT NULL, `model` TEXT NOT NULL, `serial_number` TEXT NOT NULL, `primary_bssid` TEXT NOT NULL, `ssid` TEXT NOT NULL, `group_id` INTEGER, `location` TEXT NOT NULL, `notes` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `last_seen_at` INTEGER, `last_seen_rssi` INTEGER, `is_enabled` INTEGER NOT NULL, `workspace_id` INTEGER NOT NULL, FOREIGN KEY(`group_id`) REFERENCES `wifi_device_groups_new`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL, FOREIGN KEY(`workspace_id`) REFERENCES `workspaces`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("INSERT INTO registered_wifi_devices_new SELECT id,display_name,manufacturer,model,serial_number,primary_bssid,ssid,group_id,location,notes,created_at,updated_at,last_seen_at,last_seen_rssi,is_enabled,1 FROM registered_wifi_devices")
                db.execSQL("CREATE TABLE `wifi_device_bssids_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `device_id` INTEGER NOT NULL, `bssid` TEXT NOT NULL, `band` TEXT NOT NULL, `label` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `workspace_id` INTEGER NOT NULL, FOREIGN KEY(`device_id`) REFERENCES `registered_wifi_devices_new`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`workspace_id`) REFERENCES `workspaces`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("INSERT INTO wifi_device_bssids_new SELECT id,device_id,bssid,band,label,created_at,1 FROM wifi_device_bssids")
                db.execSQL("DROP TABLE wifi_device_bssids")
                db.execSQL("DROP TABLE registered_wifi_devices")
                db.execSQL("DROP TABLE wifi_device_groups")
                db.execSQL("ALTER TABLE wifi_device_groups_new RENAME TO wifi_device_groups")
                db.execSQL("ALTER TABLE registered_wifi_devices_new RENAME TO registered_wifi_devices")
                db.execSQL("ALTER TABLE wifi_device_bssids_new RENAME TO wifi_device_bssids")
                db.execSQL("CREATE INDEX `index_wifi_device_groups_workspace_id` ON `wifi_device_groups` (`workspace_id`)")
                db.execSQL("CREATE UNIQUE INDEX `index_wifi_device_groups_workspace_id_normalized_name` ON `wifi_device_groups` (`workspace_id`,`normalized_name`)")
                db.execSQL("CREATE INDEX `index_registered_wifi_devices_group_id` ON `registered_wifi_devices` (`group_id`)")
                db.execSQL("CREATE INDEX `index_registered_wifi_devices_workspace_id` ON `registered_wifi_devices` (`workspace_id`)")
                db.execSQL("CREATE INDEX `index_registered_wifi_devices_workspace_id_display_name` ON `registered_wifi_devices` (`workspace_id`,`display_name`)")
                db.execSQL("CREATE INDEX `index_wifi_device_bssids_device_id` ON `wifi_device_bssids` (`device_id`)")
                db.execSQL("CREATE INDEX `index_wifi_device_bssids_workspace_id` ON `wifi_device_bssids` (`workspace_id`)")
                db.execSQL("CREATE UNIQUE INDEX `index_wifi_device_bssids_workspace_id_bssid` ON `wifi_device_bssids` (`workspace_id`,`bssid`)")
                db.execSQL("CREATE TABLE `device_photos` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `device_id` INTEGER NOT NULL, `workspace_id` INTEGER NOT NULL, `file_name` TEXT NOT NULL, `mime_type` TEXT NOT NULL, `width` INTEGER NOT NULL, `height` INTEGER NOT NULL, `file_size` INTEGER NOT NULL, `sort_order` INTEGER NOT NULL, `caption` TEXT NOT NULL, `is_primary` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, FOREIGN KEY(`device_id`) REFERENCES `registered_wifi_devices`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`workspace_id`) REFERENCES `workspaces`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX `index_device_photos_device_id` ON `device_photos` (`device_id`)")
                db.execSQL("CREATE INDEX `index_device_photos_workspace_id` ON `device_photos` (`workspace_id`)")
                db.execSQL("CREATE INDEX `index_device_photos_device_id_sort_order` ON `device_photos` (`device_id`,`sort_order`)")
                db.execSQL("CREATE TABLE `pending_file_deletions` (`path` TEXT NOT NULL, `created_at` INTEGER NOT NULL, PRIMARY KEY(`path`))")
            }
        }

        @Volatile private var instance: WifiAnalyzerDatabase? = null

        fun get(context: Context): WifiAnalyzerDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                WifiAnalyzerDatabase::class.java,
                "wifi_analyzer.db",
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
