package com.lazyapps.wifianalyzer.kintone

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lazyapps.wifianalyzer.data.registry.WifiAnalyzerDatabase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class KintoneSyncTrigger { MANUAL, AUTO_CHANGE, AUTO_PERIODIC, AUTO_ENABLED }
enum class KintoneSyncStatus { NEVER, WAITING, RUNNING, SUCCESS, PARTIAL, FAILED }
object KintoneSyncLock { val mutex = Mutex() }

data class KintoneAutoSyncState(
    val enabled: Boolean = false,
    val lastRequestedAt: Long = 0,
    val lastStartedAt: Long = 0,
    val lastFinishedAt: Long = 0,
    val status: KintoneSyncStatus = KintoneSyncStatus.NEVER,
    val trigger: KintoneSyncTrigger? = null,
    val targetCount: Int = 0,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val skippedCount: Int = 0,
    val unsentCount: Int = 0,
    val cancelled: Boolean = false,
    val partiallyCompleted: Boolean = false,
    val lastErrorCategory: String? = null,
)

object WorkspaceUuid {
    fun fromId(workspaceId: Long): String = UUID.nameUUIDFromBytes("wifi-analyzer-workspace:$workspaceId".toByteArray()).toString()
}

class KintoneAutoSyncStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("kintone_auto_sync", Context.MODE_PRIVATE)
    private fun prefix(uuid: String) = "$uuid."
    fun read(uuid: String): KintoneAutoSyncState {
        val p = prefix(uuid)
        return KintoneAutoSyncState(
            enabled = prefs.getBoolean(p + "enabled", false),
            lastRequestedAt = prefs.getLong(p + "requested", 0), lastStartedAt = prefs.getLong(p + "started", 0),
            lastFinishedAt = prefs.getLong(p + "finished", 0),
            status = runCatching { KintoneSyncStatus.valueOf(prefs.getString(p + "status", "NEVER")!!) }.getOrDefault(KintoneSyncStatus.NEVER),
            trigger = prefs.getString(p + "trigger", null)?.let { runCatching { KintoneSyncTrigger.valueOf(it) }.getOrNull() },
            targetCount = prefs.getInt(p + "target", 0), successCount = prefs.getInt(p + "success", 0),
            failureCount = prefs.getInt(p + "failure", 0), skippedCount = prefs.getInt(p + "skipped", 0),
            unsentCount = prefs.getInt(p + "unsent", 0), cancelled = prefs.getBoolean(p + "cancelled", false),
            partiallyCompleted = prefs.getBoolean(p + "partial", false), lastErrorCategory = prefs.getString(p + "error", null),
        )
    }
    fun write(uuid: String, state: KintoneAutoSyncState) {
        val p = prefix(uuid)
        prefs.edit().putBoolean(p + "enabled", state.enabled).putLong(p + "requested", state.lastRequestedAt)
            .putLong(p + "started", state.lastStartedAt).putLong(p + "finished", state.lastFinishedAt)
            .putString(p + "status", state.status.name).putString(p + "trigger", state.trigger?.name)
            .putInt(p + "target", state.targetCount).putInt(p + "success", state.successCount)
            .putInt(p + "failure", state.failureCount).putInt(p + "skipped", state.skippedCount)
            .putInt(p + "unsent", state.unsentCount).putBoolean(p + "cancelled", state.cancelled)
            .putBoolean(p + "partial", state.partiallyCompleted).putString(p + "error", state.lastErrorCategory).apply()
    }
    fun remove(uuid: String) { val p = prefix(uuid); prefs.edit().also { e -> prefs.all.keys.filter { it.startsWith(p) }.forEach(e::remove) }.apply() }
}

class KintoneAutoSyncScheduler(private val context: Context) {
    private val workManager = WorkManager.getInstance(context.applicationContext)
    private val store = KintoneAutoSyncStore(context)
    private fun oneTimeName(uuid: String) = "kintone-auto-sync-$uuid"
    private fun periodicName(uuid: String) = "kintone-auto-sync-periodic-$uuid"
    private fun input(workspaceId: Long, trigger: KintoneSyncTrigger) = Data.Builder().putLong(KEY_WORKSPACE_ID, workspaceId).putString(KEY_TRIGGER, trigger.name).build()
    private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun enable(workspaceId: Long) {
        val uuid = WorkspaceUuid.fromId(workspaceId)
        store.write(uuid, store.read(uuid).copy(enabled = true))
        enqueue(workspaceId, KintoneSyncTrigger.AUTO_ENABLED, 0)
        val periodic = PeriodicWorkRequestBuilder<KintoneAutoSyncWorker>(15, TimeUnit.MINUTES).setConstraints(constraints)
            .setInputData(input(workspaceId, KintoneSyncTrigger.AUTO_PERIODIC)).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        workManager.enqueueUniquePeriodicWork(periodicName(uuid), ExistingPeriodicWorkPolicy.UPDATE, periodic)
    }
    fun requestChange(workspaceId: Long) { if (store.read(WorkspaceUuid.fromId(workspaceId)).enabled) enqueue(workspaceId, KintoneSyncTrigger.AUTO_CHANGE, 8) }
    fun disable(workspaceId: Long) {
        val uuid = WorkspaceUuid.fromId(workspaceId); store.write(uuid, store.read(uuid).copy(enabled = false, cancelled = true))
        workManager.cancelUniqueWork(oneTimeName(uuid)); workManager.cancelUniqueWork(periodicName(uuid))
    }
    fun remove(workspaceId: Long) { disable(workspaceId); store.remove(WorkspaceUuid.fromId(workspaceId)) }
    private fun enqueue(workspaceId: Long, trigger: KintoneSyncTrigger, delaySeconds: Long) {
        val uuid = WorkspaceUuid.fromId(workspaceId)
        store.write(uuid, store.read(uuid).copy(lastRequestedAt = System.currentTimeMillis(), status = KintoneSyncStatus.WAITING, trigger = trigger, cancelled = false))
        val request = OneTimeWorkRequestBuilder<KintoneAutoSyncWorker>().setConstraints(constraints).setInputData(input(workspaceId, trigger))
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        workManager.enqueueUniqueWork(oneTimeName(uuid), ExistingWorkPolicy.REPLACE, request)
    }
    companion object { const val KEY_WORKSPACE_ID = "workspace_id"; const val KEY_TRIGGER = "sync_trigger" }
}

class KintoneAutoSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val workspaceId = inputData.getLong(KintoneAutoSyncScheduler.KEY_WORKSPACE_ID, 0)
        if (workspaceId <= 0) return Result.failure()
        val uuid = WorkspaceUuid.fromId(workspaceId); val store = KintoneAutoSyncStore(applicationContext)
        if (!store.read(uuid).enabled) return Result.success()
        val trigger = inputData.getString(KintoneAutoSyncScheduler.KEY_TRIGGER)?.let { runCatching { KintoneSyncTrigger.valueOf(it) }.getOrNull() } ?: KintoneSyncTrigger.AUTO_CHANGE
        return KintoneSyncLock.mutex.withLock {
            val repository = KintoneRepository(WifiAnalyzerDatabase.get(applicationContext))
            store.write(uuid, store.read(uuid).copy(lastStartedAt = System.currentTimeMillis(), status = KintoneSyncStatus.RUNNING, trigger = trigger))
            try {
                val records = repository.buildSyncRecords(workspaceId)
                val result = if (records.isEmpty()) KintoneSyncResult(0, 0, 0, 0, emptyList()) else repository.sync(workspaceId, records)
                val partial = result.failed > 0 && result.succeeded > 0
                store.write(uuid, store.read(uuid).copy(lastFinishedAt = System.currentTimeMillis(), status = when { partial -> KintoneSyncStatus.PARTIAL; result.failed > 0 -> KintoneSyncStatus.FAILED; else -> KintoneSyncStatus.SUCCESS }, targetCount = result.total, successCount = result.succeeded, failureCount = result.failed, skippedCount = result.skipped, unsentCount = result.failed + result.skipped, partiallyCompleted = partial, lastErrorCategory = result.batches.firstOrNull { it.error != null }?.error?.name))
                if (result.batches.any { it.error in retryable }) Result.retry() else if (result.failed > 0) Result.failure() else Result.success()
            } catch (e: KintoneException) {
                store.write(uuid, store.read(uuid).copy(lastFinishedAt = System.currentTimeMillis(), status = KintoneSyncStatus.FAILED, lastErrorCategory = e.code.name))
                if (e.code in retryable) Result.retry() else Result.failure()
            }
        }
    }
    companion object {
        private val retryable = setOf(KintoneErrorCode.KINTONE_NETWORK_UNAVAILABLE, KintoneErrorCode.KINTONE_TIMEOUT, KintoneErrorCode.KINTONE_RATE_LIMITED, KintoneErrorCode.KINTONE_SERVER_ERROR)
    }
}
