package com.lazyapps.wifianalyzer

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lazyapps.wifianalyzer.data.registry.DeviceRegistryRepository
import com.lazyapps.wifianalyzer.data.registry.WifiAnalyzerDatabase
import com.lazyapps.wifianalyzer.data.registry.WorkspaceRepository
import com.lazyapps.wifianalyzer.domain.DeviceBssidInput
import com.lazyapps.wifianalyzer.domain.DeviceInput
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceRepositoryTest {
    @Test fun createRenameSelectSeparateAndDeleteLastRecoversDefault() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, WifiAnalyzerDatabase::class.java).build()
        val workspaces = WorkspaceRepository(context, db); val first = workspaces.ensureUsable()
        assertEquals("default", workspaces.snapshot.first { it.selected != null }.selected?.name)
        workspaces.rename(first, " Main "); assertEquals("Main", workspaces.snapshot.first { it.selected?.name == "Main" }.selected?.name)
        val second = workspaces.create("default"); workspaces.select(second)
        val devices = DeviceRegistryRepository(context, db, workspaces)
        devices.save(DeviceInput(displayName = "Second", bssids = listOf(DeviceBssidInput("AA:BB:CC:DD:EE:90", "5 GHz"))))
        assertEquals(1, devices.snapshot.first { it.devices.isNotEmpty() }.devices.size)
        workspaces.select(first); assertTrue(devices.snapshot.first { it.devices.isEmpty() }.devices.isEmpty())
        devices.save(DeviceInput(displayName = "First", bssids = listOf(DeviceBssidInput("AA:BB:CC:DD:EE:90", "5 GHz"))))
        assertEquals(1, devices.snapshot.first { it.devices.singleOrNull()?.displayName == "First" }.devices.size)
        workspaces.delete(second); workspaces.delete(first)
        val recovered = workspaces.snapshot.first { it.workspaces.size == 1 && it.selected != null }
        assertEquals("default", recovered.selected?.name); assertNotEquals(first, recovered.selectedId)
        db.close(); Unit
    }
}
