package com.fueloptimizer.presentation.navigation

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.fueloptimizer.presentation.home.HomeScreen
import com.fueloptimizer.presentation.map.MapScreen
import com.fueloptimizer.presentation.result.ResultScreen
import com.fueloptimizer.presentation.search.PlaceSearchScreen
import com.fueloptimizer.presentation.settings.SettingsScreen

enum class BottomNavItem(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "홈", Icons.Filled.Home),
    RESULT("result", "결과", Icons.Filled.Assessment),
    MAP("map", "지도", Icons.Filled.Map),
    SETTINGS("settings", "설정", Icons.Filled.Settings)
}

@Composable
fun GasSmartNavigation(
    currentRoute: String,
    onNavigateTo: (String) -> Unit,
    viewModel: com.fueloptimizer.presentation.MainViewModel
) {
    val items = BottomNavItem.entries

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                items.forEach { item ->
                    NavigationBarItem(
                        selected = currentRoute == item.route,
                        onClick = { onNavigateTo(item.route) },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentRoute) {
                "home" -> HomeScreen(
                    viewModel = viewModel,
                    onNavigateToResult = { onNavigateTo("result") }
                )
                "result" -> ResultScreen(
                    viewModel = viewModel,
                    onNavigateToMap = { onNavigateTo("map") }
                )
                "map" -> MapScreen(
                    viewModel = viewModel
                )
                "settings" -> SettingsScreen(viewModel = viewModel)
                "search" -> PlaceSearchScreen(
                    viewModel = viewModel,
                    onBack = { viewModel.cancelPlaceSearch() }
                )
            }
        }
    }
}