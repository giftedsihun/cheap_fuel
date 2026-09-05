package com.fueloptimizer.domain

interface LatLng {
    val lat: Double
    val lng: Double
}

data class PointLatLng(override val lat: Double, override val lng: Double) : LatLng

data class NamedPlace(
    override val lat: Double,
    override val lng: Double,
    val name: String,
    val address: String? = null
) : LatLng

enum class FuelKind {
    gasoline,
    premium,
    diesel,
    lpg
}

enum class Brand {
    SKE,
    GSC,
    HDO,
    SOL,
    RTE,
    RTX,
    NHO,
    ETC
}

val FUEL_KIND_LABEL = mapOf(
    FuelKind.gasoline to "휘발유",
    FuelKind.premium to "고급휘발유",
    FuelKind.diesel to "경유",
    FuelKind.lpg to "자동차부탄(LPG)"
)

val BRAND_LABEL = mapOf(
    Brand.SKE to "SK에너지",
    Brand.GSC to "GS칼텍스",
    Brand.HDO to "현대오일뱅크",
    Brand.SOL to "S-OIL",
    Brand.RTE to "자영알뜰",
    Brand.RTX to "고속도로알뜰",
    Brand.NHO to "농협알뜰",
    Brand.ETC to "자가상표"
)

enum class ReportKind {
    closed,
    priceMismatch,
    gone
}

data class StationReport(
    val id: String = "",
    val stationId: String,
    val stationName: String = "",
    val kind: ReportKind,
    val note: String = "",
    val reportedAt: String = ""
)

data class FillRecord(
    val id: String,
    val at: String,
    val kmDriven: Double,
    val liters: Double
)

data class Vehicle(
    val fuelKind: FuelKind,
    val kmPerLiter: Double,
    val tankCapacityL: Double,
    val currentFuelL: Double,
    val reserveL: Double = 5.0,
)

enum class FillPolicyMode {
    toDestination,
    full,
    fixedLiters,
    fixedBudget
}

data class FillPolicy(
    val mode: FillPolicyMode,
    val liters: Double? = null,
    val krw: Double? = null,
)

data class Preferences(
    val timeValueKrwPerMin: Double = 0.0,
    val cardDiscountKrwPerL: Double = 0.0,
    val extraDiscountRate: Double = 0.0,
    val maxDetourKm: Double = 5.0,
    val maxDetourMin: Double = 30.0,
    val fillPolicy: FillPolicy = FillPolicy(FillPolicyMode.toDestination),
    val minMeaningfulSavingKrw: Double = 1000.0,
    val selfServiceOnly: Boolean = false,
    val brands: List<Brand> = emptyList(),
    val avoidHighwayExit: Boolean = false,
    val discountRules: List<DiscountRule> = emptyList(),
)

data class DiscountRule(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val flatKrwPerL: Double = 0.0,
    val rate: Double = 0.0,
    val brands: List<Brand> = emptyList(),
)

data class OpeningHours(
    val allDay: Boolean = true,
    val open: String? = null,
    val close: String? = null
)

data class AccessHint(
    val oppositeSide: Boolean = false,
    val requiresHighwayExit: Boolean = false,
    val onHighway: Boolean = false,
)

data class Station(
    val id: String,
    val name: String,
    val brand: Brand,
    val isSelfService: Boolean = false,
    val lat: Double,
    val lng: Double,
    val prices: Map<FuelKind, Double>,
    val priceUpdatedAt: String,
    val openingHours: OpeningHours? = null,
    val address: String? = null,
    val accessHint: AccessHint? = null,
    val hasCarWash: Boolean = false,
)

data class Route(
    val id: String,
    val origin: NamedPlace,
    val destination: NamedPlace,
    val polyline: List<NamedPlace> = emptyList(),
    val distanceM: Double = 0.0,
    val durationS: Double = 0.0,
    val tollKrw: Double = 0.0,
    val summary: String? = null,
    val durationIncludesTraffic: Boolean = false,
    val driveable: Boolean = true,
)

data class Detour(
    val extraDistanceM: Double = 0.0,
    val extraDurationS: Double = 0.0,
    val extraTollKrw: Double = 0.0,
    val alongRouteM: Double = 0.0,
    val offRouteM: Double = 0.0,
    val joinPoint: LatLng,
    val source: String = "geometric-estimate",
    val viaPolyline: List<LatLng>? = null,
)

enum class WarningCode {
    unreachable,
    lowMarginOnArrival,
    tankCapped,
    insufficientToDestination,
    stalePrice,
    closedOnArrival,
    oppositeSide,
    highwayExit,
    estimatedDetour,
    userReported,
    congested
}

enum class Severity {
    info,
    warn,
    error
}

data class Warning(
    val code: WarningCode,
    val message: String,
    val severity: Severity
)

data class RefuelOption(
    val station: Station,
    val detour: Detour,
    val listPriceKrwPerL: Double,
    val effectivePriceKrwPerL: Double,
    val fuelOnArrivalL: Double,
    val reachable: Boolean,
    val litersToBuy: Double,
    val tankCapped: Boolean,
    val detourFuelL: Double,
    val fuelAtDestinationL: Double,
    val surplusFuelL: Double,
    val shortfallFuelL: Double,
    val outOfPocketKrw: Double,
    val detourFuelCostKrw: Double,
    val timeCostKrw: Double,
    val tollDeltaKrw: Double,
    val surplusCreditKrw: Double,
    val shortfallCostKrw: Double,
    val normalizedCostKrw: Double,
    val krwPerUsefulLiter: Double,
    val warnings: List<Warning>,
)

data class RankedOption(
    val savingKrw: Double,
    val savingPessimisticKrw: Double,
    val breakEvenDetourKm: Double,
    val stockUpValueKrw: Double,
    val rank: Int,
    val station: Station,
    val detour: Detour,
    val listPriceKrwPerL: Double,
    val effectivePriceKrwPerL: Double,
    val fuelOnArrivalL: Double,
    val reachable: Boolean,
    val litersToBuy: Double,
    val tankCapped: Boolean,
    val detourFuelL: Double,
    val fuelAtDestinationL: Double,
    val surplusFuelL: Double,
    val shortfallFuelL: Double,
    val outOfPocketKrw: Double,
    val detourFuelCostKrw: Double,
    val timeCostKrw: Double,
    val tollDeltaKrw: Double,
    val surplusCreditKrw: Double,
    val shortfallCostKrw: Double,
    val normalizedCostKrw: Double,
    val krwPerUsefulLiter: Double,
    val warnings: List<Warning>,
)

enum class Verdict {
    detourWorthIt,
    marginal,
    stayOnRoute,
    noCandidates,
    noRefuelNeeded,
    multiStop
}

data class ItineraryStop(
    val option: RankedOption,
    val litersToBuy: Double,
    val fillReason: String,
    val outOfPocketKrw: Double,
    val fuelOnArrivalL: Double,
    val fuelOnDepartL: Double,
)

data class PlanMeta(
    val stationProvider: String,
    val routeProvider: String,
    val detourSource: String,
    val computedAt: String,
    val candidateCount: Int,
    val exactlyEvaluated: Int,
    val corridorHalfWidthM: Double,
    val requestedHalfWidthM: Double,
    val corridorTruncated: Boolean,
    val searchRadiusM: Double,
    val searchCallCount: Int,
    val optimalityGuaranteed: Boolean,
)

data class RefuelPlan(
    val route: Route,
    val vehicle: Vehicle,
    val preferences: Preferences,
    val litersRequiredWithoutDetour: Double,
    val canReachWithoutRefueling: Boolean,
    val referencePriceKrwPerL: Double,
    val baseline: RankedOption?,
    val best: RankedOption?,
    val options: List<RankedOption>,
    val excluded: List<Pair<Station, String>>,
    val verdict: Verdict,
    val headline: String,
    val itinerary: List<ItineraryStop>,
    val meta: PlanMeta,
)