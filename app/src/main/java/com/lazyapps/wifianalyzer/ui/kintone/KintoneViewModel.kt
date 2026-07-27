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
import com.lazyapps.wifianalyzer.kintone.KintoneAutoSyncScheduler
import com.lazyapps.wifianalyzer.kintone.KintoneAutoSyncState
import com.lazyapps.wifianalyzer.kintone.KintoneAutoSyncStore
import com.lazyapps.wifianalyzer.kintone.KintoneSyncLock
import com.lazyapps.wifianalyzer.kintone.KintoneSyncStatus
import com.lazyapps.wifianalyzer.kintone.KintoneSyncTrigger
import com.lazyapps.wifianalyzer.kintone.KintoneWorkspaceOption
import com.lazyapps.wifianalyzer.kintone.KintoneManualSyncSelectionStore
import com.lazyapps.wifianalyzer.kintone.KintoneMultiSyncResult
import com.lazyapps.wifianalyzer.kintone.KintoneWorkspaceSyncResult
import com.lazyapps.wifianalyzer.kintone.WorkspaceUuid
import com.lazyapps.wifianalyzer.ui.operation.OperationProgress
import com.lazyapps.wifianalyzer.ui.operation.OperationError
import com.lazyapps.wifianalyzer.ui.operation.OperationErrorCategory
import com.lazyapps.wifianalyzer.ui.operation.OperationState
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

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
    val appWorkspaceId: Long = 0,
    val workspaces: List<KintoneWorkspaceOption> = emptyList(),
    val connection: KintoneConnectionSummary? = null,
    val pending: PendingKintoneConnection? = null,
    val operation: OperationState = OperationState.Idle,
    val errorCode: KintoneErrorCode? = null,
    val syncPreview: KintoneSyncPreview? = null,
    val syncResult: KintoneSyncResult? = null,
    val selectedWorkspaceIds: Set<Long> = emptySet(),
    val multiSyncResult: KintoneMultiSyncResult? = null,
    val syncingWorkspaceIndex: Int = 0,
    val autoSync: KintoneAutoSyncState = KintoneAutoSyncState(),
    val canUseKintone: Boolean = false,
    val message: String? = null,
    val failureContext: KintoneFailureContext? = null,
)

enum class KintoneFailureContext { QR, CONNECTION, SYNC }

class KintoneViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = KintoneRepository(WifiAnalyzerDatabase.get(application), context = application)
    private val mutable = MutableStateFlow(KintoneUiState())
    val uiState: StateFlow<KintoneUiState> = mutable.asStateFlow()
    private var observeJob: Job? = null
    private var operationJob: Job? = null
    private var eventId = 1L
    private val autoSyncStore = KintoneAutoSyncStore(application)
    private val autoSyncScheduler = KintoneAutoSyncScheduler(application)
    private val manualSelectionStore = KintoneManualSyncSelectionStore(application)

    init {
        viewModelScope.launch {
            while (true) {
                val id = mutable.value.workspaceId
                val options = repository.workspaceOptions(autoSyncStore)
                val selected = manualSelectionStore.reconcile(options.map { it.id }, mutable.value.appWorkspaceId.takeIf { it > 0 } ?: id)
                if (id > 0 && options.none { it.id == id }) {
                    options.firstOrNull()?.let { selectWorkspace(it.id, it.name) }
                } else if (id > 0) {
                    mutable.value = mutable.value.copy(
                        workspaceName = options.firstOrNull { it.id == id }?.name ?: mutable.value.workspaceName,
                        autoSync = autoSyncStore.read(WorkspaceUuid.fromId(id)),
                        workspaces = options,
                        selectedWorkspaceIds = selected,
                    )
                } else {
                    mutable.value = mutable.value.copy(workspaces = options, selectedWorkspaceIds = selected)
                }
                delay(1_000)
            }
        }
    }

    fun setAccessAllowed(allowed: Boolean) {
        if (!allowed && mutable.value.workspaceId > 0 && mutable.value.autoSync.enabled) autoSyncScheduler.disable(mutable.value.workspaceId)
        if (mutable.value.canUseKintone != allowed) mutable.value = mutable.value.copy(canUseKintone = allowed)
    }

    fun selectWorkspace(id: Long, name: String, fromAppSelection: Boolean = false) {
        if (id <= 0) return
        if (mutable.value.workspaceId == id) { mutable.value = mutable.value.copy(workspaceName = name, appWorkspaceId = if (fromAppSelection) id else mutable.value.appWorkspaceId); return }
        mutable.value = KintoneUiState(
            workspaceId = id,
            workspaceName = name,
            appWorkspaceId = if (fromAppSelection) id else mutable.value.appWorkspaceId,
            workspaces = mutable.value.workspaces,
            selectedWorkspaceIds = mutable.value.selectedWorkspaceIds,
            canUseKintone = mutable.value.canUseKintone,
            autoSync = autoSyncStore.read(WorkspaceUuid.fromId(id)),
        )
        observeJob?.cancel()
        observeJob = viewModelScope.launch { repository.observe(id).collect { mutable.value = mutable.value.copy(connection = it) } }
    }

    fun setManualSyncWorkspaces(ids: Set<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val ordered = manualSelectionStore.write(ids, mutable.value.workspaces.map { it.id })
            mutable.value = mutable.value.copy(selectedWorkspaceIds = ordered, syncPreview = null, multiSyncResult = null)
        }
    }

    fun acceptQr(raw: String, workspaceId: Long, workspaceName: String) {
        if (operationJob?.isActive == true) return
        val payload = try { KintoneQrParser.parse(raw) } catch (e: KintoneException) {
            fail(e.code, KintoneFailureContext.QR); return
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
            } catch (e: KintoneException) { fail(e.code, KintoneFailureContext.CONNECTION) }
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
            } catch (e: KintoneException) { fail(e.code, KintoneFailureContext.CONNECTION) }
        }
    }

    fun reverify() = launchOperation(KintoneFailureContext.CONNECTION) {
        repository.reverify(mutable.value.workspaceId)
        val uuid = WorkspaceUuid.fromId(mutable.value.workspaceId)
        autoSyncStore.write(uuid, autoSyncStore.read(uuid).copy(requiresAttention = false))
        mutable.value = mutable.value.copy(operation = OperationState.Success(R.string.kintone_verified, eventId++), errorCode = null)
    }

    fun disconnect() = launchOperation(KintoneFailureContext.CONNECTION) {
        autoSyncScheduler.disable(mutable.value.workspaceId)
        repository.disconnect(mutable.value.workspaceId)
        mutable.value = mutable.value.copy(operation = OperationState.Success(R.string.kintone_disconnected, eventId++), pending = null, errorCode = null)
    }

    private fun legacySingleWorkspaceSync() = launchOperation(KintoneFailureContext.SYNC) {
        mutable.value = mutable.value.copy(message = "データ確認中")
        val records = repository.buildSyncRecordsForConnection(mutable.value.workspaceId)
        if (records.isEmpty()) {
            val uuid = WorkspaceUuid.fromId(mutable.value.workspaceId)
            autoSyncStore.write(uuid, autoSyncStore.read(uuid).copy(
                lastFinishedAt = System.currentTimeMillis(), status = KintoneSyncStatus.NO_TARGETS,
                targetCount = 0, successCount = 0, failureCount = 0, skippedCount = 0, unsentCount = 0,
                partiallyCompleted = false, lastErrorCategory = null, lastHttpStatus = null,
                lastKintoneErrorCode = null, lastUserMessage = null, failedAt = 0, requiresAttention = false,
                lastErrorPath = null, lastErrorDetail = null, lastFailedRecordIndex = null,
            ))
            mutable.value = mutable.value.copy(message = "同期する登録機器がありません", syncPreview = KintoneSyncPreview(0, 0, emptyList(), emptyList()), operation = OperationState.Idle, errorCode = null, failureContext = null)
            return@launchOperation
        }
        val hadPreview = mutable.value.syncPreview != null
        mutable.value = mutable.value.copy(message = "写真確認中")
        val preview = mutable.value.syncPreview ?: repository.previewSync(mutable.value.workspaceId).also {
            mutable.value = mutable.value.copy(syncPreview = it)
        }
        if (!hadPreview || preview.valid == 0) {
            if (preview.valid == 0) throw KintoneException(KintoneErrorCode.KINTONE_VALIDATION_FAILED)
            mutable.value = mutable.value.copy(operation = OperationState.Idle, message = null)
            return@launchOperation
        }
        val uuid = WorkspaceUuid.fromId(mutable.value.workspaceId)
        val mutex = KintoneSyncLock.tryAcquire(uuid)
        if (mutex == null) {
            mutable.value = mutable.value.copy(
                operation = OperationState.Idle,
                message = "自動同期が完了してから再試行してください",
            )
            return@launchOperation
        }
        val result = try {
            repository.sync(mutable.value.workspaceId, records.filter { it.deviceUuid.isNotBlank() }, includePhotos = true) { stage, current, total ->
                mutable.value = mutable.value.copy(message = stage, operation = OperationState.Running(R.string.kintone_verifying, progress = OperationProgress.Count(current, total.coerceAtLeast(1)), cancellable = true))
            }
        } finally {
            KintoneSyncLock.release(uuid, mutex)
        }
        mutable.value = mutable.value.copy(message = "結果確認中")
        val failure = result.batches.firstOrNull { it.error != null }
        autoSyncStore.write(uuid, autoSyncStore.read(uuid).copy(
            lastStartedAt = System.currentTimeMillis(), lastFinishedAt = System.currentTimeMillis(),
            status = when { result.failed == 0 -> KintoneSyncStatus.SUCCESS; result.succeeded > 0 -> KintoneSyncStatus.PARTIAL; else -> KintoneSyncStatus.FAILED },
            trigger = KintoneSyncTrigger.MANUAL, targetCount = result.total, successCount = result.succeeded,
            failureCount = result.failed, skippedCount = result.skipped, unsentCount = result.failed + result.skipped,
            partiallyCompleted = result.failed > 0 && result.succeeded > 0,
            lastErrorCategory = failure?.errorCategory?.name, lastHttpStatus = failure?.httpStatus,
            lastKintoneErrorCode = failure?.kintoneErrorCode, lastUserMessage = failure?.userMessage,
            failedAt = if (failure != null) System.currentTimeMillis() else 0, requiresAttention = failure != null,
        ))
        mutable.value = mutable.value.copy(message = "完了", syncResult = result, operation = OperationState.Success(R.string.kintone_connected, eventId++))
    }

    fun sync() = launchOperation(KintoneFailureContext.SYNC) {
        val selected = mutable.value.workspaces.filter { it.id in mutable.value.selectedWorkspaceIds }
        val results = mutableListOf<KintoneWorkspaceSyncResult>()
        selected.forEachIndexed { index, option ->
            mutable.value = mutable.value.copy(syncingWorkspaceIndex = index + 1, message = "同期中 ${index + 1}/${selected.size}ワークスペース")
            val uuid = WorkspaceUuid.fromId(option.id)
            if (!option.connected) {
                results += KintoneWorkspaceSyncResult(option.id, uuid, option.name, com.lazyapps.wifianalyzer.kintone.KintoneWorkspaceSyncStatus.NOT_CONNECTED)
                return@forEachIndexed
            }
            val records = try { repository.buildSyncRecordsForConnection(option.id) } catch (e: KintoneException) {
                results += KintoneWorkspaceSyncResult(option.id, uuid, option.name, com.lazyapps.wifianalyzer.kintone.KintoneWorkspaceSyncStatus.FAILED, safeError = e.userMessage)
                return@forEachIndexed
            }
            if (records.isEmpty()) {
                results += KintoneWorkspaceSyncResult(option.id, uuid, option.name, com.lazyapps.wifianalyzer.kintone.KintoneWorkspaceSyncStatus.NO_TARGETS)
                persistManualResult(uuid, null)
                return@forEachIndexed
            }
            val mutex = KintoneSyncLock.tryAcquire(uuid)
            if (mutex == null) {
                results += KintoneWorkspaceSyncResult(option.id, uuid, option.name, com.lazyapps.wifianalyzer.kintone.KintoneWorkspaceSyncStatus.FAILED, safeError = "別の同期処理が実行中です")
                return@forEachIndexed
            }
            try {
                val result = repository.sync(option.id, records, includePhotos = true) { stage, current, total ->
                    mutable.value = mutable.value.copy(message = "$stage（${index + 1}/${selected.size}ワークスペース）", operation = OperationState.Running(R.string.kintone_verifying, progress = OperationProgress.Count(current, total.coerceAtLeast(1)), cancellable = true))
                }
                val status = when { result.failed == 0 -> com.lazyapps.wifianalyzer.kintone.KintoneWorkspaceSyncStatus.SUCCESS; result.succeeded > 0 -> com.lazyapps.wifianalyzer.kintone.KintoneWorkspaceSyncStatus.PARTIAL; else -> com.lazyapps.wifianalyzer.kintone.KintoneWorkspaceSyncStatus.FAILED }
                results += KintoneWorkspaceSyncResult(option.id, uuid, option.name, status, result)
                persistManualResult(uuid, result)
            } catch (e: KintoneException) {
                results += KintoneWorkspaceSyncResult(option.id, uuid, option.name, com.lazyapps.wifianalyzer.kintone.KintoneWorkspaceSyncStatus.FAILED, safeError = e.userMessage)
            } finally { KintoneSyncLock.release(uuid, mutex) }
        }
        val overall = com.lazyapps.wifianalyzer.kintone.aggregateMultiSyncStatus(results)
        mutable.value = mutable.value.copy(message = null, multiSyncResult = KintoneMultiSyncResult(overall, results), operation = OperationState.Success(R.string.kintone_connected, eventId++))
    }

    private fun persistManualResult(uuid: String, result: KintoneSyncResult?) {
        val failure = result?.batches?.firstOrNull { it.error != null }
        autoSyncStore.write(uuid, autoSyncStore.read(uuid).copy(
            lastStartedAt = System.currentTimeMillis(), lastFinishedAt = System.currentTimeMillis(),
            status = when { result == null -> KintoneSyncStatus.NO_TARGETS; result.failed == 0 -> KintoneSyncStatus.SUCCESS; result.succeeded > 0 -> KintoneSyncStatus.PARTIAL; else -> KintoneSyncStatus.FAILED },
            trigger = KintoneSyncTrigger.MANUAL, targetCount = result?.total ?: 0, successCount = result?.succeeded ?: 0,
            failureCount = result?.failed ?: 0, skippedCount = result?.skipped ?: 0, unsentCount = (result?.failed ?: 0) + (result?.skipped ?: 0),
            partiallyCompleted = result != null && result.failed > 0 && result.succeeded > 0,
            lastErrorCategory = failure?.errorCategory?.name, lastHttpStatus = failure?.httpStatus,
            lastKintoneErrorCode = failure?.kintoneErrorCode, lastUserMessage = failure?.userMessage,
            failedAt = if (failure != null) System.currentTimeMillis() else 0, requiresAttention = failure != null,
        ))
    }

    fun setAutoSync(enabled: Boolean) {
        val state = mutable.value
        if (state.workspaceId <= 0 || state.connection == null) return
        if (enabled) { if (!state.canUseKintone) return; autoSyncScheduler.enable(state.workspaceId) }
        else autoSyncScheduler.disable(state.workspaceId)
        mutable.value = state.copy(autoSync = autoSyncStore.read(WorkspaceUuid.fromId(state.workspaceId)))
    }

    fun setPhotoAutoSync(enabled: Boolean) {
        val state = mutable.value
        if (state.workspaceId <= 0 || state.connection == null || !state.autoSync.enabled) return
        autoSyncScheduler.setPhotoEnabled(state.workspaceId, enabled)
        mutable.value = state.copy(autoSync = autoSyncStore.read(WorkspaceUuid.fromId(state.workspaceId)))
    }

    fun cancel() { operationJob?.cancel(); operationJob = null; mutable.value = mutable.value.copy(operation = OperationState.Idle, pending = null) }
    fun consumeEvent(id: Long) { val op = mutable.value.operation; if ((op as? OperationState.Success)?.eventId == id || (op as? OperationState.Failure)?.eventId == id) mutable.value = mutable.value.copy(operation = OperationState.Idle) }

    private fun launchOperation(context: KintoneFailureContext, block: suspend () -> Unit) {
        if (operationJob?.isActive == true) return
        operationJob = viewModelScope.launch {
            mutable.value = mutable.value.copy(operation = OperationState.Running(R.string.kintone_verifying, cancellable = true))
            try {
                block()
            } catch (e: KintoneException) {
                fail(e.code, context)
            } catch (e: CancellationException) {
                if (context == KintoneFailureContext.SYNC) {
                    val completed = mutable.value.multiSyncResult?.workspaces.orEmpty()
                    val completedIds = completed.mapTo(mutableSetOf()) { it.workspaceId }
                    val cancelled = mutable.value.workspaces.filter { it.id in mutable.value.selectedWorkspaceIds && it.id !in completedIds }.map {
                        KintoneWorkspaceSyncResult(it.id, WorkspaceUuid.fromId(it.id), it.name, com.lazyapps.wifianalyzer.kintone.KintoneWorkspaceSyncStatus.CANCELLED)
                    }
                    mutable.value = mutable.value.copy(
                        operation = OperationState.Idle,
                        message = null,
                        multiSyncResult = KintoneMultiSyncResult(com.lazyapps.wifianalyzer.kintone.KintoneMultiSyncStatus.CANCELLED, completed + cancelled),
                    )
                } else mutable.value = mutable.value.copy(operation = OperationState.Idle)
                throw e
            } catch (_: Exception) {
                fail(KintoneErrorCode.KINTONE_BATCH_FAILED, context)
            } finally {
                operationJob = null
            }
        }
    }

    private fun fail(code: KintoneErrorCode, context: KintoneFailureContext) {
        val category = when (code) {
            KintoneErrorCode.KINTONE_NETWORK_UNAVAILABLE, KintoneErrorCode.KINTONE_TIMEOUT, KintoneErrorCode.KINTONE_TLS_ERROR -> OperationErrorCategory.NETWORK_SCAN_FAILED
            KintoneErrorCode.KINTONE_CONNECTION_CANCELLED -> OperationErrorCategory.CANCELLED
            KintoneErrorCode.KINTONE_SECURE_STORAGE_FAILED -> OperationErrorCategory.DATABASE_FAILED
            else -> OperationErrorCategory.VALIDATION_FAILED
        }
        mutable.value = mutable.value.copy(
            errorCode = code,
            failureContext = context,
            message = null,
            operation = OperationState.Failure(OperationError(category, R.string.kintone_verifying, stableCode = code.name), eventId++),
            pending = null,
        )
    }
}
