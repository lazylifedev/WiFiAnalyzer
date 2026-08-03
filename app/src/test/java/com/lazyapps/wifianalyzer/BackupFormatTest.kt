package com.lazyapps.wifianalyzer

import com.lazyapps.wifianalyzer.data.backup.*
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class BackupFormatTest {
    @Test fun `manifest and dto round trip with checksums`() {
        val root=createTempDir(); val zip=File(root,"backup.zip"); val data=sampleData()
        val manifest=zip.outputStream().use { out -> BackupArchiveWriter().write(out,data,{checksums->manifest(checksums)}, { error("no photos") }) }
        assertEquals(BACKUP_FORMAT_VERSION,manifest.formatVersion)
        val preview=BackupArchiveReader().read(zip,File(root,"out"))
        assertEquals(data,preview.data); assertEquals(5,preview.manifest.checksums.size)
        root.deleteRecursively()
    }
    @Test fun `zip slip absolute and duplicate paths are rejected`() {
        listOf("../bad","/absolute","C:/absolute").forEach { path -> assertFails(BackupException.Code.UNSAFE_PATH){ BackupSecurity.validatePath(path) } }
        val root=createTempDir();val zip=File(root,"bad.zip")
        ZipOutputStream(zip.outputStream()).use { z-> listOf("same.json","SAME.json").forEach{z.putNextEntry(ZipEntry(it));z.write("{}".toByteArray());z.closeEntry()} }
        assertFails(BackupException.Code.UNSAFE_PATH){BackupArchiveReader().read(zip,File(root,"out"))};root.deleteRecursively()
    }
    @Test fun `newer format is rejected`() {
        val root=createTempDir();val zip=File(root,"new.zip");val data=sampleData()
        zip.outputStream().use { out->BackupArchiveWriter().write(out,data,{checksums->manifest(checksums).copy(formatVersion=99)},{error("no photos")}) }
        assertFails(BackupException.Code.UNSUPPORTED_FORMAT){BackupArchiveReader().read(zip,File(root,"out"))};root.deleteRecursively()
    }
    @Test fun `workspace names are made unique with language independent suffixes`() { val used=mutableSetOf("東京");val first=uniqueRestoredName("東京",used);used+=first;assertEquals("東京 (2)",first);assertEquals("東京 (3)",uniqueRestoredName("東京",used)) }
    @Test fun `same workspace duplicate bssid rejected but cross workspace allowed`() {
        val base=sampleData();val one=base.bssids.first();val duplicate=base.copy(bssids=listOf(one,one.copy(backupId="b2")))
        assertFails(BackupException.Code.DUPLICATE_BSSID){BackupValidator.validate(manifest(emptyList()).copy(bssidCount=2),duplicate,createTempDir())}
        val otherWs=base.workspaces.first().copy(backupId="w2",name="大阪");val otherDevice=base.devices.first().copy(backupId="d2",workspaceBackupId="w2");val cross=base.copy(workspaces=base.workspaces+otherWs,devices=base.devices+otherDevice,bssids=listOf(one,one.copy(backupId="b2",deviceBackupId="d2",workspaceBackupId="w2")))
        BackupValidator.validate(manifest(emptyList()).copy(workspaceCount=2,deviceCount=2,bssidCount=2),cross,createTempDir())
    }
    @Test fun `sha256 known value`() { assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",BackupSecurity.sha256("abc".toByteArray())) }
    private fun sampleData():BackupData { val ws=BackupWorkspace("w1","東京",0,1,2);val d=BackupDevice("d1","w1","AP","Maker","Model","TEST-SERIAL","02:00:00:00:00:01","TEST",null,"","",1,2,3,-50,true);val b=BackupBssid("b1","d1","w1","02:00:00:00:00:01","5","",1);return BackupData(listOf(ws),emptyList(),listOf(d),listOf(b),emptyList()) }
    private fun manifest(checksums:List<BackupChecksum>)=BackupManifest(appPackage="test",appVersionName="1",appVersionCode=1,createdAt=1,backupType=BackupType.all,workspaceCount=1,deviceCount=1,groupCount=0,bssidCount=1,photoCount=0,totalPhotoBytes=0,databaseSchemaVersion=2,deviceManufacturer="test",androidVersion="test",checksums=checksums.ifEmpty { listOf("workspaces","groups","devices","bssids","photos").map { BackupChecksum("data/$it.json",0,"x") } })
    private fun assertFails(code:BackupException.Code,block:()->Unit){try{block();fail("expected $code")}catch(e:BackupException){assertEquals(code,e.code)}}
}
