package com.lazyapps.wifianalyzer.ui.registry

import android.content.Context
import com.lazyapps.wifianalyzer.R
import com.lazyapps.wifianalyzer.data.registry.RegistryError
import com.lazyapps.wifianalyzer.data.registry.RegistryValidationException

fun Context.registryErrorText(error: RegistryValidationException): String {
    val resource = when (error.error) {
        RegistryError.WORKSPACE_NOT_FOUND -> R.string.error_workspace_not_found
        RegistryError.DEVICE_NAME_REQUIRED -> R.string.error_device_name_required
        RegistryError.BSSID_REQUIRED -> R.string.error_bssid_required
        RegistryError.INVALID_BSSID -> R.string.error_invalid_bssid
        RegistryError.DUPLICATE_BSSID_INPUT -> R.string.error_duplicate_bssid_input
        RegistryError.BSSID_ALREADY_REGISTERED -> R.string.error_bssid_already_registered
        RegistryError.GROUP_NOT_FOUND -> R.string.error_group_not_found
        RegistryError.GROUP_NAME_REQUIRED -> R.string.error_group_name_required
        RegistryError.GROUP_NAME_TOO_LONG -> R.string.error_group_name_too_long
        RegistryError.DUPLICATE_GROUP -> R.string.error_duplicate_group
        RegistryError.WORKSPACE_NAME_REQUIRED -> R.string.error_workspace_name_required
        RegistryError.DUPLICATE_WORKSPACE -> R.string.error_duplicate_workspace
        RegistryError.DUPLICATE_VALUE -> R.string.error_duplicate_value
        RegistryError.PHOTO_LIMIT -> R.string.error_photo_limit
        RegistryError.DEVICE_NOT_FOUND -> R.string.error_device_not_found
        RegistryError.INVALID_PHOTO -> R.string.error_invalid_photo
        RegistryError.PHOTO_TOO_LARGE -> R.string.error_photo_too_large
        RegistryError.PHOTO_OUT_OF_MEMORY -> R.string.error_photo_out_of_memory
        RegistryError.PHOTO_WRITE_FAILED -> R.string.error_photo_write
    }
    return getString(resource, *error.arguments)
}
