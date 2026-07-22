package com.lazyapps.wifianalyzer.ui

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lazyapps.wifianalyzer.ui.navigation.AppDestination
import com.lazyapps.wifianalyzer.ui.navigation.OCR_ROUTE
import com.lazyapps.wifianalyzer.ui.screens.channel.ChannelScreen
import com.lazyapps.wifianalyzer.ui.screens.devices.DevicesScreen
import com.lazyapps.wifianalyzer.ui.screens.devices.OcrRegistrationScreen
import com.lazyapps.wifianalyzer.ui.screens.home.HomeScreen
import com.lazyapps.wifianalyzer.ui.screens.monitor.MonitorScreen
import com.lazyapps.wifianalyzer.ui.screens.settings.SettingsScreen
import com.lazyapps.wifianalyzer.ui.theme.ThemeViewModel
import com.lazyapps.wifianalyzer.ui.theme.WifiAnalyzerTheme

@Composable
fun WifiAnalyzerApp(themeViewModel: ThemeViewModel = viewModel()) {
    val themeState by themeViewModel.uiState.collectAsStateWithLifecycle()
    WifiAnalyzerTheme(mode = themeState.mode, accent = themeState.accent) {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route
        val showBottomBar = currentRoute != OCR_ROUTE

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
                composable(AppDestination.Home.route) { HomeScreen() }
                composable(AppDestination.Channel.route) { ChannelScreen() }
                composable(AppDestination.Monitor.route) { MonitorScreen() }
                composable(AppDestination.Devices.route) { DevicesScreen { navController.navigate(OCR_ROUTE) } }
                composable(AppDestination.Settings.route) {
                    SettingsScreen(
                        state = themeState,
                        onModeChange = themeViewModel::setMode,
                        onAccentChange = themeViewModel::setAccent,
                        onAnimationChange = themeViewModel::setAnimationsEnabled,
                    )
                }
                composable(OCR_ROUTE) { OcrRegistrationScreen { navController.popBackStack() } }
            }
        }
    }
}
