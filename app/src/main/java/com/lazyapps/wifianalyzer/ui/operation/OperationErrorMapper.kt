package com.lazyapps.wifianalyzer.ui.operation

import android.database.sqlite.SQLiteConstraintException
import com.lazyapps.wifianalyzer.R
import com.lazyapps.wifianalyzer.data.backup.BackupException
import com.lazyapps.wifianalyzer.importcsv.CsvImportException
import java.io.FileNotFoundException
import java.io.IOException

object OperationErrorMapper {
    fun classify(error: Throwable): OperationErrorCategory = when (error) {
        is kotlinx.coroutines.CancellationException -> OperationErrorCategory.CANCELLED
        is SecurityException -> OperationErrorCategory.PERMISSION_DENIED
        is FileNotFoundException -> OperationErrorCategory.FILE_NOT_FOUND
        is SQLiteConstraintException -> OperationErrorCategory.DATA_CONFLICT
        is CsvImportException -> OperationErrorCategory.INVALID_CSV
        is BackupException -> when (error.code) {
            BackupException.Code.CHECKSUM_MISMATCH -> OperationErrorCategory.CHECKSUM_MISMATCH
            BackupException.Code.UNSUPPORTED_FORMAT -> OperationErrorCategory.UNSUPPORTED_FORMAT
            BackupException.Code.PHOTO_WRITE_FAILED -> OperationErrorCategory.PHOTO_WRITE_FAILED
            BackupException.Code.DUPLICATE_BSSID -> OperationErrorCategory.DATA_CONFLICT
            BackupException.Code.MISSING_FILE -> OperationErrorCategory.FILE_NOT_FOUND
            BackupException.Code.INVALID_ZIP, BackupException.Code.MISSING_MANIFEST,
            BackupException.Code.INVALID_JSON, BackupException.Code.UNSAFE_PATH,
            BackupException.Code.LIMIT_EXCEEDED, BackupException.Code.INVALID_REFERENCE ->
                OperationErrorCategory.INVALID_BACKUP
        }
        is IOException -> if (isStorageFull(error)) OperationErrorCategory.STORAGE_FULL else OperationErrorCategory.FILE_READ_FAILED
        is IllegalArgumentException -> OperationErrorCategory.VALIDATION_FAILED
        else -> OperationErrorCategory.UNKNOWN
    }

    fun map(error: Throwable, operationRes: Int): OperationError =
        OperationError(classify(error), operationRes)

    fun scan(category: OperationErrorCategory): OperationError =
        OperationError(category, R.string.operation_wifi_scan)

    private fun isStorageFull(error: IOException): Boolean {
        val text = error.message.orEmpty().lowercase()
        return "enospc" in text || "no space" in text || "disk full" in text
    }
}

fun OperationState.isRunning(): Boolean = this is OperationState.Running
