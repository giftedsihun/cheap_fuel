package com.fueloptimizer.presentation.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fueloptimizer.domain.FuelKind
import com.fueloptimizer.presentation.MainViewModel
import com.fueloptimizer.presentation.PlaceSearchTarget
import com.fueloptimizer.ui.components.TossCard
import com.fueloptimizer.ui.components.TossInputField
import com.fueloptimizer.ui.components.TossSearchField
import com.fueloptimizer.ui.components.TossSelector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel = viewModel(),
    onNavigateToResult: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "Gas Smart",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 24.sp
                        )
                    )
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.statusBarsPadding()
            )
        },
        bottomBar = {
            if (state.origin != null && state.destination != null) {
                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    Button(
                        onClick = {
                            viewModel.calculateRoute()
                            onNavigateToResult()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        Text(
                            text = "Find Cheapest Route",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            TossCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Vehicle",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )

                    var expanded by remember { mutableStateOf(false) }
                    val fuelKinds = FuelKind.entries.toList()

                    TossSelector(
                        value = state.vehicle?.fuelKind?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Gasoline",
                        label = "Fuel Type",
                        options = fuelKinds.map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                        onValueChange = { selected ->
                            val currentVehicle = state.vehicle ?: return@TossSelector
                            viewModel.updateVehicle(currentVehicle.copy(fuelKind = FuelKind.valueOf(selected.uppercase())))
                        },
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                        modifier = Modifier.fillMaxWidth()
                    )

                    TossInputField(
                        value = (state.vehicle?.kmPerLiter ?: 12.0).toString(),
                        onValueChange = { value ->
                            val currentVehicle = state.vehicle ?: return@TossInputField
                            viewModel.updateVehicle(currentVehicle.copy(kmPerLiter = value.toDoubleOrNull() ?: 12.0))
                        },
                        label = "Fuel Efficiency (km/L)",
                        modifier = Modifier.fillMaxWidth(),
                        keyboardType = KeyboardType.Decimal
                    )

                    TossInputField(
                        value = (state.vehicle?.tankCapacityL ?: 50.0).toString(),
                        onValueChange = { value ->
                            val currentVehicle = state.vehicle ?: return@TossInputField
                            viewModel.updateVehicle(currentVehicle.copy(tankCapacityL = value.toDoubleOrNull() ?: 50.0))
                        },
                        label = "Tank Capacity (L)",
                        modifier = Modifier.fillMaxWidth(),
                        keyboardType = KeyboardType.Decimal
                    )

                    TossInputField(
                        value = (state.vehicle?.currentFuelL ?: 20.0).toString(),
                        onValueChange = { value ->
                            val currentVehicle = state.vehicle ?: return@TossInputField
                            viewModel.updateVehicle(currentVehicle.copy(currentFuelL = value.toDoubleOrNull() ?: 20.0))
                        },
                        label = "Current Fuel (L)",
                        modifier = Modifier.fillMaxWidth(),
                        keyboardType = KeyboardType.Decimal
                    )

                    TossInputField(
                        value = (state.vehicle?.reserveL ?: 5.0).toString(),
                        onValueChange = { value ->
                            val currentVehicle = state.vehicle ?: return@TossInputField
                            viewModel.updateVehicle(currentVehicle.copy(reserveL = value.toDoubleOrNull() ?: 5.0))
                        },
                        label = "Reserve Fuel (L)",
                        modifier = Modifier.fillMaxWidth(),
                        keyboardType = KeyboardType.Decimal
                    )
                }
            }

            TossCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Route",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    TossSearchField(
                        value = state.origin?.name ?: "출발지 검색",
                        onClick = { viewModel.startPlaceSearch(PlaceSearchTarget.ORIGIN) },
                        placeholder = "Search start point"
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    TossSearchField(
                        value = state.destination?.name ?: "도착지 검색",
                        onClick = { viewModel.startPlaceSearch(PlaceSearchTarget.DESTINATION) },
                        placeholder = "Search destination"
                    )
                }
            }
        }
    }
}