package com.fueloptimizer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fueloptimizer.data.StationData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputScreen(viewModel: MainViewModel = viewModel()) {
    val state = viewModel.screenState
    if (state is ScreenState.Result || state is ScreenState.Error) return

    var startExpanded by remember { mutableStateOf(false) }
    var goalExpanded by remember { mutableStateOf(false) }
    var startId by remember(viewModel.stations) { mutableStateOf(viewModel.stations.firstOrNull()?.id ?: "") }
    var goalId by remember(viewModel.stations) { mutableStateOf(viewModel.stations.lastOrNull()?.id ?: "") }
    var apiKey by remember { mutableStateOf("") }
    var showApiKeyDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.errorMessage) {
        viewModel.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fuel Route Optimizer") }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (viewModel.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text("Fetching real data...")
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Minimum-cost refueling route",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Pick start and destination, enter fuel efficiency and tank capacity.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Start station", style = MaterialTheme.typography.labelLarge)
                            StationDropdown(
                                stations = viewModel.stations,
                                selectedId = startId,
                                expanded = startExpanded,
                                onExpandedChange = { startExpanded = it },
                                onSelect = { startId = it; startExpanded = false }
                            )
                        }
                    }

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Destination", style = MaterialTheme.typography.labelLarge)
                            StationDropdown(
                                stations = viewModel.stations,
                                selectedId = goalId,
                                expanded = goalExpanded,
                                onExpandedChange = { goalExpanded = it },
                                onSelect = { goalId = it; goalExpanded = false }
                            )
                        }
                    }

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Vehicle parameters", style = MaterialTheme.typography.labelLarge)
                            OutlinedTextField(
                                value = viewModel.fuelEfficiency,
                                onValueChange = viewModel::updateFuelEfficiency,
                                label = { Text("Fuel efficiency (km/L)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = viewModel.tankCapacity,
                                onValueChange = viewModel::updateTankCapacity,
                                label = { Text("Tank capacity (L)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Data source", style = MaterialTheme.typography.labelLarge)
                            Text(
                                if (viewModel.dataSource == DataSource.SAMPLE) "Sample data (10 stations)" else "Opinet real data",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (viewModel.dataSource == DataSource.SAMPLE) {
                                OutlinedButton(
                                    onClick = { showApiKeyDialog = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Load real stations (Opinet API)")
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.selectDataSource(DataSource.SAMPLE)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Use sample data")
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = { viewModel.searchRoute(startId, goalId) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = startId.isNotEmpty() && goalId.isNotEmpty()
                    ) {
                        Text("Find cheapest route")
                    }
                }
            }

            if (showApiKeyDialog) {
                ApiKeyDialog(
                    apiKey = apiKey,
                    onApiKeyChange = { apiKey = it },
                    onConfirm = {
                        showApiKeyDialog = false
                        if (apiKey.isNotBlank()) {
                            viewModel.fetchOpinetStations(apiKey)
                        }
                    },
                    onDismiss = { showApiKeyDialog = false }
                )
            }
        }
    }
}

@Composable
private fun ApiKeyDialog(
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Opinet API Key") },
        text = {
            Column {
                Text(
                    "Get a free API key from https://www.opinet.co.kr",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChange,
                    label = { Text("API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Load stations") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationDropdown(
    stations: List<StationData>,
    selectedId: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (String) -> Unit
) {
    val selected = stations.firstOrNull { it.id == selectedId }
    val displayText = selected?.let { "${it.name} (${it.brand}) - ${it.priceWhi.toInt()} KRW/L" } ?: ""
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange
    ) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            label = { Text("Station") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            stations.forEach { s ->
                DropdownMenuItem(
                    text = { Text("${s.name} (${s.brand}) - ${s.priceWhi.toInt()} KRW/L") },
                    onClick = { onSelect(s.id) }
                )
            }
        }
    }
}
