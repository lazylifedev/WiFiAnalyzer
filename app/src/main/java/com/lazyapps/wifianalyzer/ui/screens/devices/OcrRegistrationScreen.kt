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
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.res.stringResource
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
import com.lazyapps.wifianalyzer.R
import com.lazyapps.wifianalyzer.ui.components.localizedLabel
import com.lazyapps.wifianalyzer.domain.ocr.CandidateKind
import com.lazyapps.wifianalyzer.domain.ocr.ConfidenceLevel
import com.lazyapps.wifianalyzer.domain.ocr.ParsedDeviceLabel
import com.lazyapps.wifianalyzer.domain.ocr.ParsedFieldCandidate
import com.lazyapps.wifianalyzer.domain.ocr.SensitiveValueMasker
import com.lazyapps.wifianalyzer.domain.ocr.OcrUpdateMode
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
    existingDraft: DeviceInput? = null,
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
            title = { Text(stringResource(R.string.ocr_scan_device_label)) },
            navigationIcon = { IconButton(onClick = onBack, modifier = Modifier.testTag("ocr_back")) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) } },
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
                existingDevice = existingDraft != null,
                onContinue = { mode, savePhoto ->
                    val recognized = ocrViewModel.registrationDraft(savePhoto)
                    onUseDraft(existingDraft?.let { com.lazyapps.wifianalyzer.domain.ocr.OcrDeviceUpdateMerger.merge(it, recognized, mode).copy(pendingPhotoPath = recognized.pendingPhotoPath) } ?: recognized)
                },
                onManual = onManual,
            )
        }
    }
}

@Composable
internal fun PermissionExplanation(denied: Boolean, onRequest: () -> Unit, onManual: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AppSpacing.large), verticalArrangement = Arrangement.spacedBy(AppSpacing.large), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.CameraAlt, null, tint = MaterialTheme.colorScheme.primary)
        Text(
            stringResource(if (denied) R.string.camera_permission_denied else R.string.camera_permission_required),
            modifier = Modifier.testTag("camera_permission_title"),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(stringResource(R.string.camera_permission_ocr_body))
        Button(onClick = onRequest, modifier = Modifier.fillMaxWidth().testTag("request_camera_permission")) { Text(stringResource(R.string.allow_camera)) }
        TextButton(onClick = onManual, modifier = Modifier.testTag("ocr_manual")) { Text(stringResource(R.string.enter_manually)) }
    }
}

@Composable
internal fun PermanentPermissionDenial(onSettings: () -> Unit, onManual: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AppSpacing.large), verticalArrangement = Arrangement.spacedBy(AppSpacing.large)) {
        Text(stringResource(R.string.camera_permission_disabled), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.camera_permission_settings_body))
        Button(onClick = onSettings, modifier = Modifier.fillMaxWidth().testTag("open_camera_settings")) { Icon(Icons.Rounded.Settings, null); Text(stringResource(R.string.open_app_settings)) }
        OutlinedButton(onClick = onManual, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.enter_manually)) }
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
    val cameraInUseMessage = stringResource(R.string.camera_in_use)
    val cameraInitializationFailedMessage = stringResource(R.string.camera_initialization_failed)
    val captureFailedMessage = stringResource(R.string.capture_failed)

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
                onCameraError(if (error.message?.contains("IN_USE", true) == true) cameraInUseMessage else cameraInitializationFailedMessage)
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
            stringResource(R.string.align_device_label),
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
            ) { Icon(if (torch) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff, stringResource(if (torch) R.string.turn_flash_off else R.string.turn_flash_on), tint = Color.White) }
            Button(
                enabled = cameraReady && !processing && !capturing,
                onClick = {
                    capturing = true
                    imageCapture.targetRotation = previewView.display.rotation
                    val dir = File(context.cacheDir, "ocr-captures").apply { mkdirs() }
                    val file = File.createTempFile("label-", ".jpg", dir)
                    imageCapture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), mainExecutor, object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) { capturing = false; onCapture(file) }
                        override fun onError(exception: ImageCaptureException) { capturing = false; file.delete(); onCameraError(captureFailedMessage) }
                    })
                },
                modifier = Modifier.testTag("capture_label"),
            ) { Icon(Icons.Rounded.CameraAlt, null); Text(stringResource(R.string.capture)) }
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
        Text(stringResource(R.string.processing_label_on_device))
    }
}

@Composable
private fun FailurePanel(message: String, onRetake: () -> Unit, onManual: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AppSpacing.large), verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
        Text(message.ifBlank { stringResource(R.string.no_text_detected) }, style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.ocr_capture_tips))
        Button(onClick = onRetake, modifier = Modifier.fillMaxWidth().testTag("retake")) { Text(stringResource(R.string.retake)) }
        OutlinedButton(onClick = onManual, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.enter_manually)) }
    }
}

@Composable
internal fun ResultConfirmation(
    result: ParsedDeviceLabel,
    onUpdate: (ParsedFieldCandidate, ParsedFieldCandidate) -> Unit,
    onRetake: () -> Unit,
    onContinue: (OcrUpdateMode, Boolean) -> Unit,
    onManual: () -> Unit,
    existingDevice: Boolean = false,
) {
    var updateMode by rememberSaveable { mutableStateOf(OcrUpdateMode.FILL_BLANKS) }
    var savePhoto by rememberSaveable { mutableStateOf(false) }
    val manufacturerLabel = stringResource(R.string.manufacturer)
    val modelLabel = stringResource(R.string.model)
    val serialNumberLabel = stringResource(R.string.serial_number)
    val ssidCandidatesLabel = stringResource(R.string.ssid_candidates)
    val macBssidCandidatesLabel = stringResource(R.string.mac_bssid_candidates)
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("ocr_result_list"),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.small),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(AppSpacing.large),
    ) {
        item {
            Text(stringResource(if (!result.hasUsefulFields) R.string.ocr_read_failed else if (result.modelCandidates.isEmpty() || result.macCandidates.isEmpty()) R.string.ocr_partially_read else R.string.ocr_read_success), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.ocr_review_candidates), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!result.hasUsefulFields) {
                Text(stringResource(R.string.ocr_no_useful_fields), color = MaterialTheme.colorScheme.error)
            }
        }
        candidateSection(manufacturerLabel, result.manufacturerCandidates, onUpdate)
        candidateSection(modelLabel, result.modelCandidates, onUpdate)
        candidateSection(serialNumberLabel, result.serialCandidates, onUpdate)
        candidateSection(ssidCandidatesLabel, result.ssidCandidates, onUpdate)
        candidateSection(macBssidCandidatesLabel, result.macCandidates, onUpdate)
        if (existingDevice) item {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                Text(stringResource(R.string.ocr_update_method), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    FilterChip(updateMode == OcrUpdateMode.FILL_BLANKS, { updateMode = OcrUpdateMode.FILL_BLANKS }, label = { Text(stringResource(R.string.ocr_fill_blanks)) })
                    FilterChip(updateMode == OcrUpdateMode.SELECT_FIELDS, { updateMode = OcrUpdateMode.SELECT_FIELDS }, label = { Text(stringResource(R.string.ocr_select_fields)) })
                    FilterChip(updateMode == OcrUpdateMode.OVERWRITE, { updateMode = OcrUpdateMode.OVERWRITE }, label = { Text(stringResource(R.string.ocr_overwrite)) })
                }
                Text(stringResource(R.string.ocr_bssid_update_note), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (result.managementCandidates.isNotEmpty()) item {
            SensitiveSection(result.managementCandidates, onUpdate)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(AppSpacing.medium)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(savePhoto, { savePhoto = it }, modifier = Modifier.testTag("save_ocr_photo")); Text(stringResource(R.string.ocr_save_label_photo)) }
                    Text(stringResource(R.string.ocr_save_photo_privacy_note), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Card(border = CardDefaults.outlinedCardBorder()) {
                Column(Modifier.fillMaxWidth().padding(AppSpacing.medium), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    Text(stringResource(R.string.ocr_full_text), style = MaterialTheme.typography.titleMedium)
                    Text(result.rawText.ifBlank { stringResource(R.string.no_text_detected) }, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                OutlinedButton(onClick = onRetake, modifier = Modifier.testTag("retake")) { Text(stringResource(R.string.retake)) }
                TextButton(onClick = onManual, modifier = Modifier.testTag("ocr_enter_manually")) { Text(stringResource(R.string.enter_manually)) }
                Button(onClick = { onContinue(updateMode, savePhoto) }, modifier = Modifier.testTag("use_ocr_result")) { Text(stringResource(if (existingDevice) R.string.ocr_review_changes else R.string.ocr_continue_to_form)) }
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
            Text(stringResource(R.string.ocr_confidence, confidenceLabel(candidate.confidence)), style = MaterialTheme.typography.labelMedium)
            candidate.band?.let { Text(stringResource(R.string.ocr_frequency_band, it.label), style = MaterialTheme.typography.bodySmall) }
            candidate.nearbyMatch?.let { match ->
                Text(stringResource(R.string.ocr_detected_nearby, ocrMatchReason(match.reason)), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                Text("${match.accessPoint.bssid} / ${match.accessPoint.rssi} dBm / ${match.accessPoint.band.label} / CH ${match.accessPoint.channel} / ${match.accessPoint.distanceRange.localizedLabel(false)} / ${match.accessPoint.securityType.localizedLabel()}", style = MaterialTheme.typography.bodySmall)
                match.correctedValue?.let { Text(stringResource(R.string.ocr_correction_found, it), style = MaterialTheme.typography.bodySmall) }
            }
            candidate.correctionCandidate?.takeIf { candidate.nearbyMatch == null }?.let { Text(stringResource(R.string.ocr_correction_unselected, it), style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable private fun ocrMatchReason(reason: String) = stringResource(when (reason) {
    "BSSID_EXACT" -> R.string.ocr_match_bssid_exact
    "OCR_CORRECTED" -> R.string.ocr_match_corrected
    "MANUFACTURER_OUI" -> R.string.ocr_match_oui
    "SSID_EXACT" -> R.string.ocr_match_ssid_exact
    else -> R.string.ocr_match_ssid_normalized
})

@Composable
private fun SensitiveSection(candidates: List<ParsedFieldCandidate>, onUpdate: (ParsedFieldCandidate, ParsedFieldCandidate) -> Unit) {
    var revealed by rememberSaveable { mutableStateOf(emptyList<Int>()) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(AppSpacing.medium), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            Text(stringResource(R.string.ocr_management_candidates), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.ocr_sensitive_note), style = MaterialTheme.typography.bodySmall)
            candidates.forEachIndexed { index, candidate ->
                Text("${candidate.sourceLabel}: ${if (index in revealed) candidate.value else SensitiveValueMasker.mask(candidate.value)}")
                TextButton(onClick = { revealed = if (index in revealed) revealed - index else revealed + index }) { Text(stringResource(if (index in revealed) R.string.hide else R.string.show)) }
            }
        }
    }
}

@Composable
private fun confidenceLabel(level: ConfidenceLevel): String = stringResource(when (level) {
    ConfidenceLevel.HIGH -> R.string.confidence_high
    ConfidenceLevel.MEDIUM -> R.string.confidence_medium
    ConfidenceLevel.LOW -> R.string.confidence_low
})

private tailrec fun Context.findActivity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> error("Activity context is required")
}
