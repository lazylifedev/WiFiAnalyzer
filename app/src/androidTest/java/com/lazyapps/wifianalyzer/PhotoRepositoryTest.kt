package com.lazyapps.wifianalyzer

import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lazyapps.wifianalyzer.data.photos.PhotoRepository
import com.lazyapps.wifianalyzer.data.registry.DeviceRegistryRepository
import com.lazyapps.wifianalyzer.data.registry.WifiAnalyzerDatabase
import com.lazyapps.wifianalyzer.data.registry.WorkspaceRepository
import com.lazyapps.wifianalyzer.domain.DeviceBssidInput
import com.lazyapps.wifianalyzer.domain.DeviceInput
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PhotoRepositoryTest {
    @Test fun saveLimitPrimaryReorderAndDeleteKeepFilesConsistent() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, WifiAnalyzerDatabase::class.java).build()
        val workspaces = WorkspaceRepository(context, db); val workspaceId = workspaces.ensureUsable()
        val devices = DeviceRegistryRepository(context, db, workspaces)
        val deviceId = devices.save(DeviceInput(displayName = "Photo AP", bssids = listOf(DeviceBssidInput("AA:BB:CC:00:00:01", "5 GHz"))))
        val repository = PhotoRepository(context, db)
        val sources = (0 until 10).map { index -> File(context.cacheDir, "photo-test-$index.png").also { file -> Bitmap.createBitmap(2400, 1200, Bitmap.Config.ARGB_8888).also { bitmap -> file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle() } } }
        sources.take(9).forEach { repository.save(deviceId, workspaceId, Uri.fromFile(it)) }
        val photos = repository.observe(deviceId).first { it.size == 9 }
        assertTrue(photos.first().isPrimary); assertTrue(photos.all { maxOf(it.width, it.height) <= 2048 }); assertEquals(9, photos.map { it.fileName }.distinct().size)
        assertTrue(runCatching { repository.save(deviceId, workspaceId, Uri.fromFile(sources.last())) }.isFailure)
        repository.setPrimary(photos[2].id); assertTrue(repository.observe(deviceId).first { list -> list.any { it.id == photos[2].id && it.isPrimary } }.single { it.isPrimary }.id == photos[2].id)
        val deletedFile = repository.file(photos.first()); repository.delete(photos.first().id); assertFalse(deletedFile.exists()); assertEquals(8, repository.observe(deviceId).first { it.size == 8 }.size)
        sources.forEach(File::delete); db.close(); Unit
    }
}
