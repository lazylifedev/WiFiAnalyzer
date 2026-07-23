package com.lazyapps.wifianalyzer.data.backup

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.backupHistoryStore by preferencesDataStore("backup_history")
data class BackupHistory(val timestamp:Long=0, val type:String="", val itemCount:Int=0, val succeeded:Boolean=true)
class BackupHistoryRepository(private val context:Context){
    val history:Flow<BackupHistory> = context.backupHistoryStore.data.map { BackupHistory(it[time]?:0,it[type].orEmpty(),it[count]?:0,it[success]?:true) }
    suspend fun record(manifest:BackupManifest,succeeded:Boolean)=context.backupHistoryStore.edit { it[time]=System.currentTimeMillis();it[type]=manifest.backupType.name;it[count]=manifest.workspaceCount+manifest.deviceCount+manifest.photoCount;it[success]=succeeded }
    private companion object { val time=longPreferencesKey("last_time");val type=stringPreferencesKey("last_type");val count=intPreferencesKey("last_count");val success=booleanPreferencesKey("last_success") }
}
