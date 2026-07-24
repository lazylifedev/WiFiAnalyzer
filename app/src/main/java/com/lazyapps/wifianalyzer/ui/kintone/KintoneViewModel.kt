package com.lazyapps.wifianalyzer.ui.kintone

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lazyapps.wifianalyzer.R
import com.lazyapps.wifianalyzer.data.registry.WifiAnalyzerDatabase
import com.lazyapps.wifianalyzer.kintone.KintoneConnectionSummary
import com.lazyapps.wifianalyzer.kintone.KintoneErrorCode
import com.lazyapps.wifianalyzer.kintone.KintoneException
import com.lazyapps.wifianalyzer.kintone.KintoneQrParser
import com.lazyapps.wifianalyzer.kintone.KintoneQrPayload
import com.lazyapps.wifianalyzer.kintone.KintoneRepository
import com.lazyapps.wifianalyzer.kintone.KintoneVerification
import com.lazyapps.wifianalyzer.kintone.KintoneSyncPreview
import com.lazyapps.wifianalyzer.kintone.KintoneSyncResult
import com.lazyapps.wifianalyzer.ui.operation.OperationProgress
import com.lazyapps.wifianalyzer.ui.operation.OperationError
import com.lazyapps.wifianalyzer.ui.operation.OperationErrorCategory
import com.lazyapps.wifianalyzer.ui.operation.OperationState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PendingKintoneConnection(
    val workspaceId: Long,
    val workspaceName: String,
    val payload: KintoneQrPayload,
    val verification: KintoneVerification,
    val replacing: Boolean,
    val duplicateTarget: Boolean,
)

data class KintoneUiState(
    val workspaceId: Long = 0,
    val workspaceName: String = "",
    val connection: KintoneConnectionSummary? = null,
    val pending: PendingKintoneConnection? = null,
    val operation: OperationState = OperationState.Idle,
    val errorCode: KintoneErrorCode? = null,
    val syncPreview: KintoneSyncPreview? = null,
    val syncResult: KintoneSyncResult? = null,
)

class KintoneViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = KintoneRepository(WifiAnalyzerDatabase.get(application))
    private val mutable = MutableStateFlow(KintoneUiState())
    val uiState: StateFlow<KintoneUiState> = mutable.asStateFlow()
    private var observeJob: Job? = null
    private var operationJob: Job? = null
    private var eventId = 1L

    fun selectWorkspace(id: Long, name: String) {
        if (id <= 0 || mutable.value.workspaceId == id) return
        mutable.value = KintoneUiState(workspaceId = id, workspaceName = name)
        observeJob?.cancel()
        observeJob = viewModelScope.launch { repository.observe(id).collect { mutable.value = mutable.value.copy(connection = it) } }
    }

    fun acceptQr(raw: String, workspaceId: Long, workspaceName: String) {
        if (operationJob?.isActive == true) return
        val payload = try { KintoneQrParser.parse(raw) } catch (e: KintoneException) {
            fail(e.code); return
        }
        mutable.value = mutable.value.copy(workspaceId = workspaceId, workspaceName = workspaceName, errorCode = null)
        operationJob = viewModelScope.launch {
            mutable.value = mutable.value.copy(operation = OperationState.Running(R.string.kintone_verifying, progress = OperationProgress.Indeterminate, cancellable = true))
            try {
                val verification = repository.verify(payload)
                val duplicate = repository.hasDuplicateTarget(workspaceId, payload.domain, payload.appId)
                mutable.value = mutable.value.copy(
                    pending = PendingKintoneConnection(workspaceId, workspaceName, payload, verification, mutable.value.connection != null, duplicate),
                    operation = OperationState.Idle,
                )
            } catch (e: KintoneException) { fail(e.code) }
        }
    }

    fun confirmSave() {
        val pending = mutable.value.pending ?: return
        if (operationJob?.isActive == true) return
        operationJob = viewModelScope.launch {
            mutable.value = mutable.value.copy(operation = OperationState.Running(R.string.kintone_saving, cancellable = false))
            try {
                repository.save(pending.workspaceId, pending.payload)
                mutable.value = mutable.value.copy(pending = null, operation = OperationState.Success(R.string.kintone_connected, eventId++), errorCode = null)
            } catch (e: KintoneException) { fail(e.code) }
        }
    }

    fun reverify() = launchOperation {
        repository.reverify(mutable.value.workspaceId)
        mutable.value = mutable.value.copy(operation = OperationState.Success(R.string.kintone_verified, eventId++), errorCode = null)
    }

    fun disconnect() = launchOperation {
        repository.disconnect(mutable.value.workspaceId)
        mutable.value = mutable.value.copy(operation = OperationState.Success(R.string.kintone_disconnected, eventId++), pending = null, errorCode = null)
    }

    fun sync() = launchOperation {
        val records = repository.buildSyncRecords(mutable.value.workspaceId)
        val hadPreview = mutable.value.syncPreview != null
        val preview = mutable.value.syncPreview ?: repository.previewSync(mutable.value.workspaceId).also {
            mutable.value = mutable.value.copy(syncPreview = it)
        }
        if (!hadPreview || preview.valid == 0) {
            if (preview.valid == 0) throw KintoneException(KintoneErrorCode.KINTONE_VALIDATION_FAILED)
            return@launchOperation
        }
        val result = repository.sync(mutable.value.workspaceId, records.filter { it.deviceUuid.isNotBlank() }) { current, total ->
            mutable.value = mutable.value.copy(operation = OperationState.Running(R.string.kintone_verifying, progress = OperationProgress.Count(current, total), cancellable = true))
        }
        mutable.value = mutable.value.copy(syncResult = result, operation = OperationState.Success(R.string.kintone_connected, eventId++))
    }

    fun cancel() { operationJob?.cancel(); operationJob = null; mutable.value = mutable.value.copy(operation = OperationState.Idle, pending = null) }
    fun consumeEvent(id: Long) { val op = mutable.value.operation; if ((op as? OperationState.Success)?.eventId == id || (op as? OperationState.Failure)?.eventId == id) mutable.value = mutable.value.copy(operation = OperationState.Idle) }

    private fun launchOperation(block: suspend () -> Unit) {
        if (operationJob?.isActive == true) return
        operationJob = viewModelScope.launch {
            mutable.value = mutable.value.copy(operation = OperationState.Running(R.string.kintone_verifying, cancellable = true))
            try { block() } catch (e: KintoneException) { fail(e.code) }
        }
    }

    private fun fail(code: KintoneErrorCode) {
        val category = when (code) {
            KintoneErrorCode.KINTONE_NETWORK_UNAVAILABLE, KintoneErrorCode.KINTONE_TIMEOUT, KintoneErrorCode.KINTONE_TLS_ERROR -> OperationErrorCategory.NETWORK_SCAN_FAILED
            KintoneErrorCode.KINTONE_CONNECTION_CANCELLED -> OperationErrorCategory.CANCELLED
            KintoneErrorCode.KINTONE_SECURE_STORAGE_FAILED -> OperationErrorCategory.DATABASE_FAILED
            else -> OperationErrorCategory.VALIDATION_FAILED
        }
        mutable.value = mutable.value.copy(
            errorCode = code,
            operation = OperationState.Failure(OperationError(category, R.string.kintone_verifying, stableCode = code.name), eventId++),
            pending = null,
        )
    }
}
