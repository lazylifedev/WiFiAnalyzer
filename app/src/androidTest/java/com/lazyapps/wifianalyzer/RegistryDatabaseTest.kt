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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RegistryDatabaseTest {
    private lateinit var database: WifiAnalyzerDatabase
    private lateinit var repository: DeviceRegistryRepository
    private lateinit var workspaceRepository: WorkspaceRepository

    @Before fun createDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(
            context,
            WifiAnalyzerDatabase::class.java,
        ).build()
        workspaceRepository = WorkspaceRepository(context, database)
        runBlocking { workspaceRepository.ensureUsable() }
        repository = DeviceRegistryRepository(context, database, workspaceRepository)
    }

    @After fun closeDatabase() = database.close()

    @Test fun deviceAndMultipleBssidsArePersistedAndEditable() = runBlocking {
        val id = repository.save(input("AP-1", "AA:BB:CC:DD:EE:01", "AA:BB:CC:DD:EE:02"))
        assertEquals(2, database.registryDao().getBssids(id).size)
        repository.save(input("AP-1 edited", "AA:BB:CC:DD:EE:01").copy(id = id, model = "X1"))
        val edited = repository.snapshot.first { it.devices.isNotEmpty() }.devices.single()
        assertEquals("AP-1 edited", edited.displayName)
        assertEquals("X1", edited.model)
        assertEquals(1, edited.bssids.size)
    }

    @Test fun deletingDeviceCascadesBssids() = runBlocking {
        val id = repository.save(input("AP-1", "AA:BB:CC:DD:EE:03", "AA:BB:CC:DD:EE:04"))
        repository.deleteDevice(id)
        assertNull(database.registryDao().getDevice(id))
        assertEquals(0, database.registryDao().countBssids())
    }

    @Test fun deletingGroupMovesDeviceToUncategorized() = runBlocking {
        val groupId = repository.createGroup("本社")
        val id = repository.save(input("AP-1", "AA:BB:CC:DD:EE:05").copy(groupId = groupId))
        val group = repository.snapshot.first { it.groups.isNotEmpty() }.groups.single()
        repository.deleteGroup(group)
        assertNull(database.registryDao().getDevice(id)?.groupId)
    }

    @Test fun normalizedBssidIsUniqueAcrossDevices() = runBlocking {
        repository.save(input("AP-1", "aa-bb-cc-dd-ee-06"))
        val error = runCatching { repository.save(input("AP-2", "AABBCCDDEE06")) }.exceptionOrNull()
        assertTrue(error?.message?.contains("別の機器") == true)
    }

    @Test fun normalizedGroupNameIsUnique() = runBlocking {
        repository.createGroup("Ｏｆｆｉｃｅ")
        val error = runCatching { repository.createGroup(" office ") }.exceptionOrNull()
        assertTrue(error?.message?.contains("同名") == true)
    }

    @Test fun groupNameValidationAndWorkspaceIsolation() = runBlocking {
        val firstWorkspace = workspaceRepository.snapshot.first().selectedId
        repository.createGroup(firstWorkspace, "Ｏｆｆｉｃｅ")
        assertTrue(runCatching { repository.createGroup(firstWorkspace, " office ") }.exceptionOrNull()?.message?.contains("同名") == true)
        assertTrue(runCatching { repository.createGroup(firstWorkspace, " ") }.exceptionOrNull()?.message?.contains("入力") == true)
        assertTrue(runCatching { repository.createGroup(firstWorkspace, "a".repeat(51)) }.exceptionOrNull()?.message?.contains("50") == true)

        val secondWorkspace = workspaceRepository.create("別ワークスペース")
        repository.createGroup(secondWorkspace, "office")
        workspaceRepository.delete(firstWorkspace)
        assertTrue(runCatching { repository.createGroup(firstWorkspace, "削除後") }.exceptionOrNull()?.message?.contains("削除") == true)
    }

    private fun input(name: String, vararg bssids: String) = DeviceInput(
        displayName = name,
        bssids = bssids.mapIndexed { index, value -> DeviceBssidInput(value, if (index == 0) "2.4 GHz" else "5 GHz") },
    )
}
