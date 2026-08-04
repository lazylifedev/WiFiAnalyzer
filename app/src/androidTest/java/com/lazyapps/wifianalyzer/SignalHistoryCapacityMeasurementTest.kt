package com.lazyapps.wifianalyzer

import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lazyapps.wifianalyzer.data.registry.WifiAnalyzerDatabase
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.system.measureTimeMillis

@RunWith(AndroidJUnit4::class)
class SignalHistoryCapacityMeasurementTest {
    @Test fun measureSignalHistoryCapacity() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "signal-history-capacity.db"
        context.deleteDatabase(name)
        val db = Room.databaseBuilder(context, WifiAnalyzerDatabase::class.java, name).build()
        val sql = db.openHelper.writableDatabase
        val dbFile = context.getDatabasePath(name)
        fun size(path: File) = if (path.exists()) path.length() else 0L
        fun report(label: String) = Log.i("SignalHistoryCapacity", "$label db=${size(dbFile)} wal=${size(File(dbFile.path + "-wal"))} shm=${size(File(dbFile.path + "-shm"))}")
        report("empty")
        for (target in listOf(10_000, 100_000, 500_000)) {
            sql.execSQL("DELETE FROM signal_history")
            sql.query("PRAGMA wal_checkpoint(TRUNCATE)").use { }
            val elapsed = measureTimeMillis {
                sql.beginTransaction()
                try {
                    for (i in 0 until target) {
                        val bssid = "02:00:%02X:%02X:%02X:%02X".format(i / 16_777_216 % 256, i / 65_536 % 256, i / 256 % 256, i % 256)
                        val ssid = if (i % 3 == 0) "Office-$i" else if (i % 3 == 1) "長い日本語ネットワーク-$i" else "GuestNetwork-$i"
                        sql.execSQL("INSERT INTO signal_history(workspace_id,bssid,ssid,timestamp_millis,rssi,frequency_mhz,channel,band) VALUES(?,?,?,?,?,?,?,?)", arrayOf(42L, bssid, ssid, 1_700_000_000_000L + i * 20_000L, -30 - (i % 65), if (i % 2 == 0) 2412 else 5180, if (i % 2 == 0) 1 else 36, if (i % 2 == 0) "BAND_24" else "BAND_5"))
                    }
                    sql.setTransactionSuccessful()
                } finally { sql.endTransaction() }
            }
            report("rows=$target insertMs=$elapsed")
            val queryMs = measureTimeMillis { sql.query("SELECT * FROM signal_history WHERE workspace_id=42 AND bssid=? ORDER BY timestamp_millis DESC LIMIT 900", arrayOf("02:00:00:00:00:01")).use { it.count } }
            val deleteMs = measureTimeMillis { sql.beginTransaction(); try { sql.execSQL("DELETE FROM signal_history WHERE timestamp_millis < ?", arrayOf(1_700_000_000_000L + target * 20_000L - 86_400_000L)); } finally { sql.endTransaction() } }
            Log.i("SignalHistoryCapacity", "rows=$target query900Ms=$queryMs cutoffDeleteMs=$deleteMs")
            sql.query("PRAGMA wal_checkpoint(TRUNCATE)").use { }
            report("rows=$target checkpoint")
            sql.execSQL("VACUUM")
            report("rows=$target vacuum")
            if (target != 500_000) {
                // Keep the same database and continue to the next target; the later measurement is cumulative.
            }
        }
        db.close()
        context.deleteDatabase(name)
    }
}
