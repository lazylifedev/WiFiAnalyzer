package com.lazyapps.wifianalyzer.ui.operation

import androidx.annotation.StringRes
import com.lazyapps.wifianalyzer.R

sealed interface OperationProgress {
    data object Indeterminate : OperationProgress
    data class Count(val current: Int, val total: Int) : OperationProgress {
        init {
            require(current >= 0)
            require(total > 0)
            require(current <= total)
        }
        val fraction: Float get() = current.toFloat() / total
    }
    data class Percent(val value: Int) : OperationProgress {
        init { require(value in 0..100) }
        val fraction: Float get() = value / 100f
    }
    data class Stage(@StringRes val messageRes: Int) : OperationProgress
}

data class OperationAction(@StringRes val labelRes: Int, val id: String)

sealed interface OperationState {
    data object Idle : OperationState
    data class Running(
        @StringRes val titleRes: Int,
        @StringRes val messageRes: Int? = null,
        val progress: OperationProgress = OperationProgress.Indeterminate,
        val cancellable: Boolean = false,
    ) : OperationState
    data class Success(
        @StringRes val messageRes: Int,
        val eventId: Long,
        val action: OperationAction? = null,
    ) : OperationState
    data class Failure(
        val error: OperationError,
        val eventId: Long,
        val action: OperationAction? = null,
    ) : OperationState
}

enum class OperationErrorCategory(
    val code: String,
    @StringRes val titleRes: Int,
    @StringRes val messageRes: Int,
    val retryable: Boolean,
) {
    PERMISSION_DENIED("PER-001", R.string.error_permission_denied_title, R.string.error_permission_denied, true),
    PERMISSION_PERMANENTLY_DENIED("PER-002", R.string.error_permission_permanent_title, R.string.error_permission_permanent, false),
    LOCATION_SERVICE_DISABLED("DEV-001", R.string.error_location_disabled_title, R.string.error_location_disabled, true),
    WIFI_DISABLED("DEV-002", R.string.error_wifi_disabled_title, R.string.error_wifi_disabled, true),
    SCAN_THROTTLED("SCN-001", R.string.error_scan_throttled_title, R.string.error_scan_throttled, true),
    NETWORK_SCAN_FAILED("SCN-002", R.string.error_scan_failed_title, R.string.error_scan_failed, true),
    FILE_NOT_FOUND("FIL-001", R.string.error_file_not_found_title, R.string.error_file_not_found, true),
    FILE_READ_FAILED("FIL-002", R.string.error_file_read_title, R.string.error_file_read, true),
    FILE_WRITE_FAILED("FIL-003", R.string.error_file_write_title, R.string.error_file_write, true),
    STORAGE_FULL("FIL-004", R.string.error_storage_full_title, R.string.error_storage_full, true),
    INVALID_CSV("CSV-001", R.string.error_invalid_csv_title, R.string.error_invalid_csv, true),
    INVALID_BACKUP("BAK-001", R.string.error_invalid_backup_title, R.string.error_invalid_backup, false),
    UNSUPPORTED_FORMAT("FIL-005", R.string.error_unsupported_format_title, R.string.error_unsupported_format, false),
    CHECKSUM_MISMATCH("BAK-002", R.string.error_checksum_title, R.string.error_checksum, false),
    DATA_CONFLICT("DAT-001", R.string.error_data_conflict_title, R.string.error_data_conflict, false),
    VALIDATION_FAILED("DAT-002", R.string.error_validation_title, R.string.error_validation, true),
    DATABASE_FAILED("DAT-003", R.string.error_database_title, R.string.error_database, true),
    PHOTO_READ_FAILED("PHT-001", R.string.error_photo_read_title, R.string.error_photo_read, true),
    PHOTO_WRITE_FAILED("PHT-002", R.string.error_photo_write_title, R.string.error_photo_write, true),
    CAMERA_FAILED("OCR-001", R.string.error_camera_title, R.string.error_camera, true),
    OCR_FAILED("OCR-002", R.string.error_ocr_title, R.string.error_ocr, true),
    SHARE_UNAVAILABLE("OUT-001", R.string.error_share_title, R.string.error_share, true),
    PRINT_FAILED("OUT-002", R.string.error_print_title, R.string.error_print, true),
    CANCELLED("OPR-001", R.string.error_cancelled_title, R.string.error_cancelled, true),
    UNKNOWN("OPR-999", R.string.error_unknown_title, R.string.error_unknown, true),
}

data class OperationError(
    val category: OperationErrorCategory,
    @StringRes val operationRes: Int,
    val occurredAtMillis: Long = System.currentTimeMillis(),
    val stableCode: String? = null,
) {
    val detailCode: String get() = stableCode ?: category.code
    val retryable: Boolean get() = category.retryable
}
