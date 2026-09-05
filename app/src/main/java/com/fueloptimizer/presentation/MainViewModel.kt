package com.fueloptimizer.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fueloptimizer.domain.*
import com.fueloptimizer.network.MockStations
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private val _currentRoute = MutableStateFlow<Route?>(null)
    val currentRoute: StateFlow<Route?> = _currentRoute

    private val _refuelPlan = MutableStateFlow<RefuelPlan?>(null)
    val refuelPlan: StateFlow<RefuelPlan?> = _refuelPlan

    private val _navigationRoute = MutableStateFlow("home")
    val navigationRoute: StateFlow<String> = _navigationRoute

    init {
        _state.value = UiState(
            vehicle = Vehicle(
                fuelKind = FuelKind.gasoline,
                kmPerLiter = 12.0,
                tankCapacityL = 50.0,
                currentFuelL = 20.0,
                reserveL = 5.0
            ),
            preferences = Preferences()
        )
    }

    fun navigateTo(route: String) {
        _navigationRoute.value = route
    }

    fun updateOrigin(place: NamedPlace) {
        _state.value = _state.value.copy(origin = place)
    }

    fun updateDestination(place: NamedPlace) {
        _state.value = _state.value.copy(destination = place)
    }

    fun updateVehicle(vehicle: Vehicle) {
        _state.value = _state.value.copy(vehicle = vehicle)
    }

    fun updatePreferences(preferences: Preferences) {
        _state.value = _state.value.copy(preferences = preferences)
    }

    fun clearOrigin() {
        _state.value = _state.value.copy(origin = null)
    }

    fun clearDestination() {
        _state.value = _state.value.copy(destination = null)
    }

    fun calculateRoute() {
        val origin = _state.value.origin ?: return
        val destination = _state.value.destination ?: return

        viewModelScope.launch {
            val distance = haversineDistance(origin.lat, origin.lng, destination.lat, destination.lng)
            val route = Route(
                id = "route_1",
                origin = origin,
                destination = destination,
                distanceM = distance * 1000,
                durationS = (distance * 1000 / 60.0),
                polyline = listOf(origin, destination),
                summary = "직선 거리 ${String.format("%.0f", distance)}km"
            )
            _currentRoute.value = route
            calculateRefuelPlan(route)
        }
    }

    private fun calculateRefuelPlan(route: Route) {
        val vehicle = _state.value.vehicle ?: return
        val preferences = _state.value.preferences ?: return

        viewModelScope.launch {
            val stations = MockStations.sampleStations
            val prices = stations.mapNotNull { it.prices[vehicle.fuelKind] }
            val refPrice = if (prices.isNotEmpty()) median(prices) else 1800.0

            val options = stations.mapNotNull { station ->
                val detourDist = haversineDistance(
                    route.origin.lat, route.origin.lng, station.lat, station.lng
                ) * 1000

                val detour = Detour(
                    extraDistanceM = detourDist,
                    extraDurationS = detourDist / 60.0,
                    extraTollKrw = 0.0,
                    alongRouteM = 0.0,
                    joinPoint = PointLatLng(station.lat, station.lng),
                    source = "geometric-estimate"
                )

                evaluateOption(
                    station = station,
                    detour = detour,
                    ctx = CostContext(
                        vehicle = vehicle,
                        preferences = preferences,
                        route = route,
                        referencePriceKrwPerL = refPrice,
                        departAt = java.util.Date(),
                        congestionFactor = 1.0
                    )
                )
            }

            val sortedOptions = options
                .filter { it.reachable }
                .sortedBy { it.normalizedCostKrw }

            val baseline = sortedOptions.firstOrNull()
            val best = sortedOptions.firstOrNull()

            val verdict = when {
                options.isEmpty() -> Verdict.noCandidates
                best == null -> Verdict.stayOnRoute
                baseline != null && best.normalizedCostKrw < baseline.normalizedCostKrw -> Verdict.detourWorthIt
                else -> Verdict.stayOnRoute
            }

            val headline = when (verdict) {
                Verdict.noCandidates -> "도착하면 주유해주세요"
                Verdict.stayOnRoute -> "가장 가까운 곳에서 주유하세요"
                Verdict.detourWorthIt -> "우회해서 주유하는 것이 좋습니다"
                else -> "최적 주유 계획"
            }

            val plan = RefuelPlan(
                route = route,
                vehicle = vehicle,
                preferences = preferences,
                litersRequiredWithoutDetour = litersRequiredForTrip(vehicle, route.distanceM, preferences.fillPolicy),
                canReachWithoutRefueling = vehicle.currentFuelL + vehicle.reserveL >= litersRequiredForTrip(vehicle, route.distanceM, FillPolicy(FillPolicyMode.toDestination)),
                referencePriceKrwPerL = refPrice,
                baseline = baseline?.let { toRanked(it, 1, baseline.normalizedCostKrw) },
                best = best?.let { toRanked(it, 1, baseline?.normalizedCostKrw ?: 0.0) },
                options = sortedOptions.mapIndexed { index, option ->
                    toRanked(option, index + 1, baseline?.normalizedCostKrw ?: option.normalizedCostKrw)
                },
                excluded = emptyList(),
                verdict = verdict,
                headline = headline,
                itinerary = emptyList(),
                meta = PlanMeta(
                    stationProvider = "mock",
                    routeProvider = "haversine",
                    detourSource = "geometric-estimate",
                    computedAt = java.time.Instant.now().toString(),
                    candidateCount = stations.size,
                    exactlyEvaluated = options.count { it.reachable },
                    corridorHalfWidthM = 1000.0,
                    requestedHalfWidthM = preferences.maxDetourKm * 1000,
                    corridorTruncated = false,
                    searchRadiusM = 5000.0,
                    searchCallCount = 1,
                    optimalityGuaranteed = true
                )
            )

            _refuelPlan.value = plan
        }
    }

    private fun toRanked(option: RefuelOption, rank: Int, baselineCost: Double): RankedOption {
        return RankedOption(
            savingKrw = baselineCost - option.normalizedCostKrw,
            savingPessimisticKrw = 0.0,
            breakEvenDetourKm = 0.0,
            stockUpValueKrw = 0.0,
            rank = rank,
            station = option.station,
            detour = option.detour,
            listPriceKrwPerL = option.listPriceKrwPerL,
            effectivePriceKrwPerL = option.effectivePriceKrwPerL,
            fuelOnArrivalL = option.fuelOnArrivalL,
            reachable = option.reachable,
            litersToBuy = option.litersToBuy,
            tankCapped = option.tankCapped,
            detourFuelL = option.detourFuelL,
            fuelAtDestinationL = option.fuelAtDestinationL,
            surplusFuelL = option.surplusFuelL,
            shortfallFuelL = option.shortfallFuelL,
            outOfPocketKrw = option.outOfPocketKrw,
            detourFuelCostKrw = option.detourFuelCostKrw,
            timeCostKrw = option.timeCostKrw,
            tollDeltaKrw = option.tollDeltaKrw,
            surplusCreditKrw = option.surplusCreditKrw,
            shortfallCostKrw = option.shortfallCostKrw,
            normalizedCostKrw = option.normalizedCostKrw,
            krwPerUsefulLiter = option.krwPerUsefulLiter,
            warnings = option.warnings
        )
    }

    private fun haversineDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val R = 6371.0
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val deltaLat = Math.toRadians(lat2 - lat1)
        val deltaLng = Math.toRadians(lng2 - lng1)

        val a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return R * c
    }
}

data class UiState(
    val origin: NamedPlace? = null,
    val destination: NamedPlace? = null,
    val vehicle: Vehicle? = null,
    val preferences: Preferences? = null,
)