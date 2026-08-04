package com.lazyapps.wifianalyzer.billing

data class FeatureLimits(
    val maxWorkspaceCount: Int? = 1,
    val maxDeviceCount: Int? = 5,
    val maxPhotosPerDevice: Int? = 1,
)

enum class AccessRestriction { SavedDeviceLimitReached, WorkspaceLimitReached, DevicePhotoLimitReached, CsvRequiresPro, PdfRequiresPro, BackupRequiresPro, RestoreRequiresPro }

data class FeatureAccessPolicy(
    val isPro: Boolean,
    val limits: FeatureLimits = FeatureLimits(),
) {
    val canUseKintone: Boolean get() = isPro
    val canRemoveAds: Boolean get() = isPro
    val canUseUnlimitedWorkspaces: Boolean get() = limits.maxWorkspaceCount == null
    val canUseUnlimitedDevices: Boolean get() = limits.maxDeviceCount == null
    val canUseCsvImport: Boolean get() = true
    val canExportCsv: Boolean get() = isPro
    val canExportPdf: Boolean get() = isPro
    val canBackup: Boolean get() = isPro
    val canRestore: Boolean get() = isPro
    val canUseAdvancedExport: Boolean get() = isPro
    val canUseFullBackup: Boolean get() = isPro
    val maxWorkspaceCount: Int? get() = limits.maxWorkspaceCount
    val maxDeviceCountPerWorkspace: Int? get() = limits.maxDeviceCount
    val maxPhotosPerDevice: Int? get() = limits.maxPhotosPerDevice

    companion object {
        fun from(state: ProEntitlementState, debugForcePro: Boolean = false) = FeatureAccessPolicy(
            isPro = debugForcePro || state == ProEntitlementState.Pro ||
                (state is ProEntitlementState.Error && state.retainedPro),
            limits = if (debugForcePro || state == ProEntitlementState.Pro || (state is ProEntitlementState.Error && state.retainedPro)) FeatureLimits(null, null, null) else FeatureLimits(),
        )
    }

    fun restrictionForDeviceCount(total: Int) = if (isPro || total < (limits.maxDeviceCount ?: Int.MAX_VALUE)) null else AccessRestriction.SavedDeviceLimitReached
    fun restrictionForWorkspaceCount(total: Int) = if (isPro || total < (limits.maxWorkspaceCount ?: Int.MAX_VALUE)) null else AccessRestriction.WorkspaceLimitReached
    fun restrictionForPhotoCount(total: Int) = if (isPro || total < (limits.maxPhotosPerDevice ?: Int.MAX_VALUE)) null else AccessRestriction.DevicePhotoLimitReached
}

enum class AdPlacement { HOME, CHANNEL, MONITOR, DEVICE_LIST, SETTINGS }

class AdVisibilityPolicy(private val access: FeatureAccessPolicy) {
    fun canShow(@Suppress("UNUSED_PARAMETER") placement: AdPlacement): Boolean = !access.canRemoveAds
}
