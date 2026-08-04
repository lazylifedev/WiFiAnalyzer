package com.lazyapps.wifianalyzer.ui.backup

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lazyapps.wifianalyzer.R
import com.lazyapps.wifianalyzer.billing.FeatureAccessPolicy
import com.lazyapps.wifianalyzer.data.backup.BackupExportService
import com.lazyapps.wifianalyzer.data.backup.BackupHistory
import com.lazyapps.wifianalyzer.data.backup.BackupHistoryRepository
import com.lazyapps.wifianalyzer.data.backup.BackupImportService
import com.lazyapps.wifianalyzer.data.backup.BackupPreview
import com.lazyapps.wifianalyzer.data.backup.BackupProgressStage
import com.lazyapps.wifianalyzer.data.backup.BackupScope
import com.lazyapps.wifianalyzer.data.backup.RestoreMode
import com.lazyapps.wifianalyzer.data.backup.RestoreRepository
import com.lazyapps.wifianalyzer.data.registry.WifiAnalyzerDatabase
import com.lazyapps.wifianalyzer.data.registry.WorkspaceRepository
import com.lazyapps.wifianalyzer.ui.operation.OperationErrorMapper
import com.lazyapps.wifianalyzer.ui.operation.OperationProgress
import com.lazyapps.wifianalyzer.ui.operation.OperationState
import com.lazyapps.wifianalyzer.ui.operation.ExternalDataOperationCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BackupUiState(
    val operation: OperationState = OperationState.Idle,
    val preview: BackupPreview? = null,
    val restoredWorkspaceId: Long? = null,
    val history: BackupHistory = BackupHistory(),
    val message: String? = null,
    val error: String? = null,
) {
    val busy: Boolean get() = operation is OperationState.Running
    val stage: String get() = ""
    val current: Int get() = (operation as? OperationState.Running)?.progress.let { (it as? OperationProgress.Count)?.current ?: 0 }
    val total: Int get() = (operation as? OperationState.Running)?.progress.let { (it as? OperationProgress.Count)?.total ?: 0 }
}

class BackupViewModel(application: Application) : AndroidViewModel(application) {
    @Volatile private var access = FeatureAccessPolicy.from(com.lazyapps.wifianalyzer.billing.ProEntitlementState.Free)
    private val operationCoordinator = ExternalDataOperationCoordinator(access = { access })
    fun setAccess(value: FeatureAccessPolicy) { access = value }
    fun canBackup() = access.canBackup
    fun canRestore() = access.canRestore
    private val database = WifiAnalyzerDatabase.get(application)
    private val exporter = BackupExportService(application, database)
    private val importer = BackupImportService(application)
    private val restorer = RestoreRepository(application, database)
    private val workspaces = WorkspaceRepository(application, database)
    private val histories = BackupHistoryRepository(application)
    private val mutable = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = mutable.asStateFlow()
    private var job: Job? = null
    private var nextEventId = 1L

    init {
        viewModelScope.launch {
            histories.history.collect { mutable.value = mutable.value.copy(history = it) }
        }
    }

    fun export(uri: Uri, workspaceId: Long?) = runTask(R.string.operation_backup, cancellable = true) {
        operationCoordinator.authorizeBackup().getOrThrow()
        val manifest = exporter.export(
            workspaceId?.let { BackupScope.Workspace(it) } ?: BackupScope.All,
            uri,
        ) { stage, current, total ->
            mutable.value = mutable.value.copy(
                operation = OperationState.Running(
                    R.string.operation_backup,
                    stage.messageResource(),
                    if (total > 0) OperationProgress.Count(current.coerceAtMost(total), total) else OperationProgress.Indeterminate,
                    cancellable = true,
                ),
            )
        }
        histories.record(manifest, true)
        success(R.string.backup_created)
        mutable.value = mutable.value.copy(preview = null)
    }

    fun inspect(uri: Uri) = runTask(R.string.operation_restore, cancellable = true) {
        operationCoordinator.authorizeRestore().getOrThrow()
        running(R.string.operation_restore, R.string.restore_stage_verify, cancellable = true)
        val preview = importer.inspect(uri) { stage, current, total -> updateProgress(R.string.operation_restore, stage, current, total, true) }
        mutable.value = mutable.value.copy(preview = preview)
        success(R.string.backup_verified)
    }

    fun restore(mode: RestoreMode) {
        if (!access.canRestore) return
        val preview = mutable.value.preview ?: return
        runTask(R.string.operation_restore, cancellable = false) {
            operationCoordinator.authorizeRestore().getOrThrow()
            running(R.string.operation_restore, R.string.restore_stage_database, cancellable = false)
            val result = restorer.restore(preview, mode) { stage, current, total -> updateProgress(R.string.operation_restore, stage, current, total, false) }
            if (mode == RestoreMode.REPLACE) result.workspaceIds.firstOrNull()?.let { workspaces.select(it) }
            importer.discard(preview)
            mutable.value = mutable.value.copy(preview = null, restoredWorkspaceId = result.workspaceIds.firstOrNull())
            success(R.string.restore_completed)
        }
    }

    fun cancel() {
        val running = mutable.value.operation as? OperationState.Running ?: return
        if (!running.cancellable) return
        job?.cancel()
    }

    fun consumeEvent(eventId: Long) {
        val current = mutable.value.operation
        val currentId = when (current) {
            is OperationState.Success -> current.eventId
            is OperationState.Failure -> current.eventId
            else -> null
        }
        if (currentId == eventId) mutable.value = mutable.value.copy(operation = OperationState.Idle)
    }

    private fun runTask(operationRes: Int, cancellable: Boolean, block: suspend () -> Unit) {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            running(operationRes, null, cancellable)
            try {
                block()
            } catch (_: CancellationException) {
                mutable.value.preview?.let(importer::discard)
                failure(OperationErrorMapper.map(CancellationException(), operationRes))
            } catch (error: Exception) {
                failure(OperationErrorMapper.map(error, operationRes))
            }
        }
    }

    private fun running(title: Int, message: Int?, cancellable: Boolean) {
        mutable.value = mutable.value.copy(
            operation = OperationState.Running(title, message, OperationProgress.Indeterminate, cancellable),
            message = null,
            error = null,
        )
    }

    private fun updateProgress(title: Int, stage: BackupProgressStage, current: Int, total: Int, cancellable: Boolean) {
        mutable.value = mutable.value.copy(
            operation = OperationState.Running(
                title,
                stage.messageResource(),
                if (total > 0) OperationProgress.Count(current.coerceAtMost(total), total) else OperationProgress.Indeterminate,
                cancellable,
            ),
        )
    }

    private fun success(message: Int) {
        mutable.value = mutable.value.copy(
            operation = OperationState.Success(message, nextEventId++),
            message = getApplication<Application>().getString(message),
            error = null,
        )
    }

    private fun failure(error: com.lazyapps.wifianalyzer.ui.operation.OperationError) {
        mutable.value = mutable.value.copy(
            operation = OperationState.Failure(error, nextEventId++),
            message = null,
            error = getApplication<Application>().getString(error.category.messageRes),
        )
    }
}

private fun BackupProgressStage.messageResource(): Int = when (this) {
    BackupProgressStage.PREPARING -> R.string.backup_stage_preparing
    BackupProgressStage.EXPORTING_DATABASE -> R.string.backup_stage_database
    BackupProgressStage.COPYING_PHOTOS -> R.string.backup_stage_photos
    BackupProgressStage.CREATING_ARCHIVE -> R.string.backup_stage_archive
    BackupProgressStage.VERIFYING -> R.string.restore_stage_verify
    BackupProgressStage.RESTORING_DATABASE -> R.string.restore_stage_database
    BackupProgressStage.RESTORING_PHOTOS -> R.string.restore_stage_photos
    BackupProgressStage.COMPLETED -> R.string.processing
    BackupProgressStage.FAILED -> R.string.processing
}
