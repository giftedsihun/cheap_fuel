package com.fueloptimizer.domain

import kotlin.math.max
import kotlin.math.min

/**
 * 탱크 제약 아래의 최소 비용 주유 순서 (Gas Station Problem greedy).
 * - 출발지에서는 넣을 수 없다. 현재 연료로 닿는 곳 중 단가가 가장 싼 곳에 선다.
 * - 가득 채우면 닿는 후보 중 더 싼 곳이 있으면, 그중 가장 앞선 곳까지 갈 만큼만 넣는다.
 * - 더 싼 곳이 없으면 가득 채우고, 닿는 곳 중 가장 싼 곳으로 간다.
 * - 마지막 정류에서 목적지 예비량을 채울 수 있으면 일정을 끝낸다.
 */
private const val TRANSIT_RESERVE_L = 0.8

data class ItineraryInput(
    val route: Route,
    val vehicle: Vehicle,
    val options: List<RankedOption>,
    val fillFullAtLast: Boolean,
    /** 목적지 도착 시 남기고 싶은 양. 없으면 reserveL */
    val destinationHoldL: Double? = null,
)

fun travelKmBetween(fromAlongM: Double, fromExtraM: Double, toAlongM: Double, toExtraM: Double): Double {
    return max(0.0, toAlongM - fromAlongM) / 1000.0 + fromExtraM / 2000.0 + toExtraM / 2000.0
}

fun planItinerary(input: ItineraryInput): List<ItineraryStop> {
    val route = input.route
    val vehicle = input.vehicle
    val options = input.options
    val e = vehicle.kmPerLiter
    val destAlong = route.distanceM
    val destHoldL = input.destinationHoldL ?: vehicle.reserveL
    fun destNeedL(alongM: Double, extraM: Double): Double {
        return travelKmBetween(alongM, extraM, destAlong, 0.0) / e + destHoldL
    }
    val sorted = options.sortedBy { it.detour.alongRouteM }
    if (sorted.isEmpty()) return emptyList()
    if (vehicle.currentFuelL >= destNeedL(0.0, 0.0)) return emptyList()

    val reachableNow = sorted.filter { option ->
        val need = travelKmBetween(0.0, 0.0, option.detour.alongRouteM, option.detour.extraDistanceM) / e +
            TRANSIT_RESERVE_L
        vehicle.currentFuelL >= need
    }
    if (reachableNow.isEmpty()) return emptyList()

    var current = reachableNow.minByOrNull { it.effectivePriceKrwPerL } ?: return emptyList()
    var fuel = vehicle.currentFuelL -
        travelKmBetween(0.0, 0.0, current.detour.alongRouteM, current.detour.extraDistanceM) / e

    val stops = mutableListOf<ItineraryStop>()
    fun pushStop(option: RankedOption, liters: Double, fillReason: String, arrivalFuel: Double): Double {
        val litersToBuy = max(0.0, min(liters, vehicle.tankCapacityL - max(0.0, arrivalFuel)))
        stops.add(
            ItineraryStop(
                option = option,
                litersToBuy = litersToBuy,
                fillReason = fillReason,
                outOfPocketKrw = option.effectivePriceKrwPerL * litersToBuy,
                fuelOnArrivalL = arrivalFuel,
                fuelOnDepartL = arrivalFuel + litersToBuy,
            ),
        )
        return arrivalFuel + litersToBuy
    }

    var guard = 0
    while (guard < 16) {
        guard += 1
        val along = current.detour.alongRouteM
        val extra = current.detour.extraDistanceM
        val maxFillable = max(0.0, vehicle.tankCapacityL - max(0.0, fuel))
        val remainToDest = destNeedL(along, extra) - max(0.0, fuel)

        if (remainToDest <= maxFillable + 1e-6) {
            val liters = if (input.fillFullAtLast) maxFillable else max(0.0, remainToDest)
            pushStop(current, liters, if (stops.isEmpty()) "only-stop" else "last-stop", fuel)
            break
        }

        val visited = stops.map { it.option.station.id }.toSet()
        val onward = sorted.filter { option ->
            if (option.station.id in visited) return@filter false
            if (option.station.id == current.station.id) return@filter false
            if (option.detour.alongRouteM <= along + 80) return@filter false
            val need = travelKmBetween(along, extra, option.detour.alongRouteM, option.detour.extraDistanceM) / e +
                TRANSIT_RESERVE_L
            vehicle.tankCapacityL >= need
        }

        if (onward.isEmpty()) {
            pushStop(current, maxFillable, "fill-full", fuel)
            break
        }

        val cheaper = onward.filter { it.effectivePriceKrwPerL < current.effectivePriceKrwPerL - 0.5 }
        if (cheaper.isNotEmpty()) {
            val next = cheaper[0]
            val need = travelKmBetween(along, extra, next.detour.alongRouteM, next.detour.extraDistanceM) / e +
                TRANSIT_RESERVE_L
            fuel = pushStop(current, max(0.0, need - fuel), "enough-for-next", fuel)
            fuel -= travelKmBetween(along, extra, next.detour.alongRouteM, next.detour.extraDistanceM) / e
            current = next
            continue
        }

        val next = onward.minByOrNull { it.effectivePriceKrwPerL } ?: break
        fuel = pushStop(current, maxFillable, "fill-full", fuel)
        fuel -= travelKmBetween(along, extra, next.detour.alongRouteM, next.detour.extraDistanceM) / e
        current = next
    }
    return stops
}

fun itineraryOutOfPocket(stops: List<ItineraryStop>): Double {
    return stops.sumOf { it.outOfPocketKrw }
}
