package com.lazyapps.wifianalyzer.ui.screens.devices

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lazyapps.wifianalyzer.data.ocr.LabelTextRecognizer
import com.lazyapps.wifianalyzer.data.ocr.OcrImageProcessor
import com.lazyapps.wifianalyzer.domain.DeviceInput
import com.lazyapps.wifianalyzer.domain.ocr.CandidateKind
import com.lazyapps.wifianalyzer.domain.ocr.DeviceLabelParser
import com.lazyapps.wifianalyzer.domain.ocr.ParsedDeviceLabel
import com.lazyapps.wifianalyzer.domain.ocr.ParsedFieldCandidate
import com.lazyapps.wifianalyzer.domain.ocr.OcrRegistrationDraftFactory
import com.lazyapps.wifianalyzer.domain.ocr.OcrDeviceUpdateMerger
import com.lazyapps.wifianalyzer.domain.ocr.OcrUpdateMode
import com.lazyapps.wifianalyzer.model.WifiAccessPoint
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class OcrProcessingState { CAMERA, PROCESSING, RESULT, ERROR }

data class OcrResultUiState(
    val state: OcrProcessingState = OcrProcessingState.CAMERA,
    val result: ParsedDeviceLabel? = null,
    val errorMessage: String? = null,
)

class OcrRegistrationViewModel(application: Application) : AndroidViewModel(application) {
    private val recognizer = LabelTextRecognizer()
    private val _uiState = MutableStateFlow(OcrResultUiState())
    val uiState: StateFlow<OcrResultUiState> = _uiState.asStateFlow()
    private var processingJob: Job? = null

    fun process(file: File, nearby: List<WifiAccessPoint>) {
        if (processingJob?.isActive == true) return
        _uiState.value = OcrResultUiState(OcrProcessingState.PROCESSING)
        processingJob = viewModelScope.launch {
            try {
                val parsed = withContext(Dispatchers.Default) {
                    val bitmap = OcrImageProcessor.process(file)
                    try { DeviceLabelParser.parse(recognizer.recognize(bitmap), nearby) } finally { bitmap.recycle() }
                }
                _uiState.value = OcrResultUiState(OcrProcessingState.RESULT, result = parsed)
            } catch (_: kotlinx.coroutines.CancellationException) {
                _uiState.value = OcrResultUiState()
            } catch (_: Exception) {
                _uiState.value = OcrResultUiState(OcrProcessingState.ERROR, errorMessage = "認識処理を完了できませんでした")
            } finally {
                file.delete()
            }
        }
    }

    fun retake() {
        processingJob?.cancel()
        _uiState.value = OcrResultUiState()
    }

    fun updateCandidate(current: ParsedFieldCandidate, updated: ParsedFieldCandidate) {
        val result = _uiState.value.result ?: return
        val exclusive = updated.selected && updated.kind in setOf(CandidateKind.MANUFACTURER, CandidateKind.MODEL, CandidateKind.SERIAL, CandidateKind.SSID)
        fun List<ParsedFieldCandidate>.replace() = map {
            when {
                it == current -> updated
                exclusive && it.kind == updated.kind -> it.copy(selected = false)
                else -> it
            }
        }
        _uiState.value = _uiState.value.copy(result = result.copy(
            manufacturerCandidates = result.manufacturerCandidates.replace(),
            modelCandidates = result.modelCandidates.replace(),
            serialCandidates = result.serialCandidates.replace(),
            ssidCandidates = result.ssidCandidates.replace(),
            macCandidates = result.macCandidates.replace(),
            managementCandidates = result.managementCandidates.replace(),
        ))
    }

    fun registrationDraft(): DeviceInput {
        return OcrRegistrationDraftFactory.create(_uiState.value.result ?: ParsedDeviceLabel())
    }

    fun updateDraft(current: DeviceInput, mode: OcrUpdateMode): DeviceInput =
        OcrDeviceUpdateMerger.merge(current, registrationDraft(), mode)

    override fun onCleared() {
        recognizer.close()
        super.onCleared()
    }
}
