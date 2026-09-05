package com.fueloptimizer.domain

import kotlin.math.min

/** 나들목까지 갔다가 반대편으로 붙는 최소 왕복 (m). */
private const val HIGHWAY_TURNAROUND_M = 4_200.0
private const val HIGHWAY_TURNAROUND_AHEAD_M = 2_800.0
private const val HIGHWAY_TURNAROUND_SPEED_KMH = 50.0
private const val HIGHWAY_TURNAROUND_DELAY_S = 240.0
private const val HIGHWAY_REENTRY_TOLL_KRW = 900.0

fun routeLooksDivided(route: Route): Boolean {
    if (route.summary?.contains("고속") == true) return true
    val hours = route.durationS / 3600.0
    if (hours <= 0) return false
    return route.distanceM / 1000.0 / hours >= 68.0
}

/** 경로 왼쪽·본선 이탈로 접근 힌트를 채운다. */
fun inferAccessHint(
    proj: Projection,
    route: Route,
    existing: AccessHint? = null,
): AccessHint {
    val divided = routeLooksDivided(route)
    val oppositeSide = existing?.oppositeSide
        ?: (proj.side > 0 && proj.offsetM >= 25 && proj.offsetM <= 380)
    val onHighway = existing?.onHighway
        ?: (divided && proj.offsetM < 260 && !oppositeSide)
    val requiresHighwayExit = existing?.requiresHighwayExit
        ?: (divided && (oppositeSide || (!onHighway && proj.offsetM > 320)))
    return AccessHint(oppositeSide, requiresHighwayExit, onHighway)
}

fun withAccessHint(station: Station, proj: Projection, route: Route): Station {
    return station.copy(accessHint = inferAccessHint(proj, route, station.accessHint))
}

/** 반대편인데 우회가 왕복 이탈거리 수준이면, 길찾기가 유턴을 넣은 것이다. */
fun looksLikeIllegalUturn(
    detour: Detour,
    proj: Projection,
    hint: AccessHint?,
    route: Route,
): Boolean {
    if (hint?.oppositeSide != true) return false
    if (hint.requiresHighwayExit != true && !routeLooksDivided(route)) return false
    val medianHopM = proj.offsetM * 2 + 700
    return detour.extraDistanceM <= medianHopM
}

fun highwayTurnaroundDetour(proj: Projection, route: Route, station: LatLng): Detour {
    val extraDistanceM = proj.offsetM * 2 * 1.35 + HIGHWAY_TURNAROUND_M
    val extraDurationS = extraDistanceM / 1000.0 / HIGHWAY_TURNAROUND_SPEED_KMH * 3600.0 +
        HIGHWAY_TURNAROUND_DELAY_S
    val turnaroundAlongM = min(route.distanceM, proj.alongM + HIGHWAY_TURNAROUND_AHEAD_M)
    val joinPoint = pointAtDistance(route.polyline, turnaroundAlongM)
    return Detour(
        extraDistanceM = extraDistanceM,
        extraDurationS = extraDurationS,
        extraTollKrw = HIGHWAY_REENTRY_TOLL_KRW,
        alongRouteM = proj.alongM,
        offRouteM = proj.offsetM,
        joinPoint = joinPoint,
        source = "geometric-estimate",
        viaPolyline = viaRoutePolyline(route.polyline, station, joinPoint, turnaroundAlongM),
    )
}

fun rejectIllegalUturn(detour: Detour, proj: Projection, station: Station, route: Route): Detour {
    val hint = station.accessHint
    if (!looksLikeIllegalUturn(detour, proj, hint, route)) return detour
    return highwayTurnaroundDetour(proj, route, PointLatLng(station.lat, station.lng))
}
