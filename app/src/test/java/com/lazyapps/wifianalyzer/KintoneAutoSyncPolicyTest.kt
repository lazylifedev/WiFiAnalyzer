package com.lazyapps.wifianalyzer

import com.lazyapps.wifianalyzer.kintone.KintoneAutoSyncState
import com.lazyapps.wifianalyzer.kintone.KintoneSyncStatus
import com.lazyapps.wifianalyzer.kintone.KintoneSyncTrigger
import com.lazyapps.wifianalyzer.kintone.WorkspaceUuid
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
    @Test fun allSyncTriggersRemainDistinct() = assertEquals(4, KintoneSyncTrigger.entries.distinct().size)
    @Test fun manualTriggerIsPersistableByName() = assertEquals(KintoneSyncTrigger.MANUAL, KintoneSyncTrigger.valueOf("MANUAL"))
    @Test fun partialStatusIsPersistableByName() = assertEquals(KintoneSyncStatus.PARTIAL, KintoneSyncStatus.valueOf("PARTIAL"))
}
