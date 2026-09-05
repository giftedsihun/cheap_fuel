package com.fueloptimizer.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.fueloptimizer.ui.theme.GasSmartTheme
import com.fueloptimizer.presentation.navigation.GasSmartNavigation

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GasSmartTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel = MainViewModel()
                    val currentRoute by viewModel.navigationRoute.collectAsState()

                    GasSmartNavigation(
                        currentRoute = currentRoute,
                        onNavigateTo = { route -> viewModel.navigateTo(route) },
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}
