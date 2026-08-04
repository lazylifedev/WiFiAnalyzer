package com.lazyapps.wifianalyzer

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lazyapps.wifianalyzer.data.registry.WifiAnalyzerDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class SignalHistoryMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), WifiAnalyzerDatabase::class.java)

    @Test @Throws(IOException::class)
    fun migration3To4CreatesHistoryTableAndIndexes() {
        val name = "history_3_4.db"
        helper.createDatabase(name, 3).close()
        val db = helper.runMigrationsAndValidate(name, 4, true, WifiAnalyzerDatabase.MIGRATION_3_4)
        assertTrue(db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='signal_history'").use { it.moveToFirst() })
        assertEquals(0, db.query("SELECT COUNT(*) FROM signal_history").use { it.moveToFirst(); it.getInt(0) })
        db.close()
    }

    @Test @Throws(IOException::class)
    fun migration4To5RemovesDuplicatesAndAddsUniqueIndex() {
        val name = "history_4_5.db"
        val db4 = helper.createDatabase(name, 4)
        insert(db4, 1, "aa", 100, -50)
        insert(db4, 1, "aa", 100, -60)
        insert(db4, 2, "aa", 100, -70)
        db4.close()
        val db5 = helper.runMigrationsAndValidate(name, 5, true, WifiAnalyzerDatabase.MIGRATION_4_5)
        assertEquals(2, db5.query("SELECT COUNT(*) FROM signal_history").use { it.moveToFirst(); it.getInt(0) })
        assertTrue(db5.query("SELECT name FROM sqlite_master WHERE type='index' AND name='index_signal_history_workspace_id_bssid_timestamp_millis'").use { it.moveToFirst() })
        db5.close()
    }

    private fun insert(db: SupportSQLiteDatabase, workspace: Long, bssid: String, timestamp: Long, rssi: Int) {
        db.execSQL("INSERT INTO signal_history(workspace_id,bssid,ssid,timestamp_millis,rssi,frequency_mhz,channel,band) VALUES(?,?,?,?,?,?,?,?)", arrayOf(workspace, bssid, "ssid", timestamp, rssi, 2412, 1, "BAND_24"))
    }
}
