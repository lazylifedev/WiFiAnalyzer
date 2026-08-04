package com.lazyapps.wifianalyzer.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavHostController
import com.lazyapps.wifianalyzer.ui.navigation.AppDestination
import com.lazyapps.wifianalyzer.ui.navigation.REGISTRATION_ROUTE
import com.lazyapps.wifianalyzer.ui.navigation.OCR_REGISTRATION_ROUTE
import com.lazyapps.wifianalyzer.ui.navigation.DEVICE_DETAIL_ROUTE
import com.lazyapps.wifianalyzer.ui.navigation.BACKUP_ROUTE
import com.lazyapps.wifianalyzer.ui.navigation.EXPORT_ROUTE
import com.lazyapps.wifianalyzer.ui.navigation.IMPORT_ROUTE
import com.lazyapps.wifianalyzer.ui.navigation.PRO_ROUTE
import com.lazyapps.wifianalyzer.ui.navigation.KINTONE_ROUTE
import com.lazyapps.wifianalyzer.ui.navigation.KINTONE_INFO_ROUTE
import com.lazyapps.wifianalyzer.ui.navigation.KINTONE_QR_ROUTE
import com.lazyapps.wifianalyzer.ui.export.ExportScreen
import com.lazyapps.wifianalyzer.ui.importcsv.ImportScreen
import com.lazyapps.wifianalyzer.ui.backup.BackupScreen
import com.lazyapps.wifianalyzer.ui.navigation.deviceDetailRoute
import com.lazyapps.wifianalyzer.ui.screens.channel.ChannelScreen
import com.lazyapps.wifianalyzer.ui.screens.devices.DevicesScreen
import com.lazyapps.wifianalyzer.ui.screens.devices.DeviceRegistrationScreen
import com.lazyapps.wifianalyzer.ui.screens.devices.DeviceDetailScreen
import com.lazyapps.wifianalyzer.ui.screens.devices.OcrRegistrationScreen
import com.lazyapps.wifianalyzer.ui.screens.home.HomeScreen
import com.lazyapps.wifianalyzer.ui.screens.monitor.MonitorScreen
import com.lazyapps.wifianalyzer.ui.screens.settings.SettingsScreen
import com.lazyapps.wifianalyzer.ui.pro.ProScreen
import com.lazyapps.wifianalyzer.ui.pro.KintoneScreen
import com.lazyapps.wifianalyzer.ui.pro.KintonePluginInfoScreen
import com.lazyapps.wifianalyzer.ui.kintone.KintoneQrScreen
import com.lazyapps.wifianalyzer.ui.kintone.KintoneViewModel
import com.lazyapps.wifianalyzer.billing.BillingViewModel
import com.lazyapps.wifianalyzer.billing.FeatureAccessPolicy
import com.lazyapps.wifianalyzer.billing.DebugProPreferences
import com.lazyapps.wifianalyzer.BuildConfig
import com.lazyapps.wifianalyzer.R
import com.lazyapps.wifianalyzer.debug.DebugDisplayPreferences
import com.lazyapps.wifianalyzer.debug.DebugLogPanel
import com.lazyapps.wifianalyzer.debug.DebugLogs
import com.lazyapps.wifianalyzer.review.PlayReviewCoordinator
import com.lazyapps.wifianalyzer.review.ReviewContext
import com.lazyapps.wifianalyzer.review.ReviewHistoryRepository
import com.lazyapps.wifianalyzer.review.ReviewPromptController
import com.lazyapps.wifianalyzer.ui.scan.WifiScanViewModel
import com.lazyapps.wifianalyzer.ui.theme.ThemeViewModel
import com.lazyapps.wifianalyzer.ui.theme.WifiAnalyzerTheme
import com.lazyapps.wifianalyzer.model.ScanState
import com.lazyapps.wifianalyzer.ui.registry.RegistryViewModel
import com.lazyapps.wifianalyzer.ui.workspace.WorkspaceViewModel
import com.lazyapps.wifianalyzer.data.OnboardingPreferencesRepository
import com.lazyapps.wifianalyzer.ui.onboarding.OnboardingScreen
import com.lazyapps.wifianalyzer.ui.permissions.AppPermissionPolicy
import com.lazyapps.wifianalyzer.ui.permissions.PermissionStatus
import com.lazyapps.wifianalyzer.ui.permissions.PermissionSummary
import kotlinx.coroutines.launch
import com.lazyapps.wifianalyzer.ads.AdBanner
import com.lazyapps.wifianalyzer.ads.AdMobManager
import com.lazyapps.wifianalyzer.billing.AdVisibilityPolicy

private const val KINTONE_BOOTH_URL = "https://lazylifedev.booth.pm/items/8670244"

@Composable
fun WifiAnalyzerApp(
    themeViewModel: ThemeViewModel = viewModel(),
    scanViewModel: WifiScanViewModel = viewModel(),
    registryViewModel: RegistryViewModel = viewModel(),
    workspaceViewModel: WorkspaceViewModel = viewModel(),
    billingViewModel: BillingViewModel = viewModel(),
    kintoneViewModel: KintoneViewModel = viewModel(),
) {
    val themeState by themeViewModel.uiState.collectAsStateWithLifecycle()
    val scanState by scanViewModel.uiState.collectAsStateWithLifecycle()
    val registryState by registryViewModel.uiState.collectAsStateWithLifecycle()
    val workspaceState by workspaceViewModel.uiState.collectAsStateWithLifecycle()
    val billingState by billingViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val debugProPreferences = remember(context) { DebugProPreferences(context) }
    val debugForcePro by debugProPreferences.forcePro.collectAsStateWithLifecycle(initialValue = false)
    val debugDisplayPreferences = remember(context) { DebugDisplayPreferences(context) }
    val debugDisplayEnabled by debugDisplayPreferences.enabled.collectAsStateWithLifecycle(initialValue = false)
    val kintoneState by kintoneViewModel.uiState.collectAsStateWithLifecycle()
    val enrichedScanState = scanState.copy(accessPoints = registryViewModel.enriched(scanState.accessPoints))
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val onboardingPreferences = remember(context) { OnboardingPreferencesRepository(context) }
    val reviewHistory = remember(context) { ReviewHistoryRepository(context) }
    val reviewController = remember(context) { ReviewPromptController(reviewHistory, PlayReviewCoordinator()) }
    var lastRecordedScanSuccess by remember { mutableStateOf(0L) }
    val onboardingCompleted by onboardingPreferences.completed.collectAsStateWithLifecycle(initialValue = null)
    var replayOnboarding by rememberSaveable { mutableStateOf(false) }
    val permissionPreferences = remember(context) { context.getSharedPreferences("wifi_scan_permissions", Context.MODE_PRIVATE) }
    var requestedOnce by rememberSaveable {
        mutableStateOf(permissionPreferences.getBoolean("requested_once", false))
    }
    LaunchedEffect(workspaceState.selectedId, workspaceState.selected?.name) {
        workspaceState.selected?.let { kintoneViewModel.selectWorkspace(it.id, it.name, fromAppSelection = true) }
    }
    LaunchedEffect(billingState.entitlement, debugForcePro) {
        val access = FeatureAccessPolicy.from(billingState.entitlement, debugForcePro)
        if (!access.isPro && billingState.entitlement != com.lazyapps.wifianalyzer.billing.ProEntitlementState.Unknown) activity?.let { AdMobManager.initialize(it) }
        kintoneViewModel.setAccessAllowed(access.canUseKintone)
    }

    fun currentPermissionState(): ScanState {
        val permissions = AppPermissionPolicy.wifiScanPermissions()
        val granted = permissions.all { permission ->
            androidx.core.content.ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (granted) return ScanState.READY
        if (!requestedOnce) return ScanState.PERMISSION_REQUIRED
        val canAskAgain = permissions.any(activity::shouldShowRequestPermissionRationale)
        return if (canAskAgain) ScanState.PERMISSION_DENIED else ScanState.PERMISSION_PERMANENTLY_DENIED
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        requestedOnce = true
        permissionPreferences.edit().putBoolean("requested_once", true).apply()
        scanViewModel.updatePermissionState(currentPermissionState())
        if (currentPermissionState() == ScanState.READY) scanViewModel.refresh()
    }
    var showPermissionExplanation by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val permissionState = currentPermissionState()
        scanViewModel.updatePermissionState(permissionState)
        if (permissionState == ScanState.READY) scanViewModel.refresh()
    }
    DisposableEffect(lifecycleOwner, requestedOnce) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    scanViewModel.setForeground(true)
                    scanViewModel.updatePermissionState(currentPermissionState())
                    billingViewModel.refresh()
                }
                Lifecycle.Event.ON_PAUSE -> scanViewModel.setForeground(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(scanState.lastUpdatedMillis, registryState.devices.map { it.updatedAt }) {
        registryViewModel.reconcile(scanState.accessPoints)
        val updatedAt = scanState.lastUpdatedMillis ?: 0L
        if (updatedAt > 0 && updatedAt != lastRecordedScanSuccess && scanState.accessPoints.isNotEmpty()) {
            lastRecordedScanSuccess = updatedAt
            reviewHistory.recordMeaningfulSuccess(updatedAt)
        }
    }

    val requestPermission = { showPermissionExplanation = true }
    val openSettings: (ScanState) -> Unit = { state ->
        val intent = when (state) {
            ScanState.LOCATION_DISABLED -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            ScanState.WIFI_DISABLED -> Intent(Settings.ACTION_WIFI_SETTINGS)
            else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        }
        context.startActivity(intent)
    }

    WifiAnalyzerTheme(mode = themeState.mode, accent = themeState.accent) {
        if (onboardingCompleted == null) return@WifiAnalyzerTheme
        if (onboardingCompleted == false || replayOnboarding) {
            OnboardingScreen(
                onComplete = {
                    replayOnboarding = false
                    coroutineScope.launch { onboardingPreferences.setCompleted(true) }
                },
            )
            return@WifiAnalyzerTheme
        }
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route
        LaunchedEffect(currentRoute, scanState.isRefreshing, onboardingCompleted) {
            if (currentRoute == AppDestination.Home.route && onboardingCompleted == true && !scanState.isRefreshing) {
                reviewController.requestIfEligible(
                    activity,
                    ReviewContext(onboardingCompleted = true, isBusy = false, hasModal = showPermissionExplanation),
                )
            }
        }
        val showBottomBar = currentRoute in AppDestination.bottomItems.map { it.route }
        val accessPolicy = FeatureAccessPolicy.from(billingState.entitlement, debugForcePro)
        val adPlacement = when (currentRoute) {
            AppDestination.Home.route -> com.lazyapps.wifianalyzer.billing.AdPlacement.HOME
            AppDestination.Channel.route -> com.lazyapps.wifianalyzer.billing.AdPlacement.CHANNEL
            AppDestination.Monitor.route -> com.lazyapps.wifianalyzer.billing.AdPlacement.MONITOR
            AppDestination.Devices.route -> com.lazyapps.wifianalyzer.billing.AdPlacement.DEVICE_LIST
            AppDestination.Settings.route -> com.lazyapps.wifianalyzer.billing.AdPlacement.SETTINGS
            else -> null
        }
        val showAd = billingState.entitlement != com.lazyapps.wifianalyzer.billing.ProEntitlementState.Unknown &&
            adPlacement != null && AdVisibilityPolicy(accessPolicy).canShow(adPlacement)

        Box {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (showBottomBar) {
                    androidx.compose.foundation.layout.Column {
                        if (showAd) AdBanner()
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        AppDestination.bottomItems.forEach { destination ->
                            NavigationBarItem(
                                modifier = Modifier.testTag("nav_${destination.route}"),
                                selected = currentRoute == destination.route,
                                onClick = { navController.navigateTopLevel(destination.route) },
                                icon = { Icon(destination.icon, contentDescription = stringResource(destination.labelRes)) },
                                label = { Text(stringResource(destination.labelRes), maxLines = 1) },
                                alwaysShowLabel = true,
                            )
                        }
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = AppDestination.Home.route,
                modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            ) {
                composable(AppDestination.Home.route) {
                    HomeScreen(
                        state = enrichedScanState,
                        onRefresh = scanViewModel::refresh,
                        onRequestPermission = requestPermission,
                        onOpenSettings = openSettings,
                        onSelectAccessPoint = { bssid ->
                            scanViewModel.selectAccessPoint(bssid)
                            navController.navigateTopLevel(AppDestination.Monitor.route)
                        },
                        onRegisterAccessPoint = { accessPoint ->
                            registryViewModel.startNew(accessPoint)
                            navController.navigate(REGISTRATION_ROUTE)
                        },
                        workspaceName = workspaceState.selected?.name,
                        selectedBand = scanState.homeBand,
                        onBandSelected = scanViewModel::selectHomeBand,
                        onOpenDevices = { navController.navigateTopLevel(AppDestination.Devices.route) },
                        onOpenOcr = { navController.navigate(OCR_REGISTRATION_ROUTE) },
                        showInlineNativeAd = showAd,
                    )
                }
                composable(AppDestination.Channel.route) {
                    ChannelScreen(
                        state = enrichedScanState,
                        onRefresh = scanViewModel::refresh,
                        onRequestPermission = requestPermission,
                        onOpenSettings = openSettings,
                        onSelectAccessPoint = { bssid ->
                            scanViewModel.selectAccessPoint(bssid)
                        },
                        onClearAccessPointSelection = scanViewModel::clearAccessPointSelection,
                        onOpenAccessPoint = { bssid ->
                            scanViewModel.selectAccessPoint(bssid)
                            navController.navigateTopLevel(AppDestination.Monitor.route)
                        },
                        onRegisterAccessPoint = { accessPoint ->
                            registryViewModel.startNew(accessPoint)
                            navController.navigate(REGISTRATION_ROUTE)
                        },
                        workspaceName = workspaceState.selected?.name,
                        selectedBand = scanState.channelBand,
                        onBandSelected = scanViewModel::selectChannelBand,
                        onDisplayModeChange = scanViewModel::setChannelDisplayMode,
                    )
                }
                composable(AppDestination.Monitor.route) {
                    MonitorScreen(
                        state = enrichedScanState,
                        onRefresh = scanViewModel::refresh,
                        onRequestPermission = requestPermission,
                        onOpenSettings = openSettings,
                        onHistoryRangeChange = scanViewModel::setSignalHistoryRange,
                    )
                }
                composable(AppDestination.Devices.route) {
                    DevicesScreen(
                        devices = registryState.devices,
                        groups = registryState.groups,
                        errorMessage = registryState.errorMessage,
                        onAddDevice = { registryViewModel.startNew(); navController.navigate(REGISTRATION_ROUTE) },
                        onScanLabel = { navController.navigate(OCR_REGISTRATION_ROUTE) },
                        onOpenDevice = { navController.navigate(deviceDetailRoute(it)) },
                        onDeleteDevice = registryViewModel::deleteDevice,
                        onCreateGroup = registryViewModel::createGroup,
                        onRenameGroup = registryViewModel::renameGroup,
                        onDeleteGroup = registryViewModel::deleteGroup,
                        onMoveGroup = registryViewModel::moveGroup,
                        isRefreshing = scanState.isRefreshing,
                        scanState = enrichedScanState,
                        onRefresh = {
                            scanViewModel.refresh()
                            registryViewModel.reconcile(scanState.accessPoints)
                        },
                        workspaceName = workspaceState.selected?.name,
                        showInlineNativeAd = showAd,
                    )
                }
                composable(AppDestination.Settings.route) {
                    SettingsScreen(
                        state = themeState,
                        onModeChange = themeViewModel::setMode,
                        onAccentChange = themeViewModel::setAccent,
                        onAnimationChange = themeViewModel::setAnimationsEnabled,
                        refreshIntervalMillis = scanState.refreshIntervalMillis,
                        onRefreshIntervalChange = scanViewModel::setRefreshInterval,
                        distanceUnit = scanState.distanceUnit,
                        onDistanceUnitChange = scanViewModel::setDistanceUnit,
                        visibleBands = scanState.visibleBands,
                        onVisibleBandsChange = scanViewModel::setVisibleBands,
                        workspaceState = workspaceState,
                        onSelectWorkspace = workspaceViewModel::select,
                        onCreateWorkspace = workspaceViewModel::create,
                        onRenameWorkspace = workspaceViewModel::rename,
                        onMoveWorkspace = workspaceViewModel::move,
                        onDeleteWorkspace = workspaceViewModel::delete,
                        onLoadWorkspaceCounts = workspaceViewModel::loadCounts,
                        onOpenBackup = { navController.navigate(BACKUP_ROUTE) },
                        onOpenExport = { navController.navigate(EXPORT_ROUTE) },
                        onOpenImport = { navController.navigate(IMPORT_ROUTE) },
                        onOpenPro = { navController.navigate(PRO_ROUTE) },
                        onOpenPrivacyOptions = { AdMobManager.showPrivacyOptions(activity) },
                        showPrivacyOptions = AdMobManager.privacyOptionsRequired.value,
                        onOpenKintone = { navController.navigate(KINTONE_ROUTE) },
                        onRateApp = { context.openPlayStoreRating() },
                        permissionSummary = PermissionSummary(
                            wifiScan = when (currentPermissionState()) {
                                ScanState.READY -> PermissionStatus.GRANTED
                                ScanState.PERMISSION_PERMANENTLY_DENIED -> PermissionStatus.SETTINGS_REQUIRED
                                else -> PermissionStatus.NOT_GRANTED
                            },
                            camera = if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) PermissionStatus.GRANTED else PermissionStatus.NOT_GRANTED,
                        ),
                        onRequestScanPermission = requestPermission,
                        onOpenAppSettings = { openSettings(ScanState.PERMISSION_PERMANENTLY_DENIED) },
                        onShowOnboarding = { replayOnboarding = true },
                        debugForcePro = debugForcePro,
                        onDebugForceProChange = { enabled -> coroutineScope.launch { debugProPreferences.setForcePro(enabled) } },
                        debugDisplayEnabled = debugDisplayEnabled,
                        onDebugDisplayEnabledChange = { enabled ->
                            coroutineScope.launch { debugDisplayPreferences.setEnabled(enabled) }
                        },
                    )
                }
                composable(BACKUP_ROUTE) {
                    BackupScreen(
                        workspaceState = workspaceState,
                        onBack = { navController.popBackStack() },
                        onOpenWorkspace = { id -> workspaceViewModel.select(id); navController.navigateTopLevel(AppDestination.Devices.route) },
                        onOperationSuccess = {},
                    )
                }
                composable(EXPORT_ROUTE) { ExportScreen(onBack = { navController.popBackStack() }, onOperationSuccess = {}) }
                composable(IMPORT_ROUTE) { ImportScreen(onBack = { navController.popBackStack() }) }
                composable(PRO_ROUTE) {
                    ProScreen(
                        state = billingState,
                        onBack = { navController.popBackStack() },
                        onPurchase = { billingViewModel.purchase(activity) },
                        onRestore = billingViewModel::restore,
                    )
                }
                composable(KINTONE_ROUTE) {
                    KintoneScreen(
                        access = FeatureAccessPolicy.from(billingState.entitlement, debugForcePro),
                        state = kintoneState,
                        onBack = { navController.popBackStack() },
                        onOpenPro = { navController.navigate(PRO_ROUTE) },
                        onPluginInfo = { navController.navigate(KINTONE_INFO_ROUTE) },
                        onOpenBooth = { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(KINTONE_BOOTH_URL))) },
                        onScanQr = { navController.navigate(KINTONE_QR_ROUTE) },
                        onConfirm = kintoneViewModel::confirmSave,
                        onCancelPending = kintoneViewModel::cancel,
                        onVerify = kintoneViewModel::reverify,
                        onDisconnect = kintoneViewModel::disconnect,
                        onSync = kintoneViewModel::sync,
                        onAutoSyncChange = kintoneViewModel::setAutoSync,
                        onPhotoAutoSyncChange = kintoneViewModel::setPhotoAutoSync,
                        onWorkspaceSelected = { option -> kintoneViewModel.selectWorkspace(option.id, option.name) },
                        onCancelSync = kintoneViewModel::cancel,
                    )
                }
                composable(KINTONE_QR_ROUTE) {
                    if (!FeatureAccessPolicy.from(billingState.entitlement, debugForcePro).canUseKintone) {
                        LaunchedEffect(Unit) { navController.popBackStack(); navController.navigate(PRO_ROUTE) }
                    } else KintoneQrScreen(
                        onBack = { navController.popBackStack() },
                        onQr = { raw ->
                            val target = kintoneState.workspaces.firstOrNull { it.id == kintoneState.workspaceId } ?: return@KintoneQrScreen
                            navController.popBackStack()
                            kintoneViewModel.acceptQr(raw, target.id, target.name)
                        },
                    )
                }
                composable(KINTONE_INFO_ROUTE) { KintonePluginInfoScreen(
                    onBack = { navController.popBackStack() },
                    onOpenBooth = { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(KINTONE_BOOTH_URL))) },
                ) }
                composable(REGISTRATION_ROUTE) {
                    DeviceRegistrationScreen(
                        initial = registryState.draft,
                        groups = registryState.formGroups,
                        errorMessage = registryState.errorMessage,
                        busy = registryState.busy,
                        onBack = { navController.popBackStack() },
                        onSave = { input ->
                            registryViewModel.save(input) { id ->
                                navController.navigate(deviceDetailRoute(id)) {
                                    popUpTo(REGISTRATION_ROUTE) { inclusive = true }
                                }
                            }
                        },
                        baseline = registryState.editBaseline,
                        groupCreateDialogVisible = registryState.groupCreateDialogVisible,
                        newGroupName = registryState.newGroupName,
                        groupNameValidationError = registryState.groupNameValidationError,
                        isCreatingGroup = registryState.isCreatingGroup,
                        onShowGroupCreate = registryViewModel::showGroupCreateDialog,
                        onDismissGroupCreate = registryViewModel::hideGroupCreateDialog,
                        onNewGroupNameChange = registryViewModel::updateNewGroupName,
                        onCreateGroup = registryViewModel::createGroupForDraft,
                    )
                }
                composable(OCR_REGISTRATION_ROUTE) {
                    OcrRegistrationScreen(
                        nearby = enrichedScanState.accessPoints,
                        onBack = { navController.popBackStack() },
                        onUseDraft = { draft ->
                            registryViewModel.startNew(draft)
                            navController.navigate(REGISTRATION_ROUTE) {
                                popUpTo(OCR_REGISTRATION_ROUTE) { inclusive = true }
                            }
                        },
                        onManual = {
                            if (registryState.draft.id == 0L) registryViewModel.startNew()
                            navController.navigate(REGISTRATION_ROUTE) {
                                popUpTo(OCR_REGISTRATION_ROUTE) { inclusive = true }
                            }
                        },
                        existingDraft = registryState.draft.takeIf { it.id != 0L },
                    )
                }
                composable(DEVICE_DETAIL_ROUTE) { entry ->
                    val deviceId = entry.arguments?.getString("deviceId")?.toLongOrNull()
                    val device = registryState.devices.firstOrNull { it.id == deviceId }
                    val detected = enrichedScanState.accessPoints.filter { it.registeredDeviceId == deviceId }
                    DeviceDetailScreen(
                        device = device,
                        detectedAccessPoints = detected,
                        onBack = { navController.popBackStack() },
                        onEdit = {
                            deviceId?.let(registryViewModel::startEdit)
                            navController.navigate(REGISTRATION_ROUTE)
                        },
                        onDelete = {
                            deviceId?.let {
                                registryViewModel.deleteDevice(it) {
                                    navController.navigate(AppDestination.Devices.route) {
                                        popUpTo(AppDestination.Devices.route) { inclusive = false }
                                        launchSingleTop = true
                                    }
                                }
                            }
                        },
                        onMonitor = { bssid ->
                            scanViewModel.selectAccessPoint(bssid)
                            navController.navigate(AppDestination.Monitor.route)
                        },
                        onOcrUpdate = {
                            deviceId?.let(registryViewModel::startEdit)
                            navController.navigate(OCR_REGISTRATION_ROUTE)
                        },
                        useFeet = scanState.distanceUnit == com.lazyapps.wifianalyzer.data.DistanceUnitPreference.FEET,
                    )
                }
            }
        }
        if (BuildConfig.DEBUG && debugDisplayEnabled) {
            DebugLogPanel(
                store = DebugLogs.store,
                modifier = Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = if (showBottomBar) 80.dp else 0.dp),
            )
        }
        }
        if (showPermissionExplanation) {
            AlertDialog(
                onDismissRequest = { showPermissionExplanation = false },
                title = { Text(stringResource(R.string.wifi_scan_permission_title)) },
                text = { Text(stringResource(AppPermissionPolicy.wifiExplanationRes())) },
                confirmButton = {
                    Button(onClick = {
                        showPermissionExplanation = false
                        permissionLauncher.launch(AppPermissionPolicy.wifiScanPermissions().toTypedArray())
                    }) { Text(stringResource(R.string.allow)) }
                },
                dismissButton = {
                    TextButton(onClick = { showPermissionExplanation = false }) { Text(stringResource(R.string.not_now)) }
                },
            )
        }
    }
}

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun Context.openPlayStoreRating() {
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
        addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
    }
    val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
    runCatching { startActivity(market) }.recoverCatching { startActivity(web) }.onFailure {
        Toast.makeText(this, getString(R.string.play_store_unavailable), Toast.LENGTH_LONG).show()
    }
}

private tailrec fun Context.findActivity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> error("Activity context is required")
}
