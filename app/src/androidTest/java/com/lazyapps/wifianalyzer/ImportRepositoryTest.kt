package com.lazyapps.wifianalyzer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lazyapps.wifianalyzer.data.registry.*
import com.lazyapps.wifianalyzer.importcsv.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImportRepositoryTest {
    private lateinit var db: WifiAnalyzerDatabase
    private lateinit var repo: ImportRepository
    @Before fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WifiAnalyzerDatabase::class.java).allowMainThreadQueries().build(); repo = ImportRepository(db)
        db.registryDao().insertWorkspace(WorkspaceEntity(1, "Main", "main", 0, 1, 1))
        Unit
    }
    @After fun close() = db.close()

    @Test fun addThenUpdateBySerialPreservesPhotoAndAppendsBssid() = runBlocking {
        val add = row(2, "Device A", "SER-1", listOf("02:00:00:00:00:01"))
        val addSettings = ImportSettings()
        val first = repo.plan(listOf(add), addSettings, 1); assertEquals(1, first.additions); repo.execute(first, addSettings)
        val device = db.registryDao().getAllDevices().single()
        db.registryDao().insertPhoto(DevicePhotoEntity(deviceId = device.id, workspaceId = 1, fileName = "photo.jpg", mimeType = "image/jpeg", width = 1, height = 1, fileSize = 1, sortOrder = 0, createdAt = 1, updatedAt = 1))
        val update = row(2, "Device A updated", "ser-1", listOf("02:00:00:00:00:02"))
        val settings = ImportSettings(mode = ImportMode.ADD_AND_UPDATE, matchKey = MatchKey.SERIAL)
        val plan = repo.plan(listOf(update), settings, 1); assertEquals(1, plan.updates); repo.execute(plan, settings)
        assertEquals("Device A updated", db.registryDao().getAllDevices().single().displayName)
        assertEquals(2, db.registryDao().getAllBssids().size)
        assertEquals(1, db.registryDao().getAllPhotos().size)
    }

    @Test fun workspaceAndGroupAreCreatedAndCrossWorkspaceBssidIsAllowed() = runBlocking {
        val first = repo.plan(listOf(row(2, "A", "1", listOf("02:00:00:00:00:03"), "Main", "Floor")), ImportSettings(), 1)
        repo.execute(first, ImportSettings())
        val second = repo.plan(listOf(row(2, "B", "2", listOf("02:00:00:00:00:03"), "Branch", "Floor")), ImportSettings(), 1)
        val result = repo.execute(second, ImportSettings())
        assertEquals(1, result.workspacesCreated); assertEquals(1, result.groupsCreated)
        assertEquals(2, db.registryDao().getAllBssids().size)
    }

    @Test fun sameWorkspaceBssidConflictAbortsWithoutPartialInsert() = runBlocking {
        val settings = ImportSettings(matchKey = MatchKey.SERIAL); repo.execute(repo.plan(listOf(row(2, "A", "1", listOf("02:00:00:00:00:04"))), settings, 1), settings)
        val conflict = repo.plan(listOf(row(2, "B", "2", listOf("02:00:00:00:00:04"))), settings, 1)
        assertEquals(1, conflict.conflicts)
        assertThrows(IllegalStateException::class.java) { runBlocking { repo.execute(conflict, settings) } }
        assertEquals(1, db.registryDao().getAllDevices().size)
    }

    private fun row(number: Int, name: String, serial: String, bssids: List<String>, workspace: String = "Main", group: String = "") =
        ImportedDeviceRow(number, workspace, group, name, "", "", serial, "", bssids.firstOrNull().orEmpty(), bssids, "", "", 1)
}
