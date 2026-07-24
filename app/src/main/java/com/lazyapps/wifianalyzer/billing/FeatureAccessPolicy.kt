package com.lazyapps.wifianalyzer.billing

data class FeatureLimits(
    val maxWorkspaceCount: Int? = null,
    val maxDeviceCountPerWorkspace: Int? = null,
    val maxPhotosPerDevice: Int? = null,
)

data class FeatureAccessPolicy(
    val isPro: Boolean,
    val limits: FeatureLimits = FeatureLimits(),
) {
    val canUseKintone: Boolean get() = isPro
    val canRemoveAds: Boolean get() = isPro
    val canUseUnlimitedWorkspaces: Boolean get() = limits.maxWorkspaceCount == null
    val canUseUnlimitedDevices: Boolean get() = limits.maxDeviceCountPerWorkspace == null
    val canUseCsvImport: Boolean get() = true
    val canUseAdvancedExport: Boolean get() = true
    val canUseFullBackup: Boolean get() = true
    val maxWorkspaceCount: Int? get() = limits.maxWorkspaceCount
    val maxDeviceCountPerWorkspace: Int? get() = limits.maxDeviceCountPerWorkspace
    val maxPhotosPerDevice: Int? get() = limits.maxPhotosPerDevice

    companion object {
        fun from(state: ProEntitlementState, debugForcePro: Boolean = false) = FeatureAccessPolicy(
            isPro = debugForcePro || state == ProEntitlementState.Pro ||
                (state is ProEntitlementState.Error && state.retainedPro),
        )
    }
}

enum class AdPlacement { HOME, CHANNEL, DEVICE_LIST }

class AdVisibilityPolicy(private val access: FeatureAccessPolicy) {
    fun canShow(@Suppress("UNUSED_PARAMETER") placement: AdPlacement): Boolean = !access.canRemoveAds
}
