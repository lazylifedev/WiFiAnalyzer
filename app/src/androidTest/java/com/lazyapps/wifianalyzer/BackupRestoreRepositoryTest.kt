package com.lazyapps.wifianalyzer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lazyapps.wifianalyzer.data.backup.*
import com.lazyapps.wifianalyzer.data.registry.*
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRestoreRepositoryTest {
    private lateinit var context:Context;private lateinit var db:WifiAnalyzerDatabase
    @Before fun setup(){context=ApplicationProvider.getApplicationContext();db=Room.inMemoryDatabaseBuilder(context,WifiAnalyzerDatabase::class.java).build()}
    @After fun close(){db.close()}
    @Test fun additiveAndReplaceRestorePreserveRelations()=runBlocking{
        val dao=db.registryDao();val now=1L;dao.insertWorkspace(WorkspaceEntity(name="既存",normalizedName="既存",sortOrder=0,createdAt=now,updatedAt=now))
        val preview=preview();val add=RestoreRepository(context,db).restore(preview,RestoreMode.ADD);assertEquals(2,dao.getWorkspacesOnce().size);val device=dao.getAllDevices().single();assertEquals(add.workspaceIds.single(),device.workspaceId);assertEquals("02:00:00:00:00:01",dao.getAllBssids().single().bssid)
        RestoreRepository(context,db).restore(preview(),RestoreMode.REPLACE);assertEquals(1,dao.getWorkspacesOnce().size);assertEquals("復元",dao.getWorkspacesOnce().single().name);assertEquals(-42,dao.getAllDevices().single().lastSeenRssi)
    }
    @Test fun invalidBackupDoesNotChangeExistingData()=runBlocking{
        val dao=db.registryDao();dao.insertWorkspace(WorkspaceEntity(name="既存",normalizedName="既存",sortOrder=0,createdAt=1,updatedAt=1));val bad=preview().copy(data=preview().data.copy(bssids=listOf()))
        try{RestoreRepository(context,db).restore(bad,RestoreMode.REPLACE);fail()}catch(_:BackupException){};assertEquals("既存",dao.getWorkspacesOnce().single().name)
    }
    private fun preview():BackupPreview { val root=File(context.cacheDir,"test-${System.nanoTime()}").apply{mkdirs()};val ws=BackupWorkspace("w","復元",0,1,2);val group=BackupGroup("g","w","設備",0,1,2);val device=BackupDevice("d","w","テスト機器","TEST","T1","TEST-001","02:00:00:00:00:01","TEST","g","設置場所","メモ",1,2,3,-42,true);val bssid=BackupBssid("b","d","w","02:00:00:00:00:01","5 GHz","main",1);val data=BackupData(listOf(ws),listOf(group),listOf(device),listOf(bssid),emptyList());val manifest=BackupManifest(appPackage="test",appVersionName="1",appVersionCode=1,createdAt=1,backupType=BackupType.all,workspaceCount=1,deviceCount=1,groupCount=1,bssidCount=1,photoCount=0,totalPhotoBytes=0,databaseSchemaVersion=2,deviceManufacturer="test",androidVersion="11",checksums=listOf("workspaces","groups","devices","bssids","photos").map{BackupChecksum("data/$it.json",0,"x")});return BackupPreview(manifest,data,root) }
}
