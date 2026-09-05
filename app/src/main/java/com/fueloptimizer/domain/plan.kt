package com.fueloptimizer.domain

import java.util.Date

const val MAX_EXACT_DETOUR_CANDIDATES = 24
const val MIN_EXACT_DETOUR_CANDIDATES = 10
const val DETOUR_BATCH_SIZE = 12
const val MIN_CORRIDOR_HALF_WIDTH_M = 1200.0
const val DETOUR_SPEED_KMH = 28.0
const val PESSIMISTIC_KMPL_RATIO = 0.85
const val PESSIMISTIC_PRICE_BUMP_KRW = 25.0
const val PESSIMISTIC_DETOUR_RATIO = 1.25
const val FALLBACK_REFERENCE_PRICE = 1700.0

data class PlanInput(
    val vehicle: Vehicle,
    val preferences: Preferences,
    val route: Route,
    val stations: List<Station>,
    val routeProviderLabel: String,
    val stationProviderLabel: String,
    val detourSource: String,
    val departAt: Date,
    val congestionFactor: Double = 1.0,
    val reports: List<StationReport> = emptyList(),
    val learnedKmPerLiter: Double? = null
)

interface DetourComputer {
    suspend fun computeDetours(route: Route, stations: List<Station>): Map<String, Detour>
}

private data class EvaluatedCandidate(val option: RefuelOption, val lowerBoundCost: Double)

suspend fun buildRefuelPlan(input: PlanInput, detourComputer: DetourComputer): RefuelPlan {
    val vehicle = input.vehicle
    val prefs = input.preferences
    val route = input.route
    val policy = prefs.fillPolicy
    val e = input.learnedKmPerLiter ?: vehicle.kmPerLiter

    val requestedHalfWidthM = maxOf(MIN_CORRIDOR_HALF_WIDTH_M, prefs.maxDetourKm * 1000 / 2)
    val corridor = planCorridorSearch(route.polyline, requestedHalfWidthM)

    val litersRequiredWithoutDetour = litersRequiredForTrip(vehicle, route.distanceM, policy)
    val tripNeedL = route.distanceM / 1000 / e
    val canReachWithoutRefueling = tripNeedL <= vehicle.currentFuelL - destinationHoldL(vehicle, policy)

    val excluded = mutableListOf<Pair<Station, String>>()
    val survivors = mutableListOf<Station>()
    for (station in input.stations) {
        if (station.prices[vehicle.fuelKind] == null) {
            excluded.add(station to "이 연료(${FUEL_KIND_LABEL[vehicle.fuelKind]})를 팔지 않습니다.")
            continue
        }
        if (prefs.selfServiceOnly && !station.isSelfService) {
            excluded.add(station to "셀프 주유소가 아닙니다.")
            continue
        }
        if (prefs.brands.isNotEmpty() && station.brand !in prefs.brands) {
            excluded.add(station to "선택한 브랜드가 아닙니다.")
            continue
        }
        val gone = input.reports.find { it.stationId == station.id && it.kind == ReportKind.gone }
        if (gone != null) {
            excluded.add(station to "폐업·이전으로 제보됨")
            continue
        }
        val closed = input.reports.find { it.stationId == station.id && it.kind == ReportKind.closed }
        if (closed != null) {
            excluded.add(station to "영업하지 않는다고 제보됨")
            continue
        }
        val proj = projectOntoPolyline(PointLatLng(station.lat, station.lng), route.polyline)
        if (proj.offsetM > corridor.coveredHalfWidthM) {
            excluded.add(station to "경로에서 ${String.format("%.1f", proj.offsetM / 1000)}km 떨어져 회랑 밖입니다.")
            continue
        }
        val fuelLeft = vehicle.currentFuelL - proj.alongM / 1000 / e
        if (fuelLeft + EPS < minArrivalFuelL(vehicle)) {
            excluded.add(station to "현재 연료로는 이 주유소까지 닿지 않습니다.")
            continue
        }
        val enriched = withAccessHint(station, proj, route)
        val hint = enriched.accessHint
        if (prefs.avoidHighwayExit && (hint?.requiresHighwayExit == true || hint?.onHighway == true)) {
            excluded.add(station to "고속도로 진출입이 필요해 제외했습니다.")
            continue
        }
        survivors.add(enriched)
    }

    val referencePrice = survivors
        .mapNotNull { it.prices[vehicle.fuelKind] }
        .let { if (it.isNotEmpty()) median(it) else effectivePricePerLiter(FALLBACK_REFERENCE_PRICE, prefs, null) }

    fun ctxFor(v: Vehicle, detourScale: Double, reportList: List<StationReport>, congestion: Double) =
        CostContext(
            vehicle = v,
            preferences = prefs,
            route = route,
            referencePriceKrwPerL = referencePrice,
            departAt = input.departAt,
            congestionFactor = congestion,
            reports = reportList,
            forcedLiters = null
        )
    val baseCtx = ctxFor(vehicle, 1.0, input.reports, input.congestionFactor)

    // 하한 추정치로 정렬 + 가장 가까운 후보를 기준선 앵커로 앞으로
    data class Lb(val station: Station, val proj: Projection, val cost: Double)
    val lbs = survivors.map { station ->
        val proj = projectOntoPolyline(PointLatLng(station.lat, station.lng), route.polyline)
        val lb = Detour(
            extraDistanceM = proj.offsetM * 2,
            extraDurationS = 0.0,
            extraTollKrw = 0.0,
            alongRouteM = proj.alongM,
            offRouteM = proj.offsetM,
            joinPoint = proj.point,
            source = "geometric-estimate"
        )
        val opt = evaluateOption(station, lb, baseCtx)
        Lb(station, proj, opt?.normalizedCostKrw ?: Double.POSITIVE_INFINITY)
    }.sortedBy { it.cost }
    val ordered: List<Station> = if (lbs.isEmpty()) emptyList() else {
        val nearest = lbs.minByOrNull { projectOntoPolyline(PointLatLng(it.station.lat, it.station.lng), route.polyline).offsetM }!!
        listOf(nearest.station) + lbs.map { it.station }.filter { it.id != nearest.station.id }
    }

    // 쿼터 루프: 정확한 우회 계산 배치 처리 + 가지치기
    val evaluated = mutableListOf<RefuelOption>()
    var exactlyEvaluated = 0
    var stoppedByQuota = false
    var bestCostSoFar = Double.POSITIVE_INFINITY
    var i = 0
    while (i < ordered.size) {
        if (exactlyEvaluated >= MAX_EXACT_DETOUR_CANDIDATES) {
            stoppedByQuota = true
            break
        }
        val batch = ordered.subList(i, minOf(i + DETOUR_BATCH_SIZE, ordered.size))
        i += batch.size
        val detours: Map<String, Detour> = try {
            detourComputer.computeDetours(route, batch)
        } catch (_: Exception) {
            emptyMap()
        }
        for (station in batch) {
            val proj = projectOntoPolyline(PointLatLng(station.lat, station.lng), route.polyline)
            val detour = detours[station.id] ?: Detour(
                extraDistanceM = proj.offsetM * 2,
                extraDurationS = 0.0,
                extraTollKrw = 0.0,
                alongRouteM = proj.alongM,
                offRouteM = proj.offsetM,
                joinPoint = proj.point,
                source = "geometric-estimate"
            )
            val opt = evaluateOption(station, detour, baseCtx) ?: continue
            exactlyEvaluated++
            val fixedDetour = rejectIllegalUturn(detour, proj, station, route)
            val finalOpt = if (fixedDetour !== detour) {
                evaluateOption(station, fixedDetour, baseCtx) ?: continue
            } else opt
            val detourKm = fixedDetour.extraDistanceM / 1000
            if (detourKm > prefs.maxDetourKm + 1e-9) {
                excluded.add(station to "우회 ${String.format("%.1f", detourKm)}km가 한도(${String.format("%.1f", prefs.maxDetourKm)}km)를 넘습니다.")
                continue
            }
            if (fixedDetour.extraDurationS / 60 > prefs.maxDetourMin + 1e-9) {
                excluded.add(station to "우회 ${(fixedDetour.extraDurationS / 60).toInt()}분이 한도(${prefs.maxDetourMin.toInt()}분)를 넘습니다.")
                continue
            }
            if (!finalOpt.reachable) {
                excluded.add(station to "현재 연료로는 이 주유소까지 닿지 않습니다.")
                continue
            }
            if (finalOpt.warnings.any { it.code == WarningCode.closedOnArrival }) {
                excluded.add(station to "도착 예상 시각에 영업하지 않습니다.")
                continue
            }
            evaluated.add(finalOpt)
            if (finalOpt.normalizedCostKrw < bestCostSoFar) bestCostSoFar = finalOpt.normalizedCostKrw
        }
        // 가지치기: 충분히 평가했고, 남은 후보 하한이 이미 최적보다 비싸면 중단
        if (exactlyEvaluated >= MIN_EXACT_DETOUR_CANDIDATES && i < ordered.size) {
            val remainingLb = ordered.subList(i, ordered.size).mapNotNull { st ->
                lbs.find { it.station.id == st.id }?.cost
            }.minOrNull()
            if (remainingLb != null && remainingLb >= bestCostSoFar) break
        }
    }

    val sorted = evaluated.sortedWith(
        compareBy<RefuelOption> { it.detour.extraDistanceM }.thenBy { it.effectivePriceKrwPerL }
    )
    val baseline = sorted.firstOrNull()
    val baselineCost = baseline?.normalizedCostKrw ?: (referencePrice * litersRequiredWithoutDetour)

    val ranked = sorted.mapIndexed { index, opt ->
        val pessimisticVehicle = vehicle.copy(kmPerLiter = vehicle.kmPerLiter * PESSIMISTIC_KMPL_RATIO)
        val pessimisticStation = opt.station.copy(
            prices = opt.station.prices + (vehicle.fuelKind to (opt.listPriceKrwPerL + PESSIMISTIC_PRICE_BUMP_KRW))
        )
        val pessimisticDetour = opt.detour.copy(
            extraDistanceM = opt.detour.extraDistanceM * PESSIMISTIC_DETOUR_RATIO,
            extraDurationS = opt.detour.extraDurationS * PESSIMISTIC_DETOUR_RATIO
        )
        val pessimisticOpt = evaluateOption(
            pessimisticStation, pessimisticDetour,
            ctxFor(pessimisticVehicle, PESSIMISTIC_DETOUR_RATIO, input.reports, input.congestionFactor)
        )
        val savingPessimistic = if (pessimisticOpt != null) baselineCost - pessimisticOpt.normalizedCostKrw else 0.0
        val saving = baselineCost - opt.normalizedCostKrw
        val gainPerLiter = (baseline?.effectivePriceKrwPerL ?: referencePrice) - opt.effectivePriceKrwPerL
        toRanked(
            opt = opt,
            rank = index + 1,
            savingKrw = saving,
            savingPessimisticKrw = savingPessimistic,
            breakEvenKm = breakEvenDetourKm(
                gainPerLiter,
                opt.litersToBuy,
                opt.effectivePriceKrwPerL,
                vehicle.kmPerLiter,
                prefs.timeValueKrwPerMin,
                DETOUR_SPEED_KMH
            ),
            stockUp = maxOf(0.0, referencePrice - opt.effectivePriceKrwPerL) * opt.surplusFuelL
        )
    }

    val best = ranked.firstOrNull()
    val fillFullAtLast = policy.mode == FillPolicyMode.full
    val itinerary = if (best != null) planItinerary(
        ItineraryInput(
            route = route,
            vehicle = vehicle,
            options = ranked,
            fillFullAtLast = fillFullAtLast,
            destinationHoldL = destinationHoldL(vehicle, policy)
        )
    ) else emptyList()

    val (verdict, headline) = decide(
        ranked = ranked,
        best = best,
        baseline = baseline,
        itinerary = itinerary,
        canReach = canReachWithoutRefueling,
        policy = policy,
        prefs = prefs
    )

    val meta = PlanMeta(
        stationProvider = input.stationProviderLabel,
        routeProvider = input.routeProviderLabel,
        detourSource = input.detourSource,
        computedAt = input.departAt.toString(),
        candidateCount = input.stations.size,
        exactlyEvaluated = exactlyEvaluated,
        corridorHalfWidthM = corridor.coveredHalfWidthM,
        requestedHalfWidthM = requestedHalfWidthM,
        corridorTruncated = corridor.truncated,
        searchRadiusM = corridor.searchRadiusM,
        searchCallCount = corridor.callCount,
        optimalityGuaranteed = !stoppedByQuota
    )

    return RefuelPlan(
        route = route,
        vehicle = vehicle,
        preferences = prefs,
        litersRequiredWithoutDetour = litersRequiredWithoutDetour,
        canReachWithoutRefueling = canReachWithoutRefueling,
        referencePriceKrwPerL = referencePrice,
        baseline = baseline?.let { toRankedPlaceholder(it) },
        best = best,
        options = ranked,
        excluded = excluded,
        verdict = verdict,
        headline = headline,
        itinerary = itinerary,
        meta = meta
    )
}

private fun toRanked(
    opt: RefuelOption,
    rank: Int,
    savingKrw: Double,
    savingPessimisticKrw: Double,
    breakEvenKm: Double,
    stockUp: Double
) = RankedOption(
    savingKrw = savingKrw,
    savingPessimisticKrw = savingPessimisticKrw,
    breakEvenDetourKm = breakEvenKm,
    stockUpValueKrw = stockUp,
    rank = rank,
    station = opt.station,
    detour = opt.detour,
    listPriceKrwPerL = opt.listPriceKrwPerL,
    effectivePriceKrwPerL = opt.effectivePriceKrwPerL,
    fuelOnArrivalL = opt.fuelOnArrivalL,
    reachable = opt.reachable,
    litersToBuy = opt.litersToBuy,
    tankCapped = opt.tankCapped,
    detourFuelL = opt.detourFuelL,
    fuelAtDestinationL = opt.fuelAtDestinationL,
    surplusFuelL = opt.surplusFuelL,
    shortfallFuelL = opt.shortfallFuelL,
    outOfPocketKrw = opt.outOfPocketKrw,
    detourFuelCostKrw = opt.detourFuelCostKrw,
    timeCostKrw = opt.timeCostKrw,
    tollDeltaKrw = opt.tollDeltaKrw,
    surplusCreditKrw = opt.surplusCreditKrw,
    shortfallCostKrw = opt.shortfallCostKrw,
    normalizedCostKrw = opt.normalizedCostKrw,
    krwPerUsefulLiter = opt.krwPerUsefulLiter,
    warnings = opt.warnings
)

private fun toRankedPlaceholder(opt: RefuelOption) =
    toRanked(opt, 0, 0.0, 0.0, Double.POSITIVE_INFINITY, 0.0)

private fun decide(
    ranked: List<RankedOption>,
    best: RankedOption?,
    baseline: RefuelOption?,
    itinerary: List<ItineraryStop>,
    canReach: Boolean,
    policy: FillPolicy,
    prefs: Preferences
): Pair<Verdict, String> {
    if (ranked.isEmpty()) {
        return Verdict.noCandidates to "주변에서 주유 후보를 찾지 못했습니다. 우회 한도를 늘려보세요."
    }
    if (itinerary.size >= 2) {
        val names = itinerary.map { stationHeading(it.option.station) }
        return Verdict.multiStop to "두 번 나눠 넣는 게 가장 쌉니다: ${names.joinToString(" → ")}"
    }
    val b = best!!
    if (canReach && policy.mode == FillPolicyMode.toDestination) {
        return Verdict.noRefuelNeeded to "지금 연료로 목적지까지 갈 수 있습니다. 주유는 목적지 근처가 유리합니다."
    }
    if (baseline != null && b.station.id == baseline.station.id) {
        return Verdict.stayOnRoute to "가장 가까운 ${stationHeading(b.station)}에서 넣는 게 가장 쌉니다."
    }
    if (b.savingKrw < prefs.minMeaningfulSavingKrw || b.savingPessimisticKrw <= 0) {
        return Verdict.marginal to " 돌아가는 만큼 아끼는 금액이 작습니다(${formatSignedKrw(b.savingKrw)}). 그냥 가까운 곳이 낫습니다."
    }
    var headline = "${stationHeading(b.station)}에서 넣으면 ${formatSignedKrw(b.savingKrw)} 아낍니다."
    if (b.stockUpValueKrw > 500) {
        headline += " 가득 채우면 ${formatKrw(b.stockUpValueKrw)}어치 여유분까지 챙깁니다."
    }
    return Verdict.detourWorthIt to headline
}
