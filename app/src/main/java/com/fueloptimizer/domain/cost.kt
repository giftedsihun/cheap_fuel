package com.fueloptimizer.domain

const val STALE_PRICE_HOURS = 36.0
const val LOW_MARGIN_RATIO = 0.08
const val EPS = 1e-9
const val TO_DESTINATION_HOLD_RATIO = 0.2
const val SKIP_REFUEL_EXTRA_L = 8.0

fun destinationHoldL(vehicle: Vehicle, policy: FillPolicy?): Double {
    return if (policy?.mode == FillPolicyMode.toDestination) {
        maxOf(vehicle.reserveL, vehicle.tankCapacityL * TO_DESTINATION_HOLD_RATIO)
    } else {
        vehicle.reserveL
    }
}

fun minArrivalFuelL(vehicle: Vehicle): Double {
    return if (vehicle.currentFuelL > vehicle.reserveL + EPS) vehicle.reserveL else 0.0
}

fun effectivePricePerLiter(
    listPriceKrwPerL: Double,
    preferences: Preferences,
    brand: Brand? = null,
): Double {
    var flat = preferences.cardDiscountKrwPerL
    var rate = preferences.extraDiscountRate.coerceIn(0.0, 1.0)

    for (rule in preferences.discountRules) {
        if (!rule.enabled) continue
        if (rule.brands.isNotEmpty() && (brand == null || brand !in rule.brands)) continue
        flat += rule.flatKrwPerL
        rate = 1 - (1 - rate) * (1 - rule.rate.coerceIn(0.0, 1.0))
    }

    return maxOf(0.0, (listPriceKrwPerL - flat) * (1 - rate))
}

fun shouldSuggestSkipRefuel(
    vehicle: Vehicle,
    route: Route,
    options: List<Detour>,
    policy: FillPolicy? = null,
): Boolean {
    val leftL = vehicle.currentFuelL - route.distanceM / 1000 / vehicle.kmPerLiter
    if (leftL + EPS < destinationHoldL(vehicle, policy) + SKIP_REFUEL_EXTRA_L) return false
    val windowM = minOf(30_000.0, maxOf(15_000.0, route.distanceM * 0.25))
    return options.any { route.distanceM - it.alongRouteM <= windowM }
}

fun litersRequiredForTrip(
    vehicle: Vehicle,
    distanceM: Double,
    policy: FillPolicy? = null,
): Double {
    val needL = distanceM / 1000 / vehicle.kmPerLiter
    return maxOf(0.0, needL + destinationHoldL(vehicle, policy) - vehicle.currentFuelL)
}

fun desiredLitersForPolicy(
    policy: FillPolicy,
    totalTripKm: Double,
    vehicle: Vehicle,
    maxFillableL: Double,
    effectivePriceKrwPerL: Double,
): Double {
    return when (policy.mode) {
        FillPolicyMode.toDestination -> {
            maxOf(0.0, totalTripKm / vehicle.kmPerLiter + destinationHoldL(vehicle, policy) - vehicle.currentFuelL)
        }
        FillPolicyMode.full -> maxFillableL
        FillPolicyMode.fixedLiters -> maxOf(0.0, policy.liters ?: 0.0)
        FillPolicyMode.fixedBudget -> {
            if (effectivePriceKrwPerL <= EPS) 0.0
            else maxOf(0.0, (policy.krw ?: 0.0) / effectivePriceKrwPerL)
        }
    }
}

private fun parseHhmm(value: String): Int {
    val parts = value.split(":")
    val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
    return h * 60 + m
}

fun parseIsoDate(value: String): java.util.Date? {
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm",
        "yyyy-MM-dd"
    )
    for (pattern in patterns) {
        val sdf = java.text.SimpleDateFormat(pattern, java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("Asia/Seoul")
        sdf.isLenient = false
        try {
            return sdf.parse(value)
        } catch (e: java.text.ParseException) {
            // try next pattern
        }
    }
    return null
}

fun minutesOfDayInSeoul(at: java.util.Date): Int {
    val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    sdf.timeZone = java.util.TimeZone.getTimeZone("Asia/Seoul")
    val timeStr = sdf.format(at)
    val parts = timeStr.split(":")
    val hour = parts[0].toIntOrNull() ?: 0
    val minute = parts[1].toIntOrNull() ?: 0
    return (hour % 24) * 60 + minute
}

fun isOpenAt(station: Station, at: java.util.Date): Boolean {
    val hours = station.openingHours ?: return true
    if (hours.allDay) return true
    if (hours.open == null || hours.close == null) return true
    val minutes = minutesOfDayInSeoul(at)
    val open = parseHhmm(hours.open)
    val close = parseHhmm(hours.close)
    return if (close <= open) minutes >= open || minutes < close else minutes >= open && minutes < close
}

data class CostContext(
    val vehicle: Vehicle,
    val preferences: Preferences,
    val route: Route,
    val referencePriceKrwPerL: Double,
    val departAt: java.util.Date,
    val congestionFactor: Double = 1.0,
    val reports: List<StationReport> = emptyList(),
    val forcedLiters: Double? = null,
)

fun evaluateOption(station: Station, detour: Detour, ctx: CostContext): RefuelOption? {
    val listPrice = station.prices[ctx.vehicle.fuelKind] ?: return null

    val mismatch = ctx.reports.find { it.stationId == station.id && it.kind == ReportKind.priceMismatch }
    val listed = listPrice + (if (mismatch != null) 40 else 0)
    val effectivePrice = effectivePricePerLiter(listed, ctx.preferences, station.brand)
    val e = ctx.vehicle.kmPerLiter

    val baseKm = ctx.route.distanceM / 1000
    val detourKm = detour.extraDistanceM / 1000
    val totalTripKm = baseKm + detourKm
    val detourFuelL = detourKm / e

    val inboundKm = detour.alongRouteM / 1000 + detourKm / 2
    val fuelOnArrivalL = ctx.vehicle.currentFuelL - inboundKm / e
    val reachable = fuelOnArrivalL + EPS >= minArrivalFuelL(ctx.vehicle)

    val maxFillableL = maxOf(0.0, ctx.vehicle.tankCapacityL - maxOf(0.0, fuelOnArrivalL))
    val desiredL = ctx.forcedLiters
        ?: desiredLitersForPolicy(ctx.preferences.fillPolicy, totalTripKm, ctx.vehicle, maxFillableL, effectivePrice)
    val litersToBuy = minOf(desiredL, maxFillableL)
    val tankCapped = desiredL > maxFillableL + 1e-6

    val fuelAtDestinationL = ctx.vehicle.currentFuelL + litersToBuy - totalTripKm / e
    val holdL = destinationHoldL(ctx.vehicle, ctx.preferences.fillPolicy)
    val surplusFuelL = maxOf(0.0, fuelAtDestinationL - holdL)
    val shortfallFuelL = maxOf(0.0, holdL - fuelAtDestinationL)

    val outOfPocketKrw = effectivePrice * litersToBuy
    val detourFuelCostKrw = effectivePrice * detourFuelL
    val congestion = maxOf(1.0, ctx.congestionFactor)
    val inflatedDurationS = detour.extraDurationS * congestion
    val timeCostKrw = (inflatedDurationS / 60) * ctx.preferences.timeValueKrwPerMin
    val tollDeltaKrw = detour.extraTollKrw
    val surplusCreditKrw = ctx.referencePriceKrwPerL * surplusFuelL
    val shortfallCostKrw = ctx.referencePriceKrwPerL * shortfallFuelL

    val normalizedCostKrw = outOfPocketKrw + timeCostKrw + tollDeltaKrw + shortfallCostKrw - surplusCreditKrw
    val usefulLiters = maxOf(0.0, litersToBuy - surplusFuelL)
    val krwPerUsefulLiter = if (usefulLiters > EPS) normalizedCostKrw / usefulLiters else 0.0

    val warnings = mutableListOf<Warning>()

    if (!reachable) {
        warnings.add(
            Warning(
                code = WarningCode.unreachable,
                severity = Severity.error,
                message = if (fuelOnArrivalL < 0) "현재 연료로는 도달 전에 ${-fuelOnArrivalL}L 부족합니다."
                else "예비 ${ctx.vehicle.reserveL}L 아래로 내려가 이 주유소는 제외합니다."
            )
        )
    } else if (fuelOnArrivalL < ctx.vehicle.tankCapacityL * LOW_MARGIN_RATIO) {
        warnings.add(
            Warning(
                code = WarningCode.lowMarginOnArrival,
                severity = Severity.warn,
                message = "도착 시 잔량이 ${String.format("%.1f", fuelOnArrivalL)}L뿐입니다. 정체나 경로 변경이 생기면 위험합니다."
            )
        )
    }

    if (tankCapped) {
        warnings.add(
            Warning(
                code = WarningCode.tankCapped,
                severity = Severity.info,
                message = "탱크 용량 때문에 ${String.format("%.1f", maxFillableL)}L까지만 주입됩니다."
            )
        )
    }

    if (shortfallFuelL > 0.05) {
        warnings.add(
            Warning(
                code = WarningCode.insufficientToDestination,
                severity = Severity.warn,
                message = "이 계획만으로는 목적지에서 예비량이 ${String.format("%.1f", shortfallFuelL)}L 부족합니다."
            )
        )
    }

    val ageH = parseIsoDate(station.priceUpdatedAt)?.let { updated ->
        (ctx.departAt.time - updated.time) / 3_600_000.0
    } ?: Double.NaN
    if (ageH.isFinite() && ageH > STALE_PRICE_HOURS) {
        warnings.add(
            Warning(
                code = WarningCode.stalePrice,
                severity = Severity.info,
                message = if (ageH < 24) "가격 신고가 ${ageH.toInt()}시간 전입니다."
                else "가격 신고가 ${(ageH / 24).toInt()}일 전입니다."
            )
        )
    }

    val arrivalAt = java.util.Date(
        ctx.departAt.time +
            ((ctx.route.durationS * (detour.alongRouteM / maxOf(1.0, ctx.route.distanceM)) + detour.extraDurationS) *
                congestion * 1000).toLong()
    )
    val reportedClosed = ctx.reports.any { it.stationId == station.id && (it.kind == ReportKind.closed || it.kind == ReportKind.gone) }
    if (reportedClosed || !isOpenAt(station, arrivalAt)) {
        val minutes = minutesOfDayInSeoul(arrivalAt)
        val hhmm = String.format("%02d:%02d", minutes / 60, minutes % 60)
        warnings.add(
            Warning(
                code = WarningCode.closedOnArrival,
                severity = Severity.error,
                message = "도착 예상 시각($hhmm KST)에 영업하지 않습니다."
            )
        )
    }

    if (station.accessHint?.oppositeSide == true && station.accessHint?.requiresHighwayExit == true) {
        warnings.add(
            Warning(
                code = WarningCode.oppositeSide,
                severity = Severity.warn,
                message = "반대편 차로입니다. 유턴할 수 없어 나들목까지 돌아 계산했습니다."
            )
        )
    } else if (station.accessHint?.oppositeSide == true) {
        warnings.add(
            Warning(
                code = WarningCode.oppositeSide,
                severity = Severity.warn,
                message = "진행 방향 반대편입니다. 교차로에서 돌아가야 할 수 있습니다."
            )
        )
    }
    if (station.accessHint?.requiresHighwayExit == true && station.accessHint?.oppositeSide != true) {
        warnings.add(
            Warning(
                code = WarningCode.highwayExit,
                severity = Severity.warn,
                message = "고속도로를 진출했다가 재진입해야 합니다."
            )
        )
    }
    if (detour.source == "geometric-estimate") {
        warnings.add(
            Warning(
                code = WarningCode.estimatedDetour,
                severity = Severity.info,
                message = "우회 거리는 도로망이 아닌 기하학적 추정치입니다."
            )
        )
    }
    if (congestion > 1.05) {
        warnings.add(
            Warning(
                code = WarningCode.congested,
                severity = Severity.info,
                message = "출발 시각 기준 정체를 반영해 우회 시간을 ${((congestion - 1) * 100).toInt()}% 늘여 계산했습니다."
            )
        )
    }
    for (report in ctx.reports) {
        if (report.stationId != station.id) continue
        warnings.add(
            Warning(
                code = WarningCode.userReported,
                severity = if (report.kind == ReportKind.priceMismatch) Severity.warn else Severity.error,
                message = when (report.kind) {
                    ReportKind.priceMismatch -> "이전에 현장 가격이 다르다고 제보한 곳입니다."
                    ReportKind.closed -> "영업하지 않는다고 제보한 곳입니다."
                    ReportKind.gone -> "폐업·이전으로 제보한 곳입니다."
                    else -> ""
                }
            )
        )
    }

    return RefuelOption(
        station = station,
        detour = detour,
        listPriceKrwPerL = listPrice,
        effectivePriceKrwPerL = effectivePrice,
        fuelOnArrivalL = fuelOnArrivalL,
        reachable = reachable,
        litersToBuy = litersToBuy,
        tankCapped = tankCapped,
        detourFuelL = detourFuelL,
        fuelAtDestinationL = fuelAtDestinationL,
        surplusFuelL = surplusFuelL,
        shortfallFuelL = shortfallFuelL,
        outOfPocketKrw = outOfPocketKrw,
        detourFuelCostKrw = detourFuelCostKrw,
        timeCostKrw = timeCostKrw,
        tollDeltaKrw = tollDeltaKrw,
        surplusCreditKrw = surplusCreditKrw,
        shortfallCostKrw = shortfallCostKrw,
        normalizedCostKrw = normalizedCostKrw,
        krwPerUsefulLiter = krwPerUsefulLiter,
        warnings = warnings,
    )
}

fun breakEvenDetourKm(
    gainPerLiterKrw: Double,
    litersToBuy: Double,
    effectivePriceKrwPerL: Double,
    kmPerLiter: Double,
    timeValueKrwPerMin: Double,
    detourSpeedKmh: Double,
): Double {
    val grossGain = gainPerLiterKrw * litersToBuy
    if (grossGain <= 0) return 0.0
    val fuelCostPerKm = effectivePriceKrwPerL / kmPerLiter
    val minutesPerKm = if (detourSpeedKmh > 0) 60 / detourSpeedKmh else 0.0
    val costPerKm = fuelCostPerKm + timeValueKrwPerMin * minutesPerKm
    if (costPerKm <= EPS) return Double.POSITIVE_INFINITY
    return grossGain / costPerKm
}

fun median(values: List<Double>): Double {
    if (values.isEmpty()) return 0.0
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2 else sorted[mid]
}