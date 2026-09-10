package io.github.geekdex.subout.presentation.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.geekdex.subout.presentation.ui.dashboard.DashboardScreen
import io.github.geekdex.subout.presentation.ui.export.ExportScreen
import io.github.geekdex.subout.presentation.ui.nodes.NodesScreen
import io.github.geekdex.subout.presentation.ui.simpleconfig.SimpleConfigScreen
import io.github.geekdex.subout.presentation.ui.subscriptions.SubscriptionsScreen
import io.github.geekdex.subout.presentation.viewmodel.DashboardViewModel
import io.github.geekdex.subout.presentation.viewmodel.ExportViewModel
import io.github.geekdex.subout.presentation.viewmodel.NodesViewModel
import io.github.geekdex.subout.presentation.viewmodel.SimpleConfigViewModel
import io.github.geekdex.subout.presentation.viewmodel.SubscriptionsViewModel

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "首页", Icons.Default.Home)
    object Subscriptions : Screen("subscriptions", "订阅", Icons.Default.RssFeed)
    object Nodes : Screen("nodes", "节点", Icons.Default.Dns)
    object Config : Screen("config", "配置", Icons.Default.Tune)
    object Export : Screen("export", "导出", Icons.Default.Download)
}

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val items = listOf(
        Screen.Dashboard,
        Screen.Subscriptions,
        Screen.Nodes,
        Screen.Config,
        Screen.Export
    )

    val navigateToTab: (String) -> Unit = { route ->
        if (currentRoute != route) {
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentRoute == screen.route,
                        onClick = { navigateToTab(screen.route) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            composable(Screen.Dashboard.route) {
                val dashboardViewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory())
                DashboardScreen(
                    viewModel = dashboardViewModel,
                    onNavigateToSubscriptions = { navigateToTab(Screen.Subscriptions.route) },
                    onNavigateToNodes = { navigateToTab(Screen.Nodes.route) },
                    onNavigateToConfig = { navigateToTab(Screen.Config.route) },
                    onNavigateToExport = { navigateToTab(Screen.Export.route) }
                )
            }

            composable(Screen.Subscriptions.route) {
                val subscriptionsViewModel: SubscriptionsViewModel = viewModel(factory = SubscriptionsViewModel.Factory())
                SubscriptionsScreen(
                    viewModel = subscriptionsViewModel
                )
            }

            composable(Screen.Nodes.route) {
                val nodesViewModel: NodesViewModel = viewModel(factory = NodesViewModel.Factory())
                NodesScreen(
                    viewModel = nodesViewModel
                )
            }

            composable(Screen.Config.route) {
                val configViewModel: SimpleConfigViewModel = viewModel(factory = SimpleConfigViewModel.Factory())
                SimpleConfigScreen(
                    viewModel = configViewModel,
                    onNavigateToExport = { navigateToTab(Screen.Export.route) }
                )
            }

            composable(Screen.Export.route) {
                val exportViewModel: ExportViewModel = viewModel(factory = ExportViewModel.Factory())
                ExportScreen(
                    viewModel = exportViewModel
                )
            }
        }
    }
}
