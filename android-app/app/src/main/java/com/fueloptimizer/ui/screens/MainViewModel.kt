package com.fueloptimizer.ui.screens

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fueloptimizer.data.SampleStations
import com.fueloptimizer.data.StationData
import com.fueloptimizer.graph.FuelGraph
import com.fueloptimizer.graph.FuelPlan
import com.fueloptimizer.graph.FuelRouteFinder
import com.fueloptimizer.network.OpinetRepository
import com.fueloptimizer.network.Result
import kotlinx.coroutines.launch

sealed class ScreenState {
    data object Input : ScreenState()
    data class Result(val plan: FuelPlan, val graph: FuelGraph, val startId: String, val goalId: String) : ScreenState()
    data class Error(val message: String) : ScreenState()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    var screenState by mutableStateOf<ScreenState>(ScreenState.Input)
        private set

    val stations = mutableStateListOf<StationData>().apply {
        addAll(SampleStations.all)
    }

    var isLoading by mutableStateOf(false)
        private set

    var fuelEfficiency by mutableStateOf("12.0")
        private set

    var tankCapacity by mutableStateOf("50.0")
        private set

    var dataSource by mutableStateOf(DataSource.SAMPLE)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun updateFuelEfficiency(value: String) {
        fuelEfficiency = value
    }

    fun updateTankCapacity(value: String) {
        tankCapacity = value
    }

    fun selectDataSource(source: DataSource) {
        dataSource = source
    }

    fun searchRoute(startId: String, goalId: String) {
        val fe = fuelEfficiency.toDoubleOrNull()
        val cap = tankCapacity.toDoubleOrNull()

        if (fe == null || fe <= 0) {
            screenState = ScreenState.Error("Please enter a valid fuel efficiency (km/L)")
            return
        }
        if (cap == null || cap <= 0) {
            screenState = ScreenState.Error("Please enter a valid tank capacity (L)")
            return
        }

        val graph = buildGraph()
        if (graph == null) {
            screenState = ScreenState.Error("No stations available")
            return
        }

        val plan = FuelRouteFinder.findMinFuelRoute(
            graph = graph,
            startId = startId,
            goalId = goalId,
            fuelEfficiencyKmPerL = fe,
            tankCapacityL = cap,
            initialFuelL = 0.0
        )

        screenState = if (plan != null) {
            ScreenState.Result(plan, graph, startId, goalId)
        } else {
            ScreenState.Error("No reachable route - try increasing tank capacity")
        }
    }

    private fun buildGraph(): FuelGraph? {
        if (stations.isEmpty()) return null
        val graph = FuelGraph()
        for (s in stations) {
            val station = s.toStation()
            graph.addStation(station)
        }
        val ids = stations.map { it.id }
        for (i in ids.indices) {
            for (j in i + 1 until ids.size) {
                val dist = graph.distance(ids[i], ids[j])
                if (dist <= 30.0) {
                    graph.addEdge(ids[i], ids[j], dist)
                }
            }
        }
        return graph
    }

    fun fetchOpinetStations(apiKey: String, lat: Double = 37.5665, lon: Double = 126.9780) {
        isLoading = true
        errorMessage = null
        viewModelScope.launch {
            when (val result = OpinetRepository.getStationsByLocation(apiKey, lat, lon)) {
                is Result.Success -> {
                    if (result.data.isNotEmpty()) {
                        stations.clear()
                        stations.addAll(result.data)
                        dataSource = DataSource.OPINET
                    } else {
                        errorMessage = "No stations found in this area"
                    }
                }
                is Result.Error -> {
                    errorMessage = "Failed to fetch: ${result.exception.message}"
                }
                Result.Loading -> {}
            }
            isLoading = false
        }
    }

    fun fetchLowestPriceStations(apiKey: String) {
        isLoading = true
        errorMessage = null
        viewModelScope.launch {
            when (val result = OpinetRepository.getLowestPriceStations(apiKey)) {
                is Result.Success -> {
                    if (result.data.isNotEmpty()) {
                        stations.clear()
                        stations.addAll(result.data)
                        dataSource = DataSource.OPINET
                    } else {
                        errorMessage = "No stations found"
                    }
                }
                is Result.Error -> {
                    errorMessage = "Failed to fetch: ${result.exception.message}"
                }
                Result.Loading -> {}
            }
            isLoading = false
        }
    }

    fun goBack() {
        screenState = ScreenState.Input
    }

    fun clearError() {
        errorMessage = null
    }
}

enum class DataSource { SAMPLE, OPINET }
