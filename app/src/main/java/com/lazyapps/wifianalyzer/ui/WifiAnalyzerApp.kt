package com.lazyapps.wifianalyzer.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
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

@Composable
fun WifiAnalyzerApp(
    themeViewModel: ThemeViewModel = viewModel(),
    scanViewModel: WifiScanViewModel = viewModel(),
    registryViewModel: RegistryViewModel = viewModel(),
    workspaceViewModel: WorkspaceViewModel = viewModel(),
) {
    val themeState by themeViewModel.uiState.collectAsStateWithLifecycle()
    val scanState by scanViewModel.uiState.collectAsStateWithLifecycle()
    val registryState by registryViewModel.uiState.collectAsStateWithLifecycle()
    val workspaceState by workspaceViewModel.uiState.collectAsStateWithLifecycle()
    val enrichedScanState = scanState.copy(accessPoints = registryViewModel.enriched(scanState.accessPoints))
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val onboardingPreferences = remember(context) { OnboardingPreferencesRepository(context) }
    val onboardingCompleted by onboardingPreferences.completed.collectAsStateWithLifecycle(initialValue = null)
    var replayOnboarding by rememberSaveable { mutableStateOf(false) }
    val permissionPreferences = remember(context) { context.getSharedPreferences("wifi_scan_permissions", Context.MODE_PRIVATE) }
    var requestedOnce by rememberSaveable {
        mutableStateOf(permissionPreferences.getBoolean("requested_once", false))
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
        val showBottomBar = currentRoute in AppDestination.bottomItems.map { it.route }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (showBottomBar) {
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
                            navController.navigateTopLevel(AppDestination.Monitor.route)
                        },
                        onRegisterAccessPoint = { accessPoint ->
                            registryViewModel.startNew(accessPoint)
                            navController.navigate(REGISTRATION_ROUTE)
                        },
                        workspaceName = workspaceState.selected?.name,
                        selectedBand = scanState.channelBand,
                        onBandSelected = scanViewModel::selectChannelBand,
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
                        isRefreshing = scanState.isRefreshing || registryState.busy,
                        onRefresh = {
                            scanViewModel.refresh()
                            registryViewModel.reconcile(scanState.accessPoints)
                        },
                        workspaceName = workspaceState.selected?.name,
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
                    )
                }
                composable(BACKUP_ROUTE) {
                    BackupScreen(
                        workspaceState = workspaceState,
                        onBack = { navController.popBackStack() },
                        onOpenWorkspace = { id -> workspaceViewModel.select(id); navController.navigateTopLevel(AppDestination.Devices.route) },
                    )
                }
                composable(EXPORT_ROUTE) { ExportScreen(onBack = { navController.popBackStack() }) }
                composable(IMPORT_ROUTE) { ImportScreen(onBack = { navController.popBackStack() }) }
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
        if (showPermissionExplanation) {
            AlertDialog(
                onDismissRequest = { showPermissionExplanation = false },
                title = { Text("Wi-Fiスキャンの権限") },
                text = { Text(AppPermissionPolicy.wifiExplanation()) },
                confirmButton = {
                    Button(onClick = {
                        showPermissionExplanation = false
                        permissionLauncher.launch(AppPermissionPolicy.wifiScanPermissions().toTypedArray())
                    }) { Text("許可する") }
                },
                dismissButton = {
                    TextButton(onClick = { showPermissionExplanation = false }) { Text("今はしない") }
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

private tailrec fun Context.findActivity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> error("Activity context is required")
}
