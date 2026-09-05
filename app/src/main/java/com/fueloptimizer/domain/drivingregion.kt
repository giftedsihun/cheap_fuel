package com.fueloptimizer.domain

/**
 * 한국에서 자동차 도로로 이어지지 않은 권역.
 * 연륙교가 있는 거제·남해·진도·완도·강화·영종은 육지로 본다.
 */
enum class DrivingRegion {
    mainland, jeju, udo, marado, chuja, ulleung, dokdo,
    baengnyeong, daecheong, yeonpyeong, heuksan, hongdo, geomun, tsushima, water,
}

private data class Box(
    val id: DrivingRegion,
    val minLat: Double,
    val maxLat: Double,
    val minLng: Double,
    val maxLng: Double,
)

/** 좁은 섬을 먼저 본다. 제주 박스 안의 우도·마라도가 묻히지 않게. */
private val ISLANDS = listOf(
    Box(DrivingRegion.dokdo, 37.236, 37.25, 131.86, 131.88),
    Box(DrivingRegion.ulleung, 37.45, 37.56, 130.78, 130.95),
    Box(DrivingRegion.baengnyeong, 37.9, 38.03, 124.6, 124.8),
    Box(DrivingRegion.daecheong, 37.8, 37.86, 124.68, 124.78),
    Box(DrivingRegion.yeonpyeong, 37.64, 37.71, 125.68, 125.76),
    Box(DrivingRegion.hongdo, 34.66, 34.72, 125.17, 125.22),
    Box(DrivingRegion.heuksan, 34.64, 34.73, 125.38, 125.48),
    Box(DrivingRegion.geomun, 34.0, 34.08, 127.26, 127.35),
    Box(DrivingRegion.chuja, 33.9, 34.03, 126.28, 126.38),
    Box(DrivingRegion.marado, 33.1, 33.135, 126.25, 126.29),
    Box(DrivingRegion.udo, 33.49, 33.525, 126.945, 126.98),
    Box(DrivingRegion.jeju, 33.19, 33.58, 126.14, 126.97),
    Box(DrivingRegion.tsushima, 34.02, 34.72, 129.16, 129.52),
)

private val JEJU_FAMILY = setOf(
    DrivingRegion.jeju, DrivingRegion.udo, DrivingRegion.marado, DrivingRegion.chuja,
)
private val EAST_ISLANDS = setOf(DrivingRegion.ulleung, DrivingRegion.dokdo)

const val UNREACHABLE_BY_CAR_CODE = "UNREACHABLE_BY_CAR"

private fun inBox(p: LatLng, box: Box): Boolean {
    return p.lat >= box.minLat && p.lat <= box.maxLat &&
        p.lng >= box.minLng && p.lng <= box.maxLng
}

private fun islandAt(p: LatLng): DrivingRegion? {
    for (box in ISLANDS) {
        if (inBox(p, box)) return box.id
    }
    return null
}

/** 제주해협·울릉 앞바다처럼 도로가 없는 수역. 연안 도로는 넣지 않는다. */
private fun inKnownStrait(p: LatLng): Boolean {
    if (islandAt(p) != null) return false
    if (p.lat in 33.56..34.22 && p.lng in 125.85..127.95) return true
    if (p.lat in 36.6..37.8 && p.lng in 130.15..131.7) return true
    return false
}

private fun inMainland(p: LatLng): Boolean {
    if (p.lat < 34.27 || p.lat > 38.65) return false
    if (p.lng < 125.05 || p.lng > 129.58) return false
    return true
}

fun drivingRegion(point: LatLng): DrivingRegion {
    val island = islandAt(point)
    if (island != null) return island
    if (inKnownStrait(point)) return DrivingRegion.water
    if (inMainland(point)) return DrivingRegion.mainland
    return DrivingRegion.water
}

fun carUnreachableReason(origin: LatLng, destination: LatLng): String? {
    val from = drivingRegion(origin)
    val to = drivingRegion(destination)
    if (from == to && from != DrivingRegion.water) return null
    if (from == DrivingRegion.water || to == DrivingRegion.water) {
        return "출발지나 도착지가 바다 위입니다. 육지의 지점을 골라 주세요."
    }
    if (from in JEJU_FAMILY || to in JEJU_FAMILY) {
        return "제주도는 배로만 갈 수 있습니다. 자동차 경로는 만들지 않습니다."
    }
    if (from in EAST_ISLANDS || to in EAST_ISLANDS) {
        return "울릉도·독도는 자동차 도로로 이어지지 않습니다."
    }
    return "출발과 도착이 바다로 나뉘어 자동차로는 갈 수 없습니다."
}

fun polylineUnreachableReason(polyline: List<LatLng>): String? {
    if (polyline.size < 2) return null
    val ends = carUnreachableReason(polyline.first(), polyline.last())
    if (ends != null) return ends
    for (i in 1 until polyline.size) {
        val a = polyline[i - 1]
        val b = polyline[i]
        val mid = PointLatLng((a.lat + b.lat) / 2, (a.lng + b.lng) / 2)
        if (drivingRegion(a) == DrivingRegion.water || drivingRegion(b) == DrivingRegion.water) {
            return "이 경로는 바다를 가로지릅니다. 자동차로는 갈 수 없습니다."
        }
        val span = haversineM(a, b)
        if (span >= 8_000 && drivingRegion(mid) == DrivingRegion.water) {
            return "이 경로는 바다를 가로지릅니다. 자동차로는 갈 수 없습니다."
        }
    }
    return null
}

fun isUnreachableByCarMessage(message: String): Boolean {
    return message.contains("배로만") || message.contains("바다로 나뉘") ||
        message.contains("바다 위") || message.contains("바다를 가로지") ||
        message.contains("자동차 도로로 이어지지")
}
