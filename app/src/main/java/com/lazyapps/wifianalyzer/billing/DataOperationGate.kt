package com.lazyapps.wifianalyzer.billing

enum class RestrictedDataOperation { CSV, PDF, BACKUP, RESTORE }

class DataOperationGate(private val access: () -> FeatureAccessPolicy) {
    fun <T> run(operation: RestrictedDataOperation, action: () -> T): Result<T> = runCatching {
        val allowed = when (operation) {
            RestrictedDataOperation.CSV -> access().canExportCsv
            RestrictedDataOperation.PDF -> access().canExportPdf
            RestrictedDataOperation.BACKUP -> access().canBackup
            RestrictedDataOperation.RESTORE -> access().canRestore
        }
        check(allowed) { "${operation.name}_REQUIRES_PRO" }
        action()
    }
}
