package com.lazyapps.wifianalyzer.ui.backup

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lazyapps.wifianalyzer.data.backup.*
import com.lazyapps.wifianalyzer.data.registry.WifiAnalyzerDatabase
import com.lazyapps.wifianalyzer.data.registry.WorkspaceRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class BackupUiState(val busy:Boolean=false, val stage:String="", val current:Int=0, val total:Int=0, val preview:BackupPreview?=null, val message:String?=null, val error:String?=null, val restoredWorkspaceId:Long?=null, val history:BackupHistory=BackupHistory())

class BackupViewModel(application: Application): AndroidViewModel(application) {
    private val db=WifiAnalyzerDatabase.get(application); private val exporter=BackupExportService(application,db); private val importer=BackupImportService(application); private val restorer=RestoreRepository(application,db); private val workspaces=WorkspaceRepository(application,db); private val histories=BackupHistoryRepository(application)
    private val _state=MutableStateFlow(BackupUiState()); val state:StateFlow<BackupUiState> = _state.asStateFlow(); private var job:Job?=null
    init { viewModelScope.launch { histories.history.collect { _state.value=_state.value.copy(history=it) } } }
    fun export(uri:Uri, workspaceId:Long?) = runTask { val manifest=exporter.export(workspaceId?.let { BackupScope.Workspace(it) } ?: BackupScope.All,uri){stage,current,total -> _state.value=_state.value.copy(stage=stage,current=current,total=total) }; histories.record(manifest,true); _state.value=_state.value.copy(message="バックアップを作成しました",preview=null,error=null) }
    fun inspect(uri:Uri)=runTask { _state.value=_state.value.copy(stage="整合性を確認中"); val preview=importer.inspect(uri); _state.value=_state.value.copy(preview=preview,message="整合性チェックに成功しました",error=null) }
    fun restore(mode:RestoreMode)=runTask { val preview=_state.value.preview ?: return@runTask; _state.value=_state.value.copy(stage="復元中"); val result=restorer.restore(preview,mode); if(mode==RestoreMode.REPLACE) result.workspaceIds.firstOrNull()?.let { workspaces.select(it) }; importer.discard(preview); _state.value=_state.value.copy(preview=null,message="復元しました",restoredWorkspaceId=result.workspaceIds.firstOrNull(),error=null) }
    fun cancel(){ job?.cancel(); _state.value.preview?.let(importer::discard); _state.value=_state.value.copy(busy=false,preview=null,message="処理をキャンセルしました") }
    fun clearMessage(){ _state.value=_state.value.copy(message=null,error=null) }
    private fun runTask(block:suspend()->Unit){ if(job?.isActive==true)return; job=viewModelScope.launch { _state.value=_state.value.copy(busy=true,error=null,message=null); try{block()}catch(e:Exception){_state.value=_state.value.copy(busy=false,error=e.message ?: "処理に失敗しました")}finally{_state.value=_state.value.copy(busy=false)} } }
}
