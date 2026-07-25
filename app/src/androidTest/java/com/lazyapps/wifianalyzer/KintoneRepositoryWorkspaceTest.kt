package com.lazyapps.wifianalyzer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lazyapps.wifianalyzer.data.registry.KintoneConnectionEntity
import com.lazyapps.wifianalyzer.data.registry.RegisteredWifiDeviceEntity
import com.lazyapps.wifianalyzer.data.registry.WifiAnalyzerDatabase
import com.lazyapps.wifianalyzer.data.registry.WorkspaceEntity
import com.lazyapps.wifianalyzer.kintone.EncryptedToken
import com.lazyapps.wifianalyzer.kintone.KINTONE_TEMPLATE_ID
import com.lazyapps.wifianalyzer.kintone.KintoneApi
import com.lazyapps.wifianalyzer.kintone.KintoneDeviceRecord
import com.lazyapps.wifianalyzer.kintone.KintoneRepository
import com.lazyapps.wifianalyzer.kintone.KintoneVerification
import com.lazyapps.wifianalyzer.kintone.KintoneAutoSyncState
import com.lazyapps.wifianalyzer.kintone.KintoneAutoSyncStore
import com.lazyapps.wifianalyzer.kintone.KintoneSyncStatus
import com.lazyapps.wifianalyzer.kintone.TokenCipher
import com.lazyapps.wifianalyzer.kintone.WorkspaceUuid
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KintoneRepositoryWorkspaceTest {
    private lateinit var db: WifiAnalyzerDatabase
    private lateinit var api: CountingApi
    private lateinit var repository: KintoneRepository

    @Before fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WifiAnalyzerDatabase::class.java).allowMainThreadQueries().build()
        val dao = db.registryDao()
        dao.insertWorkspace(WorkspaceEntity(1, "default", "default", 0, 1, 1))
        dao.insertWorkspace(WorkspaceEntity(2, "other", "other", 1, 1, 1))
        dao.upsertKintoneConnection(KintoneConnectionEntity(
            1, WorkspaceUuid.fromId(1), "example.cybozu.com", null, 287, "plugin", "1",
            KINTONE_TEMPLATE_ID, 1, 1, byteArrayOf(1), byteArrayOf(2), 1, 1, "CONNECTED",
        ))
        api = CountingApi()
        repository = KintoneRepository(db, api, PlainCipher)
    }

    @After fun close() = db.close()

    @Test fun otherWorkspaceDevicesAreNotSyncTargetsAndEmptySyncDoesNotCallHttp() = runBlocking {
        insertDevice(2, "別ワークスペース機器")
        val records = repository.buildSyncRecordsForConnection(1)
        assertEquals(0, records.size)
        val result = repository.sync(1, records)
        assertEquals(0, result.total)
        assertEquals(0, result.failed)
        assertEquals(0, api.upsertCalls)
        assertEquals("CONNECTED", db.registryDao().getKintoneConnection(1)?.lastVerificationStatus)
    }

    @Test fun connectedWorkspaceDeviceIsCountedByStoredWorkspaceUuid() = runBlocking {
        insertDevice(1, "接続先機器")
        insertDevice(2, "別ワークスペース機器")
        val records = repository.buildSyncRecordsForConnection(1)
        assertEquals(1, records.size)
        assertEquals(WorkspaceUuid.fromId(1), records.single().workspaceUuid)
        assertEquals("接続先機器", records.single().deviceName)
    }

    @Test fun oneHundredOneRecordsAreSplitAndResendingUsesTheSameUpdateKeys() = runBlocking {
        val records = (1..101).map { index ->
            KintoneDeviceRecord(
                deviceUuid = "device-$index", workspaceUuid = WorkspaceUuid.fromId(1), workspaceName = "default",
                groupUuid = "", groupName = "", deviceName = "device-$index", manufacturer = "", model = "",
                serialNumber = "", ssid = "", primaryBssid = "02:00:00:00:00:${index % 100}", location = "",
                notes = "", updatedAt = "2026-07-25T00:00:00Z",
            )
        }
        val first = repository.sync(1, records)
        assertEquals(101, first.succeeded)
        assertEquals(listOf(100, 1), api.batchSizes)
        val firstKeys = api.updateKeys.toList()

        api.batchSizes.clear()
        api.updateKeys.clear()
        val second = repository.sync(1, records)
        assertEquals(101, second.succeeded)
        assertEquals(listOf(100, 1), api.batchSizes)
        assertEquals(firstKeys, api.updateKeys)
    }

    @Test fun olderWorkerCannotOverwriteTheLatestRequestedState() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = KintoneAutoSyncStore(context)
        val uuid = "test-stale-worker-${System.nanoTime()}"
        try {
            store.write(uuid, KintoneAutoSyncState(lastRequestedAt = 2, status = KintoneSyncStatus.WAITING))
            val published = store.writeResultIfCurrent(
                uuid,
                requestVersion = 1,
                state = KintoneAutoSyncState(lastRequestedAt = 1, status = KintoneSyncStatus.FAILED),
            )
            assertEquals(false, published)
            assertEquals(2, store.read(uuid).lastRequestedAt)
            assertEquals(KintoneSyncStatus.WAITING, store.read(uuid).status)
        } finally {
            store.remove(uuid)
        }
    }

    private suspend fun insertDevice(workspaceId: Long, name: String) {
        db.registryDao().insertDevice(RegisteredWifiDeviceEntity(
            displayName = name, primaryBssid = "02:00:00:00:00:0$workspaceId",
            createdAt = 1, updatedAt = 1, workspaceId = workspaceId,
        ))
    }

    private class CountingApi : KintoneApi {
        var upsertCalls = 0
        val batchSizes = mutableListOf<Int>()
        val updateKeys = mutableListOf<String>()
        override suspend fun verify(domain: String, appId: Long, token: CharArray) = KintoneVerification(emptyMap())
        override suspend fun upsert(domain: String, appId: Long, token: CharArray, records: List<KintoneDeviceRecord>) {
            upsertCalls++
            batchSizes += records.size
            updateKeys += records.map { it.deviceUuid }
        }
    }

    private object PlainCipher : TokenCipher {
        override fun encrypt(workspaceUuid: String, token: CharArray) = EncryptedToken(byteArrayOf(1), byteArrayOf(2))
        override fun decrypt(workspaceUuid: String, encrypted: EncryptedToken) = "token".toCharArray()
    }
}
