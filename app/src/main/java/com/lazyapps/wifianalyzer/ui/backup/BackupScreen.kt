package com.lazyapps.wifianalyzer.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lazyapps.wifianalyzer.data.backup.RestoreMode
import com.lazyapps.wifianalyzer.ui.theme.AppSpacing
import com.lazyapps.wifianalyzer.ui.workspace.WorkspaceUiState
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun BackupScreen(workspaceState:WorkspaceUiState,onBack:()->Unit,onOpenWorkspace:(Long)->Unit,onOperationSuccess:()->Unit={},vm:BackupViewModel=viewModel()){
    val state by vm.state.collectAsStateWithLifecycle(); var workspaceExport by remember { mutableStateOf(false) }; var confirmReplace by remember { mutableStateOf(false) }
    var lastSuccessId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(state.operation) { (state.operation as? com.lazyapps.wifianalyzer.ui.operation.OperationState.Success)?.let { if (lastSuccessId != it.eventId) { lastSuccessId = it.eventId; onOperationSuccess() } } }
    val create=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")){uri->uri?.let{vm.export(it,if(workspaceExport)workspaceState.selected?.id else null)}}
    val open=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->uri?.let(vm::inspect)}
    Scaffold(topBar={TopAppBar(title={Text("バックアップと復元")},navigationIcon={IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Rounded.ArrowBack,"戻る")}})}){padding->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(AppSpacing.large),verticalArrangement=Arrangement.spacedBy(AppSpacing.medium)){
            Text("テーマ、距離単位、表示帯域、スキャン要求間隔は管理データのバックアップ対象外です。",style=MaterialTheme.typography.bodySmall)
            Text("暗号化されません。機器写真にはパスワード等が写っている可能性があります。安全な場所に保管してください。",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
            Text(if(state.history.timestamp>0) "最終バックアップ: ${date(state.history.timestamp)} / ${if(state.history.succeeded) "成功" else "失敗"}" else "最終バックアップ: なし",style=MaterialTheme.typography.bodySmall)
            Card(Modifier.fillMaxWidth()){Column(Modifier.padding(AppSpacing.large),verticalArrangement=Arrangement.spacedBy(AppSpacing.small)){Text("全データ",style=MaterialTheme.typography.titleMedium);Text("ワークスペース ${workspaceState.workspaces.size}");Button(enabled=!state.busy,onClick={workspaceExport=false;create.launch(fileName("Backup"))},modifier=Modifier.testTag("backup_all")){Text("全データをバックアップ")}}}
            Card(Modifier.fillMaxWidth()){Column(Modifier.padding(AppSpacing.large),verticalArrangement=Arrangement.spacedBy(AppSpacing.small)){Text("選択中のワークスペース",style=MaterialTheme.typography.titleMedium);Text(workspaceState.selected?.name ?: "なし");OutlinedButton(enabled=!state.busy&&workspaceState.selected!=null,onClick={workspaceExport=true;create.launch(fileName(workspaceState.selected?.name ?: "Workspace"))},modifier=Modifier.testTag("backup_workspace")){Text("ワークスペースをバックアップ")}}}
            OutlinedButton(enabled=!state.busy,onClick={open.launch(arrayOf("application/zip","application/octet-stream"))},modifier=Modifier.testTag("restore_open")){Text("バックアップから復元")}
            if(state.busy){Card(Modifier.fillMaxWidth().testTag("backup_progress")){Column(Modifier.padding(AppSpacing.large),verticalArrangement=Arrangement.spacedBy(AppSpacing.small)){LinearProgressIndicator(Modifier.fillMaxWidth());Text(state.stage.ifBlank{"処理中"});if(state.total>0)Text("写真 ${state.current} / ${state.total}");TextButton(onClick=vm::cancel){Text("キャンセル")}}}}
            state.preview?.let{preview->Card(Modifier.fillMaxWidth().testTag("restore_preview")){Column(Modifier.padding(AppSpacing.large),verticalArrangement=Arrangement.spacedBy(AppSpacing.small)){Text("復元前プレビュー",style=MaterialTheme.typography.titleMedium);Text("作成日時: ${date(preview.manifest.createdAt)}");Text("アプリ: ${preview.manifest.appVersionName} / 形式: ${preview.manifest.formatVersion}");Text("ワークスペース ${preview.manifest.workspaceCount}、機器 ${preview.manifest.deviceCount}、グループ ${preview.manifest.groupCount}");Text("BSSID ${preview.manifest.bssidCount}、写真 ${preview.manifest.photoCount} (${preview.manifest.totalPhotoBytes} bytes)");Text("整合性チェック: 成功",color=MaterialTheme.colorScheme.primary);Button(enabled=!state.busy,onClick={vm.restore(RestoreMode.ADD)},modifier=Modifier.testTag("restore_add")){Text("追加して復元")};TextButton(enabled=!state.busy,onClick={confirmReplace=true},modifier=Modifier.testTag("restore_replace")){Text("すべて置き換えて復元",color=MaterialTheme.colorScheme.error)}}}}
            state.error?.let{Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("backup_error"))};state.message?.let{Text(it,color=MaterialTheme.colorScheme.primary)}
            state.restoredWorkspaceId?.let{id->OutlinedButton(onClick={onOpenWorkspace(id)}){Text("復元したワークスペースを開く")}}
        }
    }
    if(confirmReplace)AlertDialog(onDismissRequest={confirmReplace=false},title={Text("すべて置き換えますか？")},text={Text("現在の管理データと写真をバックアップ内容で置き換えます。この操作は元に戻せません。")},confirmButton={Button(onClick={confirmReplace=false;vm.restore(RestoreMode.REPLACE)},colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error)){Text("置き換えて復元")}},dismissButton={TextButton(onClick={confirmReplace=false}){Text("キャンセル")}})
}
private fun fileName(label:String)= "WiFiAnalyzer_${label.replace(Regex("[^A-Za-z0-9ぁ-んァ-ヶ一-龠_-]"),"_")}_${SimpleDateFormat("yyyy-MM-dd_HHmm",Locale.US).format(Date())}.zip"
private fun date(value:Long)=SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.getDefault()).format(Date(value))
