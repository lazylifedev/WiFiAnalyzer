package com.lazyapps.wifianalyzer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lazyapps.wifianalyzer.data.registry.KintoneConnectionEntity
import com.lazyapps.wifianalyzer.data.registry.DevicePhotoEntity
import com.lazyapps.wifianalyzer.data.registry.RegisteredWifiDeviceEntity
import com.lazyapps.wifianalyzer.data.registry.WifiAnalyzerDatabase
import com.lazyapps.wifianalyzer.data.registry.WorkspaceEntity
import com.lazyapps.wifianalyzer.kintone.EncryptedToken
import com.lazyapps.wifianalyzer.kintone.KINTONE_TEMPLATE_ID
import com.lazyapps.wifianalyzer.kintone.KintoneApi
import com.lazyapps.wifianalyzer.kintone.KintoneDeviceRecord
import com.lazyapps.wifianalyzer.kintone.KintoneRepository
import com.lazyapps.wifianalyzer.kintone.KintoneVerification
import com.lazyapps.wifianalyzer.kintone.KintoneException
import com.lazyapps.wifianalyzer.kintone.KintoneErrorCode
import com.lazyapps.wifianalyzer.kintone.KintoneAutoSyncState
import com.lazyapps.wifianalyzer.kintone.KintoneAutoSyncStore
import com.lazyapps.wifianalyzer.kintone.KintoneSyncStatus
import com.lazyapps.wifianalyzer.kintone.TokenCipher
import com.lazyapps.wifianalyzer.kintone.WorkspaceUuid
import kotlinx.coroutines.runBlocking
import java.io.File
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
    private lateinit var context: Context

    @Before fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("kintone_photo_sync", Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, WifiAnalyzerDatabase::class.java).allowMainThreadQueries().build()
        val dao = db.registryDao()
        dao.insertWorkspace(WorkspaceEntity(1, "default", "default", 0, 1, 1))
        dao.insertWorkspace(WorkspaceEntity(2, "other", "other", 1, 1, 1))
        dao.upsertKintoneConnection(KintoneConnectionEntity(
            1, WorkspaceUuid.fromId(1), "example.cybozu.com", null, 287, "plugin", "1",
            KINTONE_TEMPLATE_ID, 1, 1, byteArrayOf(1), byteArrayOf(2), 1, 1, "CONNECTED",
        ))
        api = CountingApi()
        repository = KintoneRepository(db, api, PlainCipher, context)
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

    @Test fun changedPhotoUploadsOnceAndUnchangedSyncOmitsPhotoField() = runBlocking {
        val deviceId = insertDevice(1, "写真機器")
        insertPhoto(deviceId, byteArrayOf(0xff.toByte(), 0xd8.toByte(), 1, 0xff.toByte(), 0xd9.toByte()))
        val records = repository.buildSyncRecordsForConnection(1)
        val first = repository.sync(1, records)
        assertEquals(1, first.uploadedPhotoCount)
        assertEquals(listOf("uploaded-1"), api.lastRecords.single().photoFileKeys)
        val uploads = api.uploadCalls
        repository.sync(1, records)
        assertEquals(uploads, api.uploadCalls)
        assertEquals(null, api.lastRecords.single().photoFileKeys)
    }

    @Test fun removingPreviouslySyncedLastPhotoSendsEmptyArray() = runBlocking {
        val deviceId = insertDevice(1, "削除機器")
        val photoId = insertPhoto(deviceId, byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xd9.toByte()))
        val records = repository.buildSyncRecordsForConnection(1)
        repository.sync(1, records)
        val photo = db.registryDao().getPhoto(photoId)!!
        File(context.filesDir, "devices/1/$deviceId/photos/${photo.fileName}").delete()
        db.registryDao().deletePhoto(photoId)
        repository.sync(1, records)
        assertEquals(emptyList<String>(), api.lastRecords.single().photoFileKeys)
    }

    @Test fun failedUpsertDoesNotCommitFingerprintAndNextSyncReuploads() = runBlocking {
        val deviceId = insertDevice(1, "再送機器")
        insertPhoto(deviceId, byteArrayOf(0xff.toByte(), 0xd8.toByte(), 2, 0xff.toByte(), 0xd9.toByte()))
        val records = repository.buildSyncRecordsForConnection(1)
        api.failUpsert = true
        repository.sync(1, records)
        val firstUploads = api.uploadCalls
        api.failUpsert = false
        repository.sync(1, records)
        assertEquals(firstUploads + 1, api.uploadCalls)
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

    private suspend fun insertDevice(workspaceId: Long, name: String): Long {
        return db.registryDao().insertDevice(RegisteredWifiDeviceEntity(
            displayName = name, primaryBssid = "02:00:00:00:00:0$workspaceId",
            createdAt = 1, updatedAt = 1, workspaceId = workspaceId,
        ))
    }

    private suspend fun insertPhoto(deviceId: Long, bytes: ByteArray): Long {
        val directory = File(context.filesDir, "devices/1/$deviceId/photos").apply { mkdirs() }
        val file = File(directory, "photo-$deviceId-${System.nanoTime()}.jpg").apply { writeBytes(bytes) }
        return db.registryDao().insertPhoto(DevicePhotoEntity(deviceId = deviceId, workspaceId = 1, fileName = file.name, mimeType = "image/jpeg", width = 1, height = 1, fileSize = file.length(), sortOrder = 0, isPrimary = true, createdAt = 1, updatedAt = 1))
    }

    private class CountingApi : KintoneApi {
        var upsertCalls = 0
        val batchSizes = mutableListOf<Int>()
        val updateKeys = mutableListOf<String>()
        var uploadCalls = 0
        var failUpsert = false
        var lastRecords = emptyList<KintoneDeviceRecord>()
        override suspend fun verify(domain: String, appId: Long, token: CharArray) = KintoneVerification(emptyMap())
        override suspend fun upsert(domain: String, appId: Long, token: CharArray, records: List<KintoneDeviceRecord>) {
            if (failUpsert) throw KintoneException(KintoneErrorCode.KINTONE_BATCH_FAILED)
            upsertCalls++
            lastRecords = records
            batchSizes += records.size
            updateKeys += records.map { it.deviceUuid }
        }
        override suspend fun uploadFile(domain: String, token: CharArray, file: File, fileName: String): String {
            uploadCalls++
            return "uploaded-$uploadCalls"
        }
    }

    private object PlainCipher : TokenCipher {
        override fun encrypt(workspaceUuid: String, token: CharArray) = EncryptedToken(byteArrayOf(1), byteArrayOf(2))
        override fun decrypt(workspaceUuid: String, encrypted: EncryptedToken) = "token".toCharArray()
    }
}
