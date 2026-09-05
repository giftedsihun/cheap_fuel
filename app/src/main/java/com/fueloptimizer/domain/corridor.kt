package com.fueloptimizer.domain

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** 오피넷 `aroundAll.do`의 반경 상한 (m) */
const val STATION_SEARCH_MAX_RADIUS_M = 5000.0

/** 회랑 반폭은 검색 반경보다 이 비율까지만 허용한다. */
private const val MAX_HALF_WIDTH_RATIO = 0.9

/** 계산한 간격에 곱하는 안전 계수. */
private const val SAFETY_FACTOR = 0.9

/** 너무 촘촘해져 호출이 폭발하지 않도록 두는 최소 간격 (m) */
private const val MIN_SAMPLE_INTERVAL_M = 600.0

/** 꺾임각을 잴 때 정점 양쪽에서 이 거리만큼 떨어진 점을 쓴다. */
private const val TURN_LEG_M = 120.0

data class CorridorSearchPlan(
    /** 각 호출에 사용할 반경 (m) */
    val searchRadiusM: Double,
    /** 실제로 빈틈 없이 덮이는 회랑 반폭 (m) */
    val coveredHalfWidthM: Double,
    /** 경로 위 샘플 간격 (m) */
    val intervalM: Double,
    /** 예상 호출 횟수 */
    val callCount: Int,
    /** 요청한 회랑 반폭이 반경 상한 때문에 잘렸는가 */
    val truncated: Boolean,
    /** 계획에 반영한 경로 최대 꺾임각 (도) */
    val maxTurnDeg: Double,
)

data class CorridorSample(val point: LatLng, val alongM: Double)

private fun indexAtLeastM(cum: List<Double>, fromIndex: Int, direction: Int, minM: Double): Int {
    val start = cum.getOrElse(fromIndex) { 0.0 }
    var i = fromIndex
    while (i + direction >= 0 && i + direction < cum.size) {
        i += direction
        if (abs(cum.getOrElse(i) { 0.0 } - start) >= minM) return i
    }
    return i
}

private fun turnAngleAtVertex(polyline: List<LatLng>, i: Int, cum: List<Double>): Double {
    val prev = indexAtLeastM(cum, i, -1, TURN_LEG_M)
    val next = indexAtLeastM(cum, i, 1, TURN_LEG_M)
    if (prev == i || next == i) return 0.0
    val a = polyline[prev]
    val b = polyline[i]
    val c = polyline[next]
    val ab = haversineM(a, b)
    val bc = haversineM(b, c)
    val ac = haversineM(a, c)
    if (ab == 0.0 || bc == 0.0) return 0.0
    val cosInterior = (ab * ab + bc * bc - ac * ac) / (2 * ab * bc)
    val interior = acos(cosInterior.coerceIn(-1.0, 1.0))
    return PI - interior
}

/** 폴리라인에서 가장 급한 꺾임각 (라디안). 직선이면 0. */
fun maxTurnAngleRad(polyline: List<LatLng>): Double {
    val cum = cumulativeDistances(polyline)
    var worst = 0.0
    for (i in 1 until polyline.size - 1) {
        worst = max(worst, turnAngleAtVertex(polyline, i, cum))
    }
    return worst
}

/** [fromM, toM] 구간의 정점 꺾임 중 가장 급한 각. */
fun maxTurnInRangeRad(
    polyline: List<LatLng>,
    fromM: Double,
    toM: Double,
    cum: List<Double> = cumulativeDistances(polyline),
): Double {
    var worst = 0.0
    for (i in 1 until polyline.size - 1) {
        if (cum[i] < fromM || cum[i] > toM) continue
        worst = max(worst, turnAngleAtVertex(polyline, i, cum))
    }
    return worst
}

/** 회랑을 빈틈 없이 덮는 검색 중심점. */
fun sampleCorridorCenters(
    polyline: List<LatLng>,
    halfWidthM: Double,
    searchRadiusM: Double = STATION_SEARCH_MAX_RADIUS_M,
): List<CorridorSample> {
    if (polyline.isEmpty()) return emptyList()
    val cum = cumulativeDistances(polyline)
    val total = cum.lastOrNull() ?: 0.0
    if (total <= 0) return listOf(CorridorSample(polyline[0], 0.0))
    val straightStep = corridorSampleIntervalM(searchRadiusM, halfWidthM, 0.0)
    val out = mutableListOf<CorridorSample>()
    var d = 0.0
    while (d < total - 1) {
        out.add(CorridorSample(pointAtDistance(polyline, d, cum), d))
        val lookAheadTo = min(total, d + straightStep)
        val localTurn = maxTurnInRangeRad(polyline, d, lookAheadTo, cum)
        d += corridorSampleIntervalM(searchRadiusM, halfWidthM, localTurn)
    }
    val end = polyline.last()
    val last = out.lastOrNull()
    if (last == null || abs(last.alongM - total) > 1) {
        out.add(CorridorSample(end, total))
    }
    return out
}

/** 회랑 반폭 halfWidthM을 빈틈 없이 덮는 샘플 간격 (m). */
fun corridorSampleIntervalM(
    searchRadiusM: Double,
    halfWidthM: Double,
    turnRad: Double = 0.0,
): Double {
    val w = min(halfWidthM, searchRadiusM * MAX_HALF_WIDTH_RATIO)
    val bend = sin(turnRad.coerceIn(0.0, PI).coerceAtMost(PI) / 2)
    val inner = w * w * bend * bend + searchRadiusM * searchRadiusM - w * w
    val raw = 2 * (-w * bend + sqrt(max(0.0, inner)))
    return max(MIN_SAMPLE_INTERVAL_M, raw * SAFETY_FACTOR)
}

fun planCorridorSearch(
    polyline: List<LatLng>,
    requestedHalfWidthM: Double,
    maxRadiusM: Double = STATION_SEARCH_MAX_RADIUS_M,
): CorridorSearchPlan {
    val cum = cumulativeDistances(polyline)
    val routeDistanceM = cum.lastOrNull() ?: 0.0
    val searchRadiusM = maxRadiusM
    val maxHalfWidthM = searchRadiusM * MAX_HALF_WIDTH_RATIO
    val coveredHalfWidthM = min(requestedHalfWidthM, maxHalfWidthM)
    val turnRad = maxTurnAngleRad(polyline)
    val intervalM = corridorSampleIntervalM(searchRadiusM, coveredHalfWidthM, turnRad)
    val adaptiveCount = sampleCorridorCenters(polyline, coveredHalfWidthM, searchRadiusM).size
    return CorridorSearchPlan(
        searchRadiusM = searchRadiusM,
        coveredHalfWidthM = coveredHalfWidthM,
        intervalM = intervalM,
        callCount = adaptiveCount,
        truncated = requestedHalfWidthM > maxHalfWidthM,
        maxTurnDeg = turnRad * 180.0 / PI,
    )
}
