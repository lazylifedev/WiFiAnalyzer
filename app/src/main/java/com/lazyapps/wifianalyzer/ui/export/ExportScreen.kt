package com.lazyapps.wifianalyzer.ui.export

import android.content.Intent
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lazyapps.wifianalyzer.R
import com.lazyapps.wifianalyzer.export.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ExportScreen(onBack: () -> Unit, onOperationSuccess: () -> Unit = {}, viewModel: ExportViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle(); val context = LocalContext.current; val scope = rememberCoroutineScope()
    var showColumns by remember { mutableStateOf(false) }; var warning by remember { mutableStateOf(false) }; var showReport by remember { mutableStateOf(false) }
    var shareError by remember { mutableStateOf(false) }; val csvChooser = stringResource(R.string.share_csv_title); val reportChooser = stringResource(R.string.share_report_title)
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let { uri -> scope.launch { viewModel.writeCsv(uri).onSuccess { onOperationSuccess() } } } }
    fun share(fileType: String, chooser: String, producer: suspend () -> Result<java.io.File>) { scope.launch { producer().onSuccess { file -> val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file); val intent = Intent(Intent.ACTION_SEND).setType(fileType).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); runCatching { context.startActivity(Intent.createChooser(intent, chooser)); viewModel.deleteAfterSharing(file); onOperationSuccess() }.onFailure { file.delete(); shareError = true } }.onFailure { shareError = true } } }
    LaunchedEffect(state.reportHtml) { if (state.reportHtml != null) onOperationSuccess() }
    Scaffold(topBar={TopAppBar(title={Text(stringResource(R.string.export_data))},navigationIcon={IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Rounded.ArrowBack,stringResource(R.string.back))}})}) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("export_screen"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item { Text(stringResource(R.string.export_kind),style=MaterialTheme.typography.titleMedium); SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()){ExportType.entries.forEachIndexed{i,type->SegmentedButton(state.type==type,{viewModel.setType(type)},SegmentedButtonDefaults.itemShape(i,ExportType.entries.size),label={Text(exportTypeLabel(type),maxLines=2)})}} }
            item { Text(stringResource(R.string.export_target),style=MaterialTheme.typography.titleMedium); Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(state.scope==ExportScope.CURRENT_WORKSPACE,{viewModel.setScope(ExportScope.CURRENT_WORKSPACE)},{Text(stringResource(R.string.export_current))});FilterChip(state.scope==ExportScope.ALL_WORKSPACES,{viewModel.setScope(ExportScope.ALL_WORKSPACES)},{Text(stringResource(R.string.export_all))})};Text(if(state.scope==ExportScope.ALL_WORKSPACES)stringResource(R.string.export_all_workspaces) else stringResource(R.string.export_workspace_format,state.workspaceName)) }
            item { Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(state.groupId==null&&!state.ungroupedOnly,{viewModel.setGroup(null)},{Text(stringResource(R.string.export_all_groups))});FilterChip(state.ungroupedOnly,{viewModel.setGroup(null,true)},{Text(stringResource(R.string.export_uncategorized))});state.groups.forEach{(id,name)->FilterChip(state.groupId==id,{viewModel.setGroup(id)},{Text(name)})}} }
            state.dataset?.let{d->item{Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Text(stringResource(R.string.export_counts,d.counts.devices,d.counts.bssids,d.counts.photos));Text(stringResource(R.string.export_row_count,d.rows.size))}}}}
            if(state.busy)item{Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){CircularProgressIndicator(Modifier.size(28.dp));Text(stringResource(R.string.processing));if(state.type==ExportType.REPORT)TextButton(onClick=viewModel::cancel){Text(stringResource(R.string.cancel))}}}
            if(state.type!=ExportType.REPORT){
                item{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(stringResource(R.string.column_settings),Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);TextButton(onClick={showColumns=true},modifier=Modifier.testTag("column_settings")){Icon(Icons.Rounded.ViewColumn,null);Text(stringResource(R.string.column_count,viewModel.activeColumns().size))}}}
                item{Text(stringResource(R.string.csv_preview),style=MaterialTheme.typography.titleMedium);Surface(Modifier.fillMaxWidth().heightIn(max=300.dp).horizontalScroll(rememberScrollState()),tonalElevation=1.dp){Text(state.preview.ifBlank{stringResource(R.string.no_data)},Modifier.padding(12.dp),style=MaterialTheme.typography.bodySmall)};Text(stringResource(R.string.csv_format_summary),style=MaterialTheme.typography.bodySmall);Text(stringResource(R.string.estimated_file_size,formatBytes(state.dataset?.estimatedBytes?:0)),style=MaterialTheme.typography.bodySmall)}
                item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){OutlinedButton(onClick={save.launch(viewModel.suggestedFileName())},modifier=Modifier.testTag("save_csv"),enabled=!state.busy&&state.dataset?.rows?.isNotEmpty()==true){Icon(Icons.Rounded.Save,null);Text(stringResource(R.string.save))};Spacer(Modifier.width(8.dp));Button(onClick={warning=true},modifier=Modifier.testTag("share_csv"),enabled=!state.busy&&state.dataset?.rows?.isNotEmpty()==true){Icon(Icons.Rounded.Share,null);Text(stringResource(R.string.share))}}}
            } else {
                item{Text(stringResource(R.string.report_photos),style=MaterialTheme.typography.titleMedium);ReportPhotoMode.entries.forEach{mode->Row(verticalAlignment=Alignment.CenterVertically){RadioButton(state.reportPhotoMode==mode,{viewModel.setPhotoMode(mode)});Text(photoModeLabel(mode))}};if(state.reportPhotoMode!=ReportPhotoMode.NONE)Text(stringResource(R.string.report_sensitive_photo_warning),color=MaterialTheme.colorScheme.error);if(state.reportPhotoMode==ReportPhotoMode.ALL)Text(stringResource(R.string.report_photo_size,state.dataset?.counts?.photos?:0,formatBytes(state.dataset?.estimatedBytes?:0)),style=MaterialTheme.typography.bodySmall)}
                item{Button(onClick={viewModel.generateReport();showReport=true},modifier=Modifier.testTag("generate_report"),enabled=!state.busy){Icon(Icons.Rounded.Description,null);Text(stringResource(R.string.report_preview_action))}}
            }
            item{state.history.lastCsvAt?.let{Text(stringResource(R.string.last_csv_export,ExportFormat.dateTime(it).orEmpty(),exportTypeLabel(state.history.lastCsvType?:ExportType.DEVICES),state.history.lastCsvCount))};state.history.lastReportAt?.let{Text(stringResource(R.string.last_report,ExportFormat.dateTime(it).orEmpty(),state.history.lastReportTarget.orEmpty()))}}
            state.error?.let{item{Text(it,color=MaterialTheme.colorScheme.error)}};state.message?.let{item{Text(it,color=MaterialTheme.colorScheme.primary)}};if(shareError)item{Text(stringResource(R.string.share_failed),color=MaterialTheme.colorScheme.error)}
        }
    }
    if(showColumns)ColumnDialog(state,viewModel){showColumns=false}
    if(warning)AlertDialog({warning=false},title={Text(stringResource(R.string.share_confirmation))},text={Text(stringResource(R.string.csv_sensitive_warning))},confirmButton={TextButton({warning=false;share("text/csv",csvChooser,viewModel::shareFile)}){Text(stringResource(R.string.continue_sharing))}},dismissButton={TextButton({warning=false}){Text(stringResource(R.string.cancel))}})
    if(showReport)state.reportHtml?.let{html->ReportDialog(html,{share("text/html",reportChooser,viewModel::shareReportFile)},{showReport=false})}
}

@Composable private fun exportTypeLabel(type:ExportType)=stringResource(when(type){ExportType.DEVICES->R.string.export_type_devices;ExportType.BSSIDS->R.string.export_type_bssids;ExportType.PHOTOS->R.string.export_type_photos;ExportType.REPORT->R.string.export_type_report})
@Composable private fun photoModeLabel(mode:ReportPhotoMode)=stringResource(when(mode){ReportPhotoMode.NONE->R.string.report_photo_none;ReportPhotoMode.PRIMARY->R.string.report_photo_primary;ReportPhotoMode.ALL->R.string.report_photo_all})
@Composable private fun ColumnDialog(state:ExportUiState,vm:ExportViewModel,onDismiss:()->Unit){
    AlertDialog(onDismissRequest=onDismiss,title={Text(stringResource(R.string.column_settings))},text={Column{
        Row(Modifier.horizontalScroll(rememberScrollState())){TextButton(vm::selectAll){Text(stringResource(R.string.select_all))};TextButton(vm::minimum){Text(stringResource(R.string.minimum_columns))};TextButton(vm::standard){Text(stringResource(R.string.reset_columns))}}
        LazyColumn(Modifier.heightIn(max=480.dp)){items(state.preset.order,key={it}){key->val c=ExportColumns.forType(state.type).first{it.key==key};Row(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically){Checkbox(key in state.preset.enabled,{vm.toggleColumn(key)});Text(exportColumnLabel(c.key),Modifier.weight(1f));IconButton({vm.moveColumn(key,-1)}){Icon(Icons.Rounded.ArrowUpward,stringResource(R.string.move_up))};IconButton({vm.moveColumn(key,1)}){Icon(Icons.Rounded.ArrowDownward,stringResource(R.string.move_down))}}}}
    }},confirmButton={TextButton(onDismiss){Text(stringResource(R.string.done))}})
}
@Composable private fun ReportDialog(html:String,onShare:()->Unit,onDismiss:()->Unit){val context=LocalContext.current;var webView by remember{mutableStateOf<WebView?>(null)};Dialog(onDismiss){Surface(Modifier.fillMaxSize()){Column{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(stringResource(R.string.report_preview_title),Modifier.weight(1f).padding(12.dp),style=MaterialTheme.typography.titleMedium);IconButton(onShare){Icon(Icons.Rounded.Share,stringResource(R.string.share_report_title))};IconButton({webView?.let{view->context.getSystemService(PrintManager::class.java).print("WiFiAnalyzer_Report",view.createPrintDocumentAdapter("WiFiAnalyzer_Report"),PrintAttributes.Builder().build())}}){Icon(Icons.Rounded.Print,stringResource(R.string.print_or_save_pdf))};IconButton(onDismiss){Icon(Icons.Rounded.Close,stringResource(R.string.close))}};AndroidView({WebView(it).apply{settings.javaScriptEnabled=false;loadDataWithBaseURL(null,html,"text/html","UTF-8",null);webView=this}},Modifier.fillMaxSize())}}}}
private fun formatBytes(bytes:Long):String=when{bytes>=1024*1024->"%.1f MB".format(java.util.Locale.ROOT,bytes/1024.0/1024.0);bytes>=1024->"%.1f KB".format(java.util.Locale.ROOT,bytes/1024.0);else->"$bytes B"}
@Composable private fun exportColumnLabel(key:String)=stringResource(when(key){
    "workspaceName"->R.string.report_workspace;"groupName"->R.string.report_group;"deviceName"->R.string.export_column_device_name
    "manufacturer"->R.string.report_manufacturer;"model"->R.string.report_model;"serialNumber"->R.string.report_serial
    "ssid"->R.string.ssid;"primaryBssid"->R.string.csv_field_primary_bssid;"allBssids"->R.string.csv_field_all_bssids
    "location"->R.string.report_location;"notes"->R.string.report_notes;"detectedStatus"->R.string.report_detected
    "latestRssi"->R.string.latest_rssi;"estimatedDistance"->R.string.report_distance;"lastSeenAt"->R.string.report_last_seen
    "photoCount"->R.string.export_column_photo_count;"primaryPhotoCaption"->R.string.export_column_primary_caption;"createdAt"->R.string.created_at;"updatedAt"->R.string.updated_at
    "bssid"->R.string.bssid;"band"->R.string.export_column_band;"label"->R.string.export_column_label;"channel"->R.string.export_column_channel
    "frequency"->R.string.export_column_frequency;"channelWidth"->R.string.export_column_channel_width;"security"->R.string.export_column_security
    "photoIndex"->R.string.export_column_photo_index;"caption"->R.string.photo_caption;"isPrimary"->R.string.photo_primary
    "mimeType"->R.string.export_column_mime;"width"->R.string.export_column_width;"height"->R.string.export_column_height;else->R.string.export_column_file_size})
