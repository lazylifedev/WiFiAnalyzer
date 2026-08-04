package com.lazyapps.wifianalyzer.ui.operation

import android.net.Uri
import com.lazyapps.wifianalyzer.billing.FeatureAccessPolicy

interface ExternalDataOperations {
    fun launchCreateDocument(name: String)
    fun launchOpenDocument()
    fun launchShareIntent(file: java.io.File)
    fun openInputStream(uri: Uri): java.io.InputStream?
    fun openOutputStream(uri: Uri): java.io.OutputStream?
    fun createTemporaryFile(name: String): java.io.File
}

class ExternalDataOperationCoordinator(private val access: () -> FeatureAccessPolicy, private val external: ExternalDataOperations = NoOpExternalDataOperations) {
    fun authorizeCsv(): Result<Unit> = run(Feature.CSV) {}
    fun authorizePdf(): Result<Unit> = run(Feature.PDF) {}
    fun authorizeBackup(): Result<Unit> = run(Feature.BACKUP) {}
    fun authorizeRestore(): Result<Unit> = run(Feature.RESTORE) {}
    fun createCsv(name: String, uri: Uri? = null, write: ((java.io.OutputStream) -> Unit)? = null): Result<Unit> = run(Feature.CSV) {
        if (uri == null) external.launchCreateDocument(name) else write?.let { external.openOutputStream(uri)?.use(it) ?: error("OUTPUT_UNAVAILABLE") }
    }
    fun shareCsv(file: java.io.File): Result<Unit> = run(Feature.CSV) { external.launchShareIntent(file) }
    fun createPdf(uri: Uri? = null, write: ((java.io.OutputStream) -> Unit)? = null): Result<Unit> = run(Feature.PDF) {
        if (uri != null) write?.let { external.openOutputStream(uri)?.use(it) ?: error("OUTPUT_UNAVAILABLE") }
    }
    fun sharePdf(file: java.io.File): Result<Unit> = run(Feature.PDF) { external.launchShareIntent(file) }
    fun backup(name: String, uri: Uri? = null, write: ((java.io.OutputStream) -> Unit)? = null): Result<Unit> = run(Feature.BACKUP) {
        if (uri == null) external.launchCreateDocument(name) else write?.let { external.openOutputStream(uri)?.use(it) ?: error("OUTPUT_UNAVAILABLE") }
    }
    fun restorePicker(): Result<Unit> = run(Feature.RESTORE) { external.launchOpenDocument() }
    fun restoreUri(uri: Uri, read: ((java.io.InputStream) -> Unit)): Result<Unit> = run(Feature.RESTORE) {
        external.openInputStream(uri)?.use(read) ?: error("INPUT_UNAVAILABLE")
    }
    private enum class Feature { CSV, PDF, BACKUP, RESTORE }
    private fun <T> run(feature: Feature, action: () -> T): Result<T> = runCatching {
        check(allowed(feature)) { "${feature.name}_REQUIRES_PRO" }
        action()
    }
    private fun allowed(feature: Feature) = when (feature) {
        Feature.CSV -> access().canExportCsv
        Feature.PDF -> access().canExportPdf
        Feature.BACKUP -> access().canBackup
        Feature.RESTORE -> access().canRestore
    }
}

private object NoOpExternalDataOperations : ExternalDataOperations {
    override fun launchCreateDocument(name: String) = Unit
    override fun launchOpenDocument() = Unit
    override fun launchShareIntent(file: java.io.File) = Unit
    override fun openInputStream(uri: Uri): java.io.InputStream? = null
    override fun openOutputStream(uri: Uri): java.io.OutputStream? = null
    override fun createTemporaryFile(name: String) = java.io.File(name)
}
