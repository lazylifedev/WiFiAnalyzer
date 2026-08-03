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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lazyapps.wifianalyzer.R
import com.lazyapps.wifianalyzer.data.backup.RestoreMode
import com.lazyapps.wifianalyzer.ui.operation.OperationState
import com.lazyapps.wifianalyzer.ui.theme.AppSpacing
import com.lazyapps.wifianalyzer.ui.workspace.WorkspaceUiState
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun BackupScreen(workspaceState:WorkspaceUiState,onBack:()->Unit,onOpenWorkspace:(Long)->Unit,onOperationSuccess:()->Unit={},vm:BackupViewModel=viewModel()){
    val state by vm.state.collectAsStateWithLifecycle();var workspaceExport by remember{mutableStateOf(false)};var confirmReplace by remember{mutableStateOf(false)};var lastSuccessId by remember{mutableStateOf<Long?>(null)}
    LaunchedEffect(state.operation){(state.operation as? OperationState.Success)?.let{if(lastSuccessId!=it.eventId){lastSuccessId=it.eventId;onOperationSuccess()}}}
    val create=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")){uri->uri?.let{vm.export(it,if(workspaceExport)workspaceState.selected?.id else null)}}
    val open=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->uri?.let(vm::inspect)}
    Scaffold(topBar={TopAppBar(title={Text(stringResource(R.string.backup_and_restore))},navigationIcon={IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Rounded.ArrowBack,stringResource(R.string.back))}})}){padding->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(AppSpacing.large),verticalArrangement=Arrangement.spacedBy(AppSpacing.medium)){
            Text(stringResource(R.string.backup_excluded_settings),style=MaterialTheme.typography.bodySmall);Text(stringResource(R.string.backup_security_warning),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
            Text(if(state.history.timestamp>0)stringResource(R.string.last_backup_status,date(state.history.timestamp),stringResource(if(state.history.succeeded)R.string.success else R.string.failure))else stringResource(R.string.last_backup_none),style=MaterialTheme.typography.bodySmall)
            Card(Modifier.fillMaxWidth()){Column(Modifier.padding(AppSpacing.large),verticalArrangement=Arrangement.spacedBy(AppSpacing.small)){Text(stringResource(R.string.all_data),style=MaterialTheme.typography.titleMedium);Text(stringResource(R.string.workspace_count,workspaceState.workspaces.size));Button(onClick={workspaceExport=false;create.launch(fileName("Backup"))},modifier=Modifier.testTag("backup_all"),enabled=!state.busy){Text(stringResource(R.string.backup_all_action))}}}
            Card(Modifier.fillMaxWidth()){Column(Modifier.padding(AppSpacing.large),verticalArrangement=Arrangement.spacedBy(AppSpacing.small)){Text(stringResource(R.string.selected_workspace),style=MaterialTheme.typography.titleMedium);Text(workspaceState.selected?.name?:stringResource(R.string.none));OutlinedButton(onClick={workspaceExport=true;create.launch(fileName(workspaceState.selected?.name?:"Workspace"))},modifier=Modifier.testTag("backup_workspace"),enabled=!state.busy&&workspaceState.selected!=null){Text(stringResource(R.string.backup_workspace_action))}}}
            OutlinedButton(onClick={open.launch(arrayOf("application/zip","application/octet-stream"))},modifier=Modifier.testTag("restore_open"),enabled=!state.busy){Text(stringResource(R.string.restore_from_backup))}
            if(state.busy)Card(Modifier.fillMaxWidth().testTag("backup_progress")){Column(Modifier.padding(AppSpacing.large),verticalArrangement=Arrangement.spacedBy(AppSpacing.small)){LinearProgressIndicator(Modifier.fillMaxWidth());val running=state.operation as? OperationState.Running;Text(running?.messageRes?.let{stringResource(it)}?:stringResource(R.string.processing));if(state.total>0)Text(stringResource(R.string.photos_progress,state.current,state.total));if(running?.cancellable==true)TextButton(vm::cancel){Text(stringResource(R.string.cancel))}}}
            state.preview?.let{p->Card(Modifier.fillMaxWidth().testTag("restore_preview")){Column(Modifier.padding(AppSpacing.large),verticalArrangement=Arrangement.spacedBy(AppSpacing.small)){Text(stringResource(R.string.restore_preview),style=MaterialTheme.typography.titleMedium);Text(stringResource(R.string.created_at_value,date(p.manifest.createdAt)));Text(stringResource(R.string.backup_app_format,p.manifest.appVersionName,p.manifest.formatVersion));Text(stringResource(R.string.backup_entity_counts,p.manifest.workspaceCount,p.manifest.deviceCount,p.manifest.groupCount));Text(stringResource(R.string.backup_asset_counts,p.manifest.bssidCount,p.manifest.photoCount,p.manifest.totalPhotoBytes));Text(stringResource(R.string.integrity_check_success),color=MaterialTheme.colorScheme.primary);Button(onClick={vm.restore(RestoreMode.ADD)},modifier=Modifier.testTag("restore_add"),enabled=!state.busy){Text(stringResource(R.string.restore_add))};TextButton(onClick={confirmReplace=true},modifier=Modifier.testTag("restore_replace"),enabled=!state.busy){Text(stringResource(R.string.restore_replace),color=MaterialTheme.colorScheme.error)}}}}
            state.error?.let{Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("backup_error"))};state.message?.let{Text(it,color=MaterialTheme.colorScheme.primary)};state.restoredWorkspaceId?.let{id->OutlinedButton({onOpenWorkspace(id)}){Text(stringResource(R.string.open_restored_workspace))}}
        }
    }
    if(confirmReplace)AlertDialog({confirmReplace=false},title={Text(stringResource(R.string.confirm_replace_title))},text={Text(stringResource(R.string.confirm_replace_body))},confirmButton={Button({confirmReplace=false;vm.restore(RestoreMode.REPLACE)},colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error)){Text(stringResource(R.string.confirm_replace_action))}},dismissButton={TextButton({confirmReplace=false}){Text(stringResource(R.string.cancel))}})
}
private fun fileName(label:String)="WiFiAnalyzer_${label.replace(Regex("[^A-Za-z0-9ぁ-んァ-ヶ一-龠_-]"),"_")}_${SimpleDateFormat("yyyy-MM-dd_HHmm",Locale.US).format(Date())}.zip"
private fun date(value:Long)=DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT,Locale.getDefault()).format(Date(value))
