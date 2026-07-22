package com.lazyapps.wifianalyzer.ui.screens.devices

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lazyapps.wifianalyzer.domain.DeviceInput
import com.lazyapps.wifianalyzer.domain.ocr.CandidateKind
import com.lazyapps.wifianalyzer.domain.ocr.ConfidenceLevel
import com.lazyapps.wifianalyzer.domain.ocr.ParsedDeviceLabel
import com.lazyapps.wifianalyzer.domain.ocr.ParsedFieldCandidate
import com.lazyapps.wifianalyzer.domain.ocr.SensitiveValueMasker
import com.lazyapps.wifianalyzer.model.WifiAccessPoint
import com.lazyapps.wifianalyzer.ui.theme.AppSpacing
import java.io.File

private enum class CameraPermissionUiState { NOT_REQUESTED, DENIED, PERMANENTLY_DENIED, GRANTED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrRegistrationScreen(
    nearby: List<WifiAccessPoint>,
    onBack: () -> Unit,
    onUseDraft: (DeviceInput) -> Unit,
    onManual: () -> Unit,
    ocrViewModel: OcrRegistrationViewModel = viewModel(),
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val prefs = remember(context) { context.getSharedPreferences("camera_permission", Context.MODE_PRIVATE) }
    var requested by rememberSaveable { mutableStateOf(prefs.getBoolean("requested", false)) }
    var permissionRevision by remember { mutableIntStateOf(0) }
    var cameraError by rememberSaveable { mutableStateOf<String?>(null) }
    fun permissionState(): CameraPermissionUiState {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) return CameraPermissionUiState.GRANTED
        if (!requested) return CameraPermissionUiState.NOT_REQUESTED
        return if (activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) CameraPermissionUiState.DENIED else CameraPermissionUiState.PERMANENTLY_DENIED
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        requested = true
        prefs.edit { putBoolean("requested", true) }
        permissionRevision++
    }
    val uiState by ocrViewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) permissionRevision++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopAppBar(
            title = { Text("機器ラベルを読み取る") },
            navigationIcon = { IconButton(onClick = onBack, modifier = Modifier.testTag("ocr_back")) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "戻る") } },
        )
        when (uiState.state) {
            OcrProcessingState.CAMERA -> when (permissionState().also { permissionRevision }) {
                CameraPermissionUiState.GRANTED -> if (cameraError == null) {
                    CameraCapture(
                        processing = false,
                        onCapture = { ocrViewModel.process(it, nearby) },
                        onCameraError = { cameraError = it },
                    )
                } else {
                    FailurePanel(cameraError.orEmpty(), { cameraError = null }, onManual)
                }
                CameraPermissionUiState.NOT_REQUESTED, CameraPermissionUiState.DENIED -> PermissionExplanation(
                    denied = permissionState() == CameraPermissionUiState.DENIED,
                    onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onManual = onManual,
                )
                CameraPermissionUiState.PERMANENTLY_DENIED -> PermanentPermissionDenial(
                    onSettings = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())) },
                    onManual = onManual,
                )
            }
            OcrProcessingState.PROCESSING -> ProcessingPanel()
            OcrProcessingState.ERROR -> FailurePanel(uiState.errorMessage.orEmpty(), ocrViewModel::retake, onManual)
            OcrProcessingState.RESULT -> ResultConfirmation(
                result = uiState.result ?: ParsedDeviceLabel(),
                onUpdate = ocrViewModel::updateCandidate,
                onRetake = ocrViewModel::retake,
                onContinue = { onUseDraft(ocrViewModel.registrationDraft()) },
                onManual = onManual,
            )
        }
    }
}

@Composable
internal fun PermissionExplanation(denied: Boolean, onRequest: () -> Unit, onManual: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AppSpacing.large), verticalArrangement = Arrangement.spacedBy(AppSpacing.large), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.CameraAlt, null, tint = MaterialTheme.colorScheme.primary)
        Text(if (denied) "カメラ権限が拒否されました" else "カメラ権限が必要です", style = MaterialTheme.typography.headlineSmall)
        Text("機器ラベルを撮影して、メーカー・SSID・BSSIDなどを端末内だけで読み取るために使用します。画像は処理後に削除されます。")
        Button(onClick = onRequest, modifier = Modifier.fillMaxWidth().testTag("request_camera_permission")) { Text("カメラを許可") }
        TextButton(onClick = onManual, modifier = Modifier.testTag("ocr_manual")) { Text("手動で入力する") }
    }
}

@Composable
internal fun PermanentPermissionDenial(onSettings: () -> Unit, onManual: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AppSpacing.large), verticalArrangement = Arrangement.spacedBy(AppSpacing.large)) {
        Text("カメラ権限が無効です", style = MaterialTheme.typography.headlineSmall)
        Text("再要求できないため、端末のアプリ設定でカメラ権限を許可してください。")
        Button(onClick = onSettings, modifier = Modifier.fillMaxWidth().testTag("open_camera_settings")) { Icon(Icons.Rounded.Settings, null); Text("アプリ設定を開く") }
        OutlinedButton(onClick = onManual, modifier = Modifier.fillMaxWidth()) { Text("手動で入力する") }
    }
}

@Composable
private fun CameraCapture(processing: Boolean, onCapture: (File) -> Unit, onCameraError: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torch by rememberSaveable { mutableStateOf(false) }
    var cameraReady by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        File(context.cacheDir, "ocr-captures").apply { mkdirs() }.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 60 * 60 * 1000L }?.forEach(File::delete)
    }
    DisposableEffect(lifecycleOwner, previewView) {
        val future = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            try {
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                provider.unbindAll()
                camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                cameraReady = true
                if (torch) camera?.cameraControl?.enableTorch(true)
            } catch (error: Exception) {
                onCameraError(if (error.message?.contains("IN_USE", true) == true) "カメラは他のアプリで使用中です" else "カメラを初期化できませんでした")
            }
        }
        future.addListener(listener, mainExecutor)
        onDispose {
            torch = false
            runCatching { camera?.cameraControl?.enableTorch(false) }
            if (future.isDone) runCatching { future.get().unbindAll() }
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        ScanFrameOverlay(Modifier.fillMaxSize())
        Text(
            "機器ラベルを枠内に合わせてください",
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.align(Alignment.TopCenter).padding(AppSpacing.large),
        )
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(AppSpacing.large),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                enabled = camera?.cameraInfo?.hasFlashUnit() == true,
                onClick = { torch = !torch; camera?.cameraControl?.enableTorch(torch) },
                modifier = Modifier.testTag("toggle_flash"),
            ) { Icon(if (torch) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff, if (torch) "フラッシュを切る" else "フラッシュを点ける", tint = Color.White) }
            Button(
                enabled = cameraReady && !processing && !capturing,
                onClick = {
                    capturing = true
                    imageCapture.targetRotation = previewView.display.rotation
                    val dir = File(context.cacheDir, "ocr-captures").apply { mkdirs() }
                    val file = File.createTempFile("label-", ".jpg", dir)
                    imageCapture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), mainExecutor, object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) { capturing = false; onCapture(file) }
                        override fun onError(exception: ImageCaptureException) { capturing = false; file.delete(); onCameraError("撮影できませんでした") }
                    })
                },
                modifier = Modifier.testTag("capture_label"),
            ) { Icon(Icons.Rounded.CameraAlt, null); Text("撮影") }
        }
    }
}

@Composable
private fun ScanFrameOverlay(modifier: Modifier = Modifier) {
    val border = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val left = size.width * .10f
        val right = size.width * .90f
        val top = size.height * .22f
        val bottom = size.height * .78f
        val shade = Color.Black.copy(alpha = .48f)
        drawRect(shade, size = androidx.compose.ui.geometry.Size(size.width, top))
        drawRect(shade, topLeft = Offset(0f, bottom), size = androidx.compose.ui.geometry.Size(size.width, size.height - bottom))
        drawRect(shade, topLeft = Offset(0f, top), size = androidx.compose.ui.geometry.Size(left, bottom - top))
        drawRect(shade, topLeft = Offset(right, top), size = androidx.compose.ui.geometry.Size(size.width - right, bottom - top))
        drawRoundRect(border, topLeft = Offset(left, top), size = androidx.compose.ui.geometry.Size(right - left, bottom - top), cornerRadius = androidx.compose.ui.geometry.CornerRadius(28f), style = Stroke(width = 5f, pathEffect = PathEffect.cornerPathEffect(8f)))
    }
}

@Composable
private fun ProcessingPanel() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
        CircularProgressIndicator()
        Text("端末内でラベルを解析しています")
    }
}

@Composable
private fun FailurePanel(message: String, onRetake: () -> Unit, onManual: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AppSpacing.large), verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
        Text(message.ifBlank { "文字を検出できませんでした" }, style = MaterialTheme.typography.headlineSmall)
        Text("ラベルへ近づき、端末を平行にして、反射を避けた明るい場所で撮影してください。必要ならフラッシュを切り替えてください。")
        Button(onClick = onRetake, modifier = Modifier.fillMaxWidth().testTag("retake")) { Text("再撮影") }
        OutlinedButton(onClick = onManual, modifier = Modifier.fillMaxWidth()) { Text("手動で入力する") }
    }
}

@Composable
internal fun ResultConfirmation(
    result: ParsedDeviceLabel,
    onUpdate: (ParsedFieldCandidate, ParsedFieldCandidate) -> Unit,
    onRetake: () -> Unit,
    onContinue: () -> Unit,
    onManual: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("ocr_result_list"),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.small),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(AppSpacing.large),
    ) {
        item {
            Text(if (!result.hasUsefulFields) "読み取れませんでした" else if (result.modelCandidates.isEmpty() || result.macCandidates.isEmpty()) "一部読み取り成功" else "読み取り成功", style = MaterialTheme.typography.headlineSmall)
            Text("内容を確認・修正し、登録する候補だけを選択してください。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!result.hasUsefulFields) {
                Text("文字またはMACらしい文字列を検出できませんでした。ラベルへ近づき、端末を平行にして、反射を避けた明るい場所で再撮影してください。", color = MaterialTheme.colorScheme.error)
            }
        }
        candidateSection("メーカー", result.manufacturerCandidates, onUpdate)
        candidateSection("型番", result.modelCandidates, onUpdate)
        candidateSection("シリアル番号", result.serialCandidates, onUpdate)
        candidateSection("SSID候補", result.ssidCandidates, onUpdate)
        candidateSection("MAC / BSSID候補", result.macCandidates, onUpdate)
        if (result.managementCandidates.isNotEmpty()) item {
            SensitiveSection(result.managementCandidates, onUpdate)
        }
        item {
            Card(border = CardDefaults.outlinedCardBorder()) {
                Column(Modifier.fillMaxWidth().padding(AppSpacing.medium), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    Text("OCR全文", style = MaterialTheme.typography.titleMedium)
                    Text(result.rawText.ifBlank { "文字を検出できませんでした" }, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                OutlinedButton(onClick = onRetake, modifier = Modifier.testTag("retake")) { Text("再撮影") }
                TextButton(onClick = onManual) { Text("手動で入力") }
                Button(onClick = onContinue, modifier = Modifier.testTag("use_ocr_result")) { Text("登録フォームへ進む") }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.candidateSection(
    title: String,
    candidates: List<ParsedFieldCandidate>,
    onUpdate: (ParsedFieldCandidate, ParsedFieldCandidate) -> Unit,
) {
    if (candidates.isEmpty()) return
    item { Text(title, style = MaterialTheme.typography.titleMedium) }
    itemsIndexed(candidates) { index, candidate -> CandidateEditor(candidate, index, onUpdate) }
}

@Composable
private fun CandidateEditor(candidate: ParsedFieldCandidate, index: Int, onUpdate: (ParsedFieldCandidate, ParsedFieldCandidate) -> Unit) {
    Card(border = CardDefaults.outlinedCardBorder()) {
        Column(Modifier.fillMaxWidth().padding(AppSpacing.medium), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(candidate.selected, { onUpdate(candidate, candidate.copy(selected = it)) }, modifier = Modifier.testTag("candidate_${candidate.kind}_$index"))
                OutlinedTextField(candidate.value, { onUpdate(candidate, candidate.copy(value = it)) }, Modifier.weight(1f), singleLine = true, label = { Text(candidate.sourceLabel.ifBlank { candidate.kind.name }) })
            }
            Text("信頼度: ${candidate.confidence.label}", style = MaterialTheme.typography.labelMedium)
            candidate.band?.let { Text("周波数帯: ${it.label}", style = MaterialTheme.typography.bodySmall) }
            candidate.nearbyMatch?.let { match ->
                Text("周辺で検出中 ・ ${match.reason}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                Text("${match.accessPoint.bssid} / ${match.accessPoint.rssi} dBm / ${match.accessPoint.band.label} / CH ${match.accessPoint.channel} / ${match.accessPoint.distanceRange.label} / ${match.accessPoint.securityType.label}", style = MaterialTheme.typography.bodySmall)
                match.correctedValue?.let { Text("「${it}」へ補正する候補が見つかりました。自動では置き換えません。", style = MaterialTheme.typography.bodySmall) }
            }
            candidate.correctionCandidate?.takeIf { candidate.nearbyMatch == null }?.let { Text("補正候補: $it（未選択）", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun SensitiveSection(candidates: List<ParsedFieldCandidate>, onUpdate: (ParsedFieldCandidate, ParsedFieldCandidate) -> Unit) {
    var revealed by rememberSaveable { mutableStateOf(emptyList<Int>()) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(AppSpacing.medium), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            Text("管理情報候補", style = MaterialTheme.typography.titleMedium)
            Text("機密候補は登録フォームやRoomへ渡しません。表示・コピーは明示操作が必要です。", style = MaterialTheme.typography.bodySmall)
            candidates.forEachIndexed { index, candidate ->
                Text("${candidate.sourceLabel}: ${if (index in revealed) candidate.value else SensitiveValueMasker.mask(candidate.value)}")
                TextButton(onClick = { revealed = if (index in revealed) revealed - index else revealed + index }) { Text(if (index in revealed) "隠す" else "表示") }
            }
        }
    }
}

private val ConfidenceLevel.label: String get() = when (this) { ConfidenceLevel.HIGH -> "高"; ConfidenceLevel.MEDIUM -> "中"; ConfidenceLevel.LOW -> "低" }

private tailrec fun Context.findActivity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> error("Activity context is required")
}
