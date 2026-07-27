package com.lazyapps.wifianalyzer

import com.lazyapps.wifianalyzer.kintone.KintoneMultiSyncStatus
import com.lazyapps.wifianalyzer.kintone.KintoneSyncResult
import com.lazyapps.wifianalyzer.kintone.KintoneWorkspaceSyncResult
import com.lazyapps.wifianalyzer.kintone.KintoneWorkspaceSyncStatus
import com.lazyapps.wifianalyzer.kintone.WorkspaceUuid
import com.lazyapps.wifianalyzer.kintone.aggregateMultiSyncStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class KintoneMultiWorkspaceSyncTest {
    @Test fun twoSuccessfulWorkspacesAggregateToSuccess() = assertEquals(
        KintoneMultiSyncStatus.SUCCESS,
        aggregateMultiSyncStatus(listOf(item(1, KintoneWorkspaceSyncStatus.SUCCESS), item(2, KintoneWorkspaceSyncStatus.SUCCESS))),
    )

    @Test fun failureDoesNotHideEarlierSuccess() = assertEquals(
        KintoneMultiSyncStatus.PARTIAL,
        aggregateMultiSyncStatus(listOf(item(1, KintoneWorkspaceSyncStatus.SUCCESS), item(2, KintoneWorkspaceSyncStatus.FAILED))),
    )

    @Test fun disconnectedWorkspaceAndSuccessAggregateToPartial() = assertEquals(
        KintoneMultiSyncStatus.PARTIAL,
        aggregateMultiSyncStatus(listOf(item(1, KintoneWorkspaceSyncStatus.NOT_CONNECTED), item(2, KintoneWorkspaceSyncStatus.SUCCESS))),
    )

    @Test fun allNoTargetsIsNotFailure() = assertEquals(
        KintoneMultiSyncStatus.NO_TARGETS,
        aggregateMultiSyncStatus(listOf(item(1, KintoneWorkspaceSyncStatus.NO_TARGETS), item(2, KintoneWorkspaceSyncStatus.NO_TARGETS))),
    )

    @Test fun cancellationTakesPrecedence() = assertEquals(
        KintoneMultiSyncStatus.CANCELLED,
        aggregateMultiSyncStatus(listOf(item(1, KintoneWorkspaceSyncStatus.SUCCESS), item(2, KintoneWorkspaceSyncStatus.CANCELLED))),
    )

    private fun item(id: Long, status: KintoneWorkspaceSyncStatus) = KintoneWorkspaceSyncResult(
        id, WorkspaceUuid.fromId(id), "workspace-$id", status,
        result = if (status == KintoneWorkspaceSyncStatus.SUCCESS) KintoneSyncResult(1, 1, 0, 0, emptyList()) else null,
    )
}
