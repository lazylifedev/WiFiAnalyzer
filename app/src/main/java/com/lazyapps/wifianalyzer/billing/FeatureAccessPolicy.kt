package com.lazyapps.wifianalyzer.billing

data class FeatureLimits(
    val maxWorkspaceCount: Int? = 1,
    val maxDeviceCount: Int? = 5,
    val maxPhotosPerDevice: Int? = 1,
)

enum class AccessRestriction { SavedDeviceLimitReached, WorkspaceLimitReached, DevicePhotoLimitReached, CsvRequiresPro, PdfRequiresPro, BackupRequiresPro, RestoreRequiresPro }

data class AccessDecision(val allowed: Boolean, val reason: AccessRestriction? = null, val currentCount: Int, val freeLimit: Int?, val proLimit: Int?)

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
            limits = if (debugForcePro || state == ProEntitlementState.Pro || (state is ProEntitlementState.Error && state.retainedPro)) FeatureLimits(null, null, 9) else FeatureLimits(),
        )
    }

    fun deviceDecision(total: Int) = decision(total, limits.maxDeviceCount, AccessRestriction.SavedDeviceLimitReached, 5, null)
    fun workspaceDecision(total: Int) = decision(total, limits.maxWorkspaceCount, AccessRestriction.WorkspaceLimitReached, 1, null)
    fun photoDecision(total: Int): AccessDecision {
        val limit = if (isPro) 9 else 1
        return AccessDecision(total < limit, if (total >= limit) AccessRestriction.DevicePhotoLimitReached else null, total, 1, 9)
    }
    fun restrictionForDeviceCount(total: Int) = deviceDecision(total).reason
    fun restrictionForWorkspaceCount(total: Int) = workspaceDecision(total).reason
    fun restrictionForPhotoCount(total: Int) = photoDecision(total).reason
    private fun decision(total: Int, freeLimit: Int?, reason: AccessRestriction, free: Int, pro: Int?) = AccessDecision(isPro || freeLimit == null || total < freeLimit, if (!isPro && freeLimit != null && total >= freeLimit) reason else null, total, free, pro)
}

enum class AdPlacement { HOME, CHANNEL, MONITOR, DEVICE_LIST, SETTINGS }

class AdVisibilityPolicy(private val access: FeatureAccessPolicy) {
    fun canShow(@Suppress("UNUSED_PARAMETER") placement: AdPlacement): Boolean = !access.canRemoveAds
}
