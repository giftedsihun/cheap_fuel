package com.fueloptimizer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fueloptimizer.ui.screens.FreeMapScreen
import com.fueloptimizer.ui.screens.InputScreen
import com.fueloptimizer.ui.screens.MainViewModel
import com.fueloptimizer.ui.screens.ResultScreen
import com.fueloptimizer.ui.screens.ScreenState
import com.fueloptimizer.ui.theme.FuelOptimizerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FuelOptimizerTheme {
                FuelOptimizerApp()
            }
        }
    }
}

@Composable
fun FuelOptimizerApp() {
    val viewModel: MainViewModel = viewModel()
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                    label = { Text("Search") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.MyLocation, contentDescription = "Map") },
                    label = { Text("Map") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = viewModel.screenState) {
                is ScreenState.Input -> {
                    when (selectedTab) {
                        0 -> InputScreen(viewModel)
                        1 -> Text("Use Search tab to find a route first")
                    }
                }
                is ScreenState.Result -> {
                    when (selectedTab) {
                        0 -> ResultScreen(
                            plan = state.plan,
                            graph = state.graph,
                            startId = state.startId,
                            goalId = state.goalId,
                            onBack = viewModel::goBack
                        )
                        1 -> FreeMapScreen(
                            plan = state.plan,
                            graph = state.graph,
                            startId = state.startId,
                            goalId = state.goalId
                        )
                    }
                }
                is ScreenState.Error -> {
                    when (selectedTab) {
                        0 -> InputScreen(viewModel)
                        1 -> Text("Use Search tab to find a route first")
                    }
                }
            }
        }
    }
}
