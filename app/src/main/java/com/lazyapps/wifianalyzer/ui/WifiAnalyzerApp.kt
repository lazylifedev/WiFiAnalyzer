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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.lazyapps.wifianalyzer.ui.navigation.AppDestination
import com.lazyapps.wifianalyzer.ui.navigation.REGISTRATION_ROUTE
import com.lazyapps.wifianalyzer.ui.navigation.DEVICE_DETAIL_ROUTE
import com.lazyapps.wifianalyzer.ui.navigation.deviceDetailRoute
import com.lazyapps.wifianalyzer.ui.screens.channel.ChannelScreen
import com.lazyapps.wifianalyzer.ui.screens.devices.DevicesScreen
import com.lazyapps.wifianalyzer.ui.screens.devices.DeviceRegistrationScreen
import com.lazyapps.wifianalyzer.ui.screens.devices.DeviceDetailScreen
import com.lazyapps.wifianalyzer.ui.screens.home.HomeScreen
import com.lazyapps.wifianalyzer.ui.screens.monitor.MonitorScreen
import com.lazyapps.wifianalyzer.ui.screens.settings.SettingsScreen
import com.lazyapps.wifianalyzer.ui.scan.WifiScanViewModel
import com.lazyapps.wifianalyzer.ui.theme.ThemeViewModel
import com.lazyapps.wifianalyzer.ui.theme.WifiAnalyzerTheme
import com.lazyapps.wifianalyzer.model.ScanState
import com.lazyapps.wifianalyzer.ui.registry.RegistryViewModel

@Composable
fun WifiAnalyzerApp(
    themeViewModel: ThemeViewModel = viewModel(),
    scanViewModel: WifiScanViewModel = viewModel(),
    registryViewModel: RegistryViewModel = viewModel(),
) {
    val themeState by themeViewModel.uiState.collectAsStateWithLifecycle()
    val scanState by scanViewModel.uiState.collectAsStateWithLifecycle()
    val registryState by registryViewModel.uiState.collectAsStateWithLifecycle()
    val enrichedScanState = scanState.copy(accessPoints = registryViewModel.enriched(scanState.accessPoints))
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val permissionPreferences = remember(context) { context.getSharedPreferences("wifi_scan_permissions", Context.MODE_PRIVATE) }
    var requestedOnce by rememberSaveable {
        mutableStateOf(permissionPreferences.getBoolean("requested_once", false))
    }

    fun currentPermissionState(): ScanState {
        val permissions = requiredScanPermissions()
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

    LaunchedEffect(Unit) {
        val permissionState = currentPermissionState()
        scanViewModel.updatePermissionState(permissionState)
        if (permissionState == ScanState.READY) scanViewModel.refresh()
    }
    DisposableEffect(lifecycleOwner, requestedOnce) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) scanViewModel.updatePermissionState(currentPermissionState())
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(scanState.lastUpdatedMillis, registryState.devices.map { it.updatedAt }) {
        registryViewModel.reconcile(scanState.accessPoints)
    }

    val requestPermission = { permissionLauncher.launch(requiredScanPermissions()) }
    val openSettings: (ScanState) -> Unit = { state ->
        val intent = when (state) {
            ScanState.LOCATION_DISABLED -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            ScanState.WIFI_DISABLED -> Intent(Settings.ACTION_WIFI_SETTINGS)
            else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        }
        context.startActivity(intent)
    }

    WifiAnalyzerTheme(mode = themeState.mode, accent = themeState.accent) {
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
                                onClick = {
                                    navController.navigate(destination.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
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
                            navController.navigate(AppDestination.Monitor.route) { launchSingleTop = true }
                        },
                        onRegisterAccessPoint = { accessPoint ->
                            registryViewModel.startNew(accessPoint)
                            navController.navigate(REGISTRATION_ROUTE)
                        },
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
                            navController.navigate(AppDestination.Monitor.route) { launchSingleTop = true }
                        },
                        onRegisterAccessPoint = { accessPoint ->
                            registryViewModel.startNew(accessPoint)
                            navController.navigate(REGISTRATION_ROUTE)
                        },
                    )
                }
                composable(AppDestination.Monitor.route) {
                    MonitorScreen(
                        state = enrichedScanState,
                        onRefresh = scanViewModel::refresh,
                        onRequestPermission = requestPermission,
                        onOpenSettings = openSettings,
                    )
                }
                composable(AppDestination.Devices.route) {
                    DevicesScreen(
                        devices = registryState.devices,
                        groups = registryState.groups,
                        errorMessage = registryState.errorMessage,
                        onAddDevice = { registryViewModel.startNew(); navController.navigate(REGISTRATION_ROUTE) },
                        onOpenDevice = { navController.navigate(deviceDetailRoute(it)) },
                        onDeleteDevice = registryViewModel::deleteDevice,
                        onCreateGroup = registryViewModel::createGroup,
                        onRenameGroup = registryViewModel::renameGroup,
                        onDeleteGroup = registryViewModel::deleteGroup,
                        onMoveGroup = registryViewModel::moveGroup,
                    )
                }
                composable(AppDestination.Settings.route) {
                    SettingsScreen(
                        state = themeState,
                        onModeChange = themeViewModel::setMode,
                        onAccentChange = themeViewModel::setAccent,
                        onAnimationChange = themeViewModel::setAnimationsEnabled,
                    )
                }
                composable(REGISTRATION_ROUTE) {
                    DeviceRegistrationScreen(
                        initial = registryState.draft,
                        groups = registryState.groups,
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
                    )
                }
            }
        }
    }
}

private fun requiredScanPermissions(): Array<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Manifest.permission.ACCESS_COARSE_LOCATION)
    add(Manifest.permission.ACCESS_FINE_LOCATION)
}.toTypedArray()

private tailrec fun Context.findActivity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> error("Activity context is required")
}
