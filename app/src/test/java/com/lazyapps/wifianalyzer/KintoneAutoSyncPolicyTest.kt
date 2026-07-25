package com.lazyapps.wifianalyzer

import com.lazyapps.wifianalyzer.kintone.KintoneAutoSyncState
import com.lazyapps.wifianalyzer.kintone.KintoneSyncStatus
import com.lazyapps.wifianalyzer.kintone.KintoneSyncTrigger
import com.lazyapps.wifianalyzer.kintone.WorkspaceUuid
import com.lazyapps.wifianalyzer.kintone.KintoneRetryPolicy
import com.lazyapps.wifianalyzer.kintone.KintoneErrorCode
import com.lazyapps.wifianalyzer.kintone.KintoneSyncLock
import com.lazyapps.wifianalyzer.kintone.KintoneWorkNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KintoneAutoSyncPolicyTest {
    @Test fun autoSyncDefaultsOff() = assertFalse(KintoneAutoSyncState().enabled)
    @Test fun autoSyncDefaultsToNeverRun() = assertEquals(KintoneSyncStatus.NEVER, KintoneAutoSyncState().status)
    @Test fun autoSyncDefaultsWithoutTrigger() = assertNull(KintoneAutoSyncState().trigger)
    @Test fun workspaceUuidIsStable() = assertEquals(WorkspaceUuid.fromId(42), WorkspaceUuid.fromId(42))
    @Test fun workspaceUuidIsWorkspaceSpecific() = assertNotEquals(WorkspaceUuid.fromId(41), WorkspaceUuid.fromId(42))
    @Test fun oneTimeAndPeriodicUseDistinctWorkspaceScopedNames() {
        val uuid = WorkspaceUuid.fromId(42)
        assertNotEquals(KintoneWorkNames.oneTime(uuid), KintoneWorkNames.periodic(uuid))
        assertNotEquals(KintoneWorkNames.oneTime(uuid), KintoneWorkNames.oneTime(WorkspaceUuid.fromId(41)))
    }
    @Test fun allSyncTriggersRemainDistinct() = assertEquals(4, KintoneSyncTrigger.entries.distinct().size)
    @Test fun manualTriggerIsPersistableByName() = assertEquals(KintoneSyncTrigger.MANUAL, KintoneSyncTrigger.valueOf("MANUAL"))
    @Test fun partialStatusIsPersistableByName() = assertEquals(KintoneSyncStatus.PARTIAL, KintoneSyncStatus.valueOf("PARTIAL"))
    @Test fun noTargetsIsAFirstClassNonFailureStatus() = assertEquals(KintoneSyncStatus.NO_TARGETS, KintoneSyncStatus.valueOf("NO_TARGETS"))
    @Test fun noTargetsNeverRetries() = assertFalse(KintoneRetryPolicy.shouldRetry(KintoneErrorCode.KINTONE_NO_DEVICES))
    @Test fun onlyTransientFailuresRetry() {
        listOf(KintoneErrorCode.KINTONE_NETWORK_UNAVAILABLE, KintoneErrorCode.KINTONE_TIMEOUT, KintoneErrorCode.KINTONE_RATE_LIMITED, KintoneErrorCode.KINTONE_SERVER_ERROR).forEach { assertEquals(true, KintoneRetryPolicy.shouldRetry(it)) }
        listOf(KintoneErrorCode.KINTONE_AUTH_FAILED, KintoneErrorCode.KINTONE_PERMISSION_DENIED, KintoneErrorCode.KINTONE_SCHEMA_MISMATCH, KintoneErrorCode.KINTONE_BATCH_FAILED).forEach { assertFalse(KintoneRetryPolicy.shouldRetry(it)) }
    }

    @Test fun overlappingOneTimeAndPeriodicRunsOnlyOneHttpSection() {
        val uuid = WorkspaceUuid.fromId(42)
        var httpCalls = 0
        val first = KintoneSyncLock.tryAcquire(uuid)!!
        try {
            httpCalls++
            assertNull(KintoneSyncLock.tryAcquire(uuid))
        } finally {
            KintoneSyncLock.release(uuid, first)
        }
        assertEquals(1, httpCalls)
        val next = KintoneSyncLock.tryAcquire(uuid)!!
        KintoneSyncLock.release(uuid, next)
    }

    @Test fun differentWorkspacesHaveIndependentSyncSlots() {
        val firstUuid = WorkspaceUuid.fromId(41)
        val secondUuid = WorkspaceUuid.fromId(42)
        val first = KintoneSyncLock.tryAcquire(firstUuid)!!
        val second = KintoneSyncLock.tryAcquire(secondUuid)!!
        KintoneSyncLock.release(secondUuid, second)
        KintoneSyncLock.release(firstUuid, first)
    }
}
