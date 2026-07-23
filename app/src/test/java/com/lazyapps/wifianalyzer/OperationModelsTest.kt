package com.lazyapps.wifianalyzer

import android.database.sqlite.SQLiteConstraintException
import com.lazyapps.wifianalyzer.ui.operation.OperationErrorCategory
import com.lazyapps.wifianalyzer.ui.operation.OperationErrorMapper
import com.lazyapps.wifianalyzer.ui.operation.OperationProgress
import com.lazyapps.wifianalyzer.ui.operation.OperationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException

class OperationModelsTest {
    @Test fun countProgressUsesRealWork() {
        assertEquals(0.5f, OperationProgress.Count(12, 24).fraction)
    }

    @Test(expected = IllegalArgumentException::class)
    fun countProgressRejectsInventedRange() { OperationProgress.Count(2, 1) }

    @Test fun runningCarriesCancellationPolicy() {
        val cancellable = OperationState.Running(R.string.operation_backup, cancellable = true)
        val protected = OperationState.Running(R.string.operation_restore, cancellable = false)
        assertTrue(cancellable.cancellable)
        assertFalse(protected.cancellable)
    }

    @Test fun exceptionsMapToStableSafeCategories() {
        assertEquals(OperationErrorCategory.PERMISSION_DENIED, OperationErrorMapper.classify(SecurityException("secret")))
        assertEquals(OperationErrorCategory.FILE_NOT_FOUND, OperationErrorMapper.classify(FileNotFoundException("/private/path")))
        assertEquals(OperationErrorCategory.DATA_CONFLICT, OperationErrorMapper.classify(SQLiteConstraintException("bssid")))
        assertEquals(OperationErrorCategory.FILE_READ_FAILED, OperationErrorMapper.classify(IOException("private uri")))
    }

    @Test fun detailNeverContainsSourceExceptionText() {
        val mapped = OperationErrorMapper.map(IOException("ssid and /absolute/path"), R.string.operation_csv_import)
        assertEquals("FIL-002", mapped.detailCode)
    }

    @Test fun resultEventsHaveStableDistinctIds() {
        val first = OperationState.Success(R.string.backup_created, eventId = 41)
        val second = OperationState.Success(R.string.backup_created, eventId = 42)
        assertFalse(first.eventId == second.eventId)
    }
}
