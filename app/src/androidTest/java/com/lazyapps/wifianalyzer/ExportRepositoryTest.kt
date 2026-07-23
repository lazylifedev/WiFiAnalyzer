package com.lazyapps.wifianalyzer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lazyapps.wifianalyzer.data.DistanceUnitPreference
import com.lazyapps.wifianalyzer.data.registry.*
import com.lazyapps.wifianalyzer.export.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExportRepositoryTest {
    private lateinit var context: Context; private lateinit var db: WifiAnalyzerDatabase; private lateinit var repo: ExportRepository
    @Before fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext(); db = Room.inMemoryDatabaseBuilder(context, WifiAnalyzerDatabase::class.java).allowMainThreadQueries().build(); repo = ExportRepository(context, db)
        val dao = db.registryDao(); dao.insertWorkspace(WorkspaceEntity(1, "東京本社", "東京本社", 0, 1, 1)); dao.insertWorkspace(WorkspaceEntity(2, "大阪支社", "大阪支社", 1, 1, 1))
        val group = dao.insertGroup(WifiDeviceGroupEntity(name = "受付", normalizedName = "受付", sortOrder = 0, createdAt = 1, updatedAt = 1, workspaceId = 1))
        val first = dao.insertDevice(RegisteredWifiDeviceEntity(displayName = "端末A", primaryBssid = "02:00:00:00:00:01", ssid = "TEST_A", groupId = group, createdAt = 1, updatedAt = 2, workspaceId = 1))
        dao.insertBssids(listOf(WifiDeviceBssidEntity(deviceId = first, bssid = "02:00:00:00:00:01", band = "5 GHz", createdAt = 1, workspaceId = 1), WifiDeviceBssidEntity(deviceId = first, bssid = "02:00:00:00:00:02", band = "2.4 GHz", createdAt = 1, workspaceId = 1)))
        dao.insertPhoto(DevicePhotoEntity(deviceId = first, workspaceId = 1, fileName = "missing.jpg", mimeType = "image/jpeg", width = 100, height = 100, fileSize = 10, sortOrder = 0, isPrimary = true, createdAt = 1, updatedAt = 1))
        val second = dao.insertDevice(RegisteredWifiDeviceEntity(displayName = "端末B", primaryBssid = "02:00:00:00:00:03", createdAt = 1, updatedAt = 2, workspaceId = 2))
        dao.insertBssids(listOf(WifiDeviceBssidEntity(deviceId = second, bssid = "02:00:00:00:00:03", band = "6 GHz", createdAt = 1, workspaceId = 2)))
    }
    @After fun close() = db.close()

    @Test fun currentAndAllWorkspaceCountsAreSeparated() = runBlocking {
        val current = repo.dataset(ExportType.DEVICES, ExportTarget(ExportScope.CURRENT_WORKSPACE, 1), DistanceUnitPreference.METERS)
        assertEquals(1, current.rows.size); assertEquals(2, current.counts.bssids); assertEquals("東京本社", current.targetLabel)
        val all = repo.dataset(ExportType.DEVICES, ExportTarget(ExportScope.ALL_WORKSPACES, 1), DistanceUnitPreference.METERS)
        assertEquals(2, all.rows.size); assertEquals(3, all.counts.bssids); assertEquals("All", all.targetLabel)
    }
    @Test fun groupAndUngroupedFiltersApplyToEveryCsvType() = runBlocking {
        val groupId = db.registryDao().getGroupsOnce(1).single().id
        assertEquals(2, repo.dataset(ExportType.BSSIDS, ExportTarget(ExportScope.ALL_WORKSPACES, 1, groupId), DistanceUnitPreference.METERS).rows.size)
        assertEquals(1, repo.dataset(ExportType.BSSIDS, ExportTarget(ExportScope.ALL_WORKSPACES, 1, ungroupedOnly = true), DistanceUnitPreference.METERS).rows.size)
    }
    @Test fun photoCsvHasNoPathAndMissingReportPhotoDoesNotFail() = runBlocking {
        val photos = repo.dataset(ExportType.PHOTOS, ExportTarget(ExportScope.CURRENT_WORKSPACE, 1), DistanceUnitPreference.METERS)
        assertEquals(1, photos.rows.size); assertFalse(photos.rows.single().values.keys.any { it.contains("path", true) })
        val report = repo.reportDevices(ExportTarget(ExportScope.CURRENT_WORKSPACE, 1), DistanceUnitPreference.FEET, ReportPhotoMode.ALL).second.single()
        assertNull(report.photos.single().dataUri)
    }
}
