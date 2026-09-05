package com.fueloptimizer.domain

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

const val EARTH_RADIUS_M = 6_371_008.8
private const val DEG = Math.PI / 180.0

fun haversineM(a: LatLng, b: LatLng): Double {
    val dLat = (b.lat - a.lat) * DEG
    val dLng = (b.lng - a.lng) * DEG
    val lat1 = a.lat * DEG
    val lat2 = b.lat * DEG
    val h = sin(dLat / 2) * sin(dLat / 2) +
        cos(lat1) * cos(lat2) * sin(dLng / 2) * sin(dLng / 2)
    return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(h)))
}

private fun toLocalMeters(p: LatLng, originLat: Double): Pair<Double, Double> {
    return Pair(
        p.lng * DEG * EARTH_RADIUS_M * cos(originLat * DEG),
        p.lat * DEG * EARTH_RADIUS_M,
    )
}

/** 폴리라인의 각 정점까지의 누적 거리(m). 길이는 polyline과 같다. */
fun cumulativeDistances(polyline: List<LatLng>): List<Double> {
    if (polyline.isEmpty()) return emptyList()
    val out = MutableList(polyline.size) { 0.0 }
    for (i in 1 until polyline.size) {
        out[i] = out[i - 1] + haversineM(polyline[i - 1], polyline[i])
    }
    return out
}

fun polylineLengthM(polyline: List<LatLng>): Double {
    return cumulativeDistances(polyline).lastOrNull() ?: 0.0
}

data class Projection(
    /** 폴리라인 위의 가장 가까운 점 */
    val point: LatLng,
    /** 그 점까지의 경로 누적 거리 (m) */
    val alongM: Double,
    /** 대상 점에서 폴리라인까지의 직선 거리 (m) */
    val offsetM: Double,
    /** 수선이 내려간 구간의 인덱스 */
    val segmentIndex: Int,
    /** 진행 방향 기준 좌우. 양수면 진행 방향의 왼쪽(한국은 대체로 반대편 차선) */
    val side: Double,
)

/** 점을 폴리라인에 수선으로 투영한다. */
fun projectOntoPolyline(
    target: LatLng,
    polyline: List<LatLng>,
    cum: List<Double> = cumulativeDistances(polyline),
): Projection {
    val originLat = target.lat
    val (tx, ty) = toLocalMeters(target, originLat)
    var best: Projection? = null
    for (i in 0 until polyline.size - 1) {
        val (ax, ay) = toLocalMeters(polyline[i], originLat)
        val (bx, by) = toLocalMeters(polyline[i + 1], originLat)
        val abx = bx - ax
        val aby = by - ay
        val lenSq = abx * abx + aby * aby
        if (lenSq == 0.0) continue
        var u = ((tx - ax) * abx + (ty - ay) * aby) / lenSq
        u = u.coerceIn(0.0, 1.0)
        val px = ax + u * abx
        val py = ay + u * aby
        val offsetM = hypot(tx - px, ty - py)
        if (best != null && offsetM >= best.offsetM) continue
        val segLenM = cum[i + 1] - cum[i]
        val cross = abx * (ty - ay) - aby * (tx - ax)
        best = Projection(
            point = PointLatLng(
                lat = polyline[i].lat + u * (polyline[i + 1].lat - polyline[i].lat),
                lng = polyline[i].lng + u * (polyline[i + 1].lng - polyline[i].lng),
            ),
            alongM = cum[i] + u * segLenM,
            offsetM = offsetM,
            segmentIndex = i,
            side = cross.sign,
        )
    }
    return best ?: Projection(
        point = polyline[0],
        alongM = 0.0,
        offsetM = haversineM(target, polyline[0]),
        segmentIndex = 0,
        side = 0.0,
    )
}

/** 경로 시작점에서 distanceM 만큼 진행한 지점의 좌표. */
fun pointAtDistance(
    polyline: List<LatLng>,
    distanceM: Double,
    cum: List<Double> = cumulativeDistances(polyline),
): LatLng {
    val total = cum.lastOrNull() ?: 0.0
    val d = distanceM.coerceIn(0.0, total)
    for (i in 0 until polyline.size - 1) {
        if (d <= cum[i + 1]) {
            val segLen = cum[i + 1] - cum[i]
            val u = if (segLen == 0.0) 0.0 else (d - cum[i]) / segLen
            return PointLatLng(
                lat = polyline[i].lat + u * (polyline[i + 1].lat - polyline[i].lat),
                lng = polyline[i].lng + u * (polyline[i + 1].lng - polyline[i].lng),
            )
        }
    }
    return polyline.last()
}

/** 경로를 일정 간격으로 샘플링한다. */
fun sampleAlongRoute(
    polyline: List<LatLng>,
    intervalM: Double,
    cum: List<Double> = cumulativeDistances(polyline),
): List<Pair<LatLng, Double>> {
    val total = cum.lastOrNull() ?: 0.0
    val out = mutableListOf<Pair<LatLng, Double>>()
    var d = 0.0
    while (d < total) {
        out.add(Pair(pointAtDistance(polyline, d, cum), d))
        d += intervalM
    }
    out.add(Pair(polyline.last(), total))
    return out
}

/** 경로를 누적 거리 구간으로 잘라 낸다. 양 끝은 보간한다. */
fun slicePolylineByDistance(
    polyline: List<LatLng>,
    fromM: Double,
    toM: Double,
    cum: List<Double> = cumulativeDistances(polyline),
): List<LatLng> {
    if (polyline.isEmpty()) return emptyList()
    val total = cum.lastOrNull() ?: 0.0
    val start = fromM.coerceIn(0.0, total)
    val end = toM.coerceIn(start, total)
    val out = mutableListOf(pointAtDistance(polyline, start, cum))
    for (i in polyline.indices) {
        if (cum[i] > start + 0.5 && cum[i] < end - 0.5) out.add(polyline[i])
    }
    val last = pointAtDistance(polyline, end, cum)
    val prev = out.lastOrNull()
    if (prev == null || haversineM(prev, last) > 0.5) out.add(last)
    return out
}

/**
 * 본선을 따라가다 주유소에 들렀다가 다시 본선으로 합류하는 경유 경로.
 * 실도로 길찾기 형상이 없을 때 지도용으로 쓴다.
 */
fun viaRoutePolyline(
    routePolyline: List<LatLng>,
    station: LatLng,
    joinPoint: LatLng,
    alongM: Double,
): List<LatLng> {
    val cum = cumulativeDistances(routePolyline)
    val total = cum.lastOrNull() ?: 0.0
    val before = slicePolylineByDistance(routePolyline, 0.0, alongM, cum)
    val spur = curveBetween(joinPoint, station)
    val back = curveBetween(station, joinPoint).drop(1)
    val after = slicePolylineByDistance(routePolyline, alongM, total, cum).drop(1)
    return before + spur.drop(1) + back + after
}

fun curveBetween(a: LatLng, b: LatLng, bulge: Double = 0.18): List<LatLng> {
    val mid = PointLatLng(
        lat = (a.lat + b.lat) / 2 - (b.lng - a.lng) * bulge,
        lng = (a.lng + b.lng) / 2 + (b.lat - a.lat) * bulge,
    )
    val out = mutableListOf<LatLng>()
    val steps = 12
    for (i in 0..steps) {
        val t = i.toDouble() / steps
        val w0 = (1 - t) * (1 - t)
        val w1 = 2 * (1 - t) * t
        val w2 = t * t
        out.add(
            PointLatLng(
                lat = w0 * a.lat + w1 * mid.lat + w2 * b.lat,
                lng = w0 * a.lng + w1 * mid.lng + w2 * b.lng,
            ),
        )
    }
    return out
}
