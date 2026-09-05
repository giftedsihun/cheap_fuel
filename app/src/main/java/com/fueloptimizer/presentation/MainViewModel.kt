package com.fueloptimizer.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fueloptimizer.BuildConfig
import com.fueloptimizer.data.UserStore
import com.fueloptimizer.domain.*
import com.fueloptimizer.network.KakaoClient
import com.fueloptimizer.network.KakaoRouteProvider
import com.fueloptimizer.network.MockRouteProvider
import com.fueloptimizer.network.MockStationProvider
import com.fueloptimizer.network.OpinetStationProvider
import com.fueloptimizer.network.SamplePlaces
import java.util.Date
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val store = UserStore(application)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    val fills: StateFlow<List<FillRecord>> = store.fillsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val reports: StateFlow<List<StationReport>> = store.reportsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val departAtMs: StateFlow<Long?> = store.departAtFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _currentRoute = MutableStateFlow<Route?>(null)
    val currentRoute: StateFlow<Route?> = _currentRoute

    private val _refuelPlan = MutableStateFlow<RefuelPlan?>(null)
    val refuelPlan: StateFlow<RefuelPlan?> = _refuelPlan

    private val _calculating = MutableStateFlow(false)
    val calculating: StateFlow<Boolean> = _calculating

    private val _planError = MutableStateFlow<String?>(null)
    val planError: StateFlow<String?> = _planError

    private val _navigationRoute = MutableStateFlow("home")
    val navigationRoute: StateFlow<String> = _navigationRoute

    private val _placeSearchTarget = MutableStateFlow<PlaceSearchTarget?>(null)
    val placeSearchTarget: StateFlow<PlaceSearchTarget?> = _placeSearchTarget

    private val _placeResults = MutableStateFlow<List<NamedPlace>>(emptyList())
    val placeResults: StateFlow<List<NamedPlace>> = _placeResults

    private val _placeSearching = MutableStateFlow(false)
    val placeSearching: StateFlow<Boolean> = _placeSearching

    private var placeSearchJob: Job? = null

    init {
        viewModelScope.launch {
            store.vehicleFlow.collect { v -> _state.value = _state.value.copy(vehicle = v) }
        }
        viewModelScope.launch {
            store.preferencesFlow.collect { p -> _state.value = _state.value.copy(preferences = p) }
        }
    }

    fun navigateTo(route: String) {
        _navigationRoute.value = route
    }

    fun startPlaceSearch(target: PlaceSearchTarget) {
        _placeSearchTarget.value = target
        searchPlaces("")
        navigateTo("search")
    }

    fun selectPlace(place: NamedPlace): Boolean {
        val target = _placeSearchTarget.value ?: return false
        if (target == PlaceSearchTarget.ORIGIN) {
            updateOrigin(place)
        } else {
            updateDestination(place)
        }
        _placeSearchTarget.value = null
        navigateTo("home")
        return true
    }

    fun cancelPlaceSearch() {
        _placeSearchTarget.value = null
        navigateTo("home")
    }

    fun searchPlaces(query: String) {
        placeSearchJob?.cancel()
        placeSearchJob = viewModelScope.launch {
            delay(400)
            val apiKey = BuildConfig.KAKAO_REST_API_KEY
            if (apiKey.isBlank()) {
                _placeResults.value = SamplePlaces.filter(query)
                return@launch
            }
            _placeSearching.value = true
            try {
                val online = KakaoClient.searchPlaces(apiKey, query)
                _placeResults.value = if (online.isNotEmpty()) online
                else SamplePlaces.filter(query)
            } finally {
                _placeSearching.value = false
            }
        }
    }

    fun updateOrigin(place: NamedPlace) {
        _state.value = _state.value.copy(origin = place)
    }

    fun updateDestination(place: NamedPlace) {
        _state.value = _state.value.copy(destination = place)
    }

    fun updateVehicle(vehicle: Vehicle) {
        _state.value = _state.value.copy(vehicle = vehicle)
        viewModelScope.launch { store.saveVehicle(vehicle) }
    }

    fun updatePreferences(preferences: Preferences) {
        _state.value = _state.value.copy(preferences = preferences)
        viewModelScope.launch { store.savePreferences(preferences) }
    }

    fun clearOrigin() {
        _state.value = _state.value.copy(origin = null)
    }

    fun clearDestination() {
        _state.value = _state.value.copy(destination = null)
    }

    fun setDepartAt(epochMs: Long?) {
        viewModelScope.launch { store.saveDepartAt(epochMs) }
    }

    fun addFill(kmDriven: Double, liters: Double) {
        viewModelScope.launch {
            val id = java.util.UUID.randomUUID().toString()
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
            sdf.timeZone = java.util.TimeZone.getTimeZone("Asia/Seoul")
            store.addFill(FillRecord(id = id, at = sdf.format(Date()), kmDriven = kmDriven, liters = liters))
        }
    }

    fun addReport(stationId: String, stationName: String, kind: ReportKind, note: String = "") {
        viewModelScope.launch {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
            sdf.timeZone = java.util.TimeZone.getTimeZone("Asia/Seoul")
            store.addReport(
                StationReport(
                    stationId = stationId,
                    stationName = stationName,
                    kind = kind,
                    note = note,
                    reportedAt = sdf.format(Date())
                )
            )
        }
    }

    fun dismissPlanError() {
        _planError.value = null
    }

    fun calculateRoute() {
        val origin = _state.value.origin ?: return
        val destination = _state.value.destination ?: return
        val vehicle = _state.value.vehicle ?: return
        val preferences = _state.value.preferences ?: return

        viewModelScope.launch {
            _calculating.value = true
            _planError.value = null
            try {
                val departAt = departAtMs.value?.let { Date(it) }
                val kakaoKey = BuildConfig.KAKAO_REST_API_KEY
                val opinetKey = BuildConfig.OPINET_CERT_KEY

                // 1. 경로
                var routeProviderLabel = "직선보간"
                var route: Route? = null
                if (kakaoKey.isNotBlank()) {
                    route = runCatching {
                        KakaoRouteProvider(kakaoKey).findRoute(origin, destination, vehicle.fuelKind, departAt)
                    }.getOrNull()
                    if (route != null) routeProviderLabel = "카카오모빌리티"
                }
                if (route == null) {
                    route = interpolateRoute(origin, destination)
                }
                _currentRoute.value = route

                // 2. 주유소 후보
                val requestedHalfWidth = maxOf(MIN_CORRIDOR_HALF_WIDTH_M, preferences.maxDetourKm * 1000 / 2)
                var stationProviderLabel = "오프라인 샘플"
                val stations: List<Station> = if (opinetKey.isNotBlank()) {
                    val live = runCatching {
                        OpinetStationProvider(opinetKey).findAlongRoute(route, vehicle.fuelKind, requestedHalfWidth)
                    }.getOrElse { emptyList() }
                    if (live.isNotEmpty()) {
                        stationProviderLabel = "오피넷 실가격"
                        live
                    } else {
                        stationProviderLabel = "오피넷 실패→샘플"
                        MockStationProvider().findAlongRoute()
                    }
                } else {
                    MockStationProvider().findAlongRoute()
                }

                // 3. 우회 계산기
                val detourComputer: DetourComputer = if (kakaoKey.isNotBlank()) {
                    KakaoRouteProvider(kakaoKey)
                } else {
                    MockRouteProvider()
                }
                val detourSource = if (kakaoKey.isNotBlank()) "routing-api" else "geometric-estimate"

                // 4. 계획
                val departDate = departAt ?: Date()
                val plan = buildRefuelPlan(
                    PlanInput(
                        vehicle = vehicle,
                        preferences = preferences,
                        route = route,
                        stations = stations,
                        routeProviderLabel = routeProviderLabel,
                        stationProviderLabel = stationProviderLabel,
                        detourSource = detourSource,
                        departAt = departDate,
                        congestionFactor = congestionFactorAt(departDate, routeLooksDivided(route)),
                        reports = reports.value,
                        learnedKmPerLiter = learnedKmPerLiter(fills.value)
                    ),
                    detourComputer
                )
                _refuelPlan.value = plan
            } catch (e: Exception) {
                _planError.value = "계획 계산에 실패했습니다: ${e.message}"
            } finally {
                _calculating.value = false
            }
        }
    }
}

data class UiState(
    val origin: NamedPlace? = null,
    val destination: NamedPlace? = null,
    val vehicle: Vehicle? = null,
    val preferences: Preferences? = null,
)

enum class PlaceSearchTarget {
    ORIGIN, DESTINATION
}
