package com.lazyapps.wifianalyzer.data.backup

import java.io.File
import java.security.MessageDigest

object BackupLimits {
    const val MAX_ZIP_BYTES = 1_073_741_824L
    const val MAX_EXPANDED_BYTES = 2_147_483_648L
    const val MAX_FILE_BYTES = 64L * 1024 * 1024
    const val MAX_PHOTO_BYTES = 30L * 1024 * 1024
    const val MAX_JSON_BYTES = 16L * 1024 * 1024
    const val MAX_ENTRIES = 20_000
    const val MAX_PHOTOS = 10_000
    const val MAX_PATH_LENGTH = 512
    const val MAX_COMPRESSION_RATIO = 200L
}

object BackupSecurity {
    fun validatePath(path: String): String {
        if (path.isBlank() || path.length > BackupLimits.MAX_PATH_LENGTH || path.indexOf('\u0000') >= 0) unsafe(path)
        if (path.startsWith('/') || path.startsWith('\\') || Regex("^[A-Za-z]:").containsMatchIn(path)) unsafe(path)
        if ('\\' in path || path.split('/').any { it.isBlank() || it == "." || it == ".." }) unsafe(path)
        return path
    }
    fun resolve(root: File, path: String): File {
        validatePath(path)
        val file = File(root, path)
        val prefix = root.canonicalFile.path + File.separator
        if (!file.canonicalFile.path.startsWith(prefix)) unsafe(path)
        return file
    }
    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    fun sha256(file: File): String = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) { val read = input.read(buffer); if (read < 0) break; digest.update(buffer, 0, read) }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
    private fun unsafe(path: String): Nothing = throw BackupException(BackupException.Code.UNSAFE_PATH, "Unsafe ZIP path: $path")
}

fun uniqueRestoredName(original: String, existingNormalized: Set<String>): String {
    fun normalized(value: String) = java.text.Normalizer.normalize(value.trim(), java.text.Normalizer.Form.NFKC).lowercase()
    if (normalized(original) !in existingNormalized) return original
    var index = 1
    while (true) {
        val suffix = " (${index + 1})"
        val candidate = original + suffix
        if (normalized(candidate) !in existingNormalized) return candidate
        index++
    }
}
