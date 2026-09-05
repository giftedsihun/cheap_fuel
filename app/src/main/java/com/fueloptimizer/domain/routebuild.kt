package com.fueloptimizer.domain

/**
 * 두 지점을 잇는 근사 경로.
 * 카카오 길찾기 키가 없을 때 사용자가 고른 출발·도착을 버리기보다
 * 직선 보간으로라도 회랑 검색이 돌아가게 한다. 배로만 이어진 구간(제주 등)은
 * 직선으로 잇지 않는다.
 */
fun interpolateRoute(origin: NamedPlace, destination: NamedPlace): Route {
    val blocked = carUnreachableReason(origin, destination)
    if (blocked != null) throw IllegalArgumentException(blocked)
    val steps = 24
    val polyline = mutableListOf<NamedPlace>()
    for (i in 0..steps) {
        val t = i.toDouble() / steps
        polyline.add(
            NamedPlace(
                lat = origin.lat + (destination.lat - origin.lat) * t,
                lng = origin.lng + (destination.lng - origin.lng) * t,
                name = "",
            ),
        )
    }
    val distanceM = polylineLengthM(polyline)
    val avgSpeedKmh = 70.0
    return Route(
        id = "custom:${origin.name}->${destination.name}",
        origin = origin,
        destination = destination,
        polyline = polyline,
        distanceM = distanceM,
        durationS = distanceM / 1000.0 / avgSpeedKmh * 3600.0,
        tollKrw = 0.0,
        summary = "직선 근사 경로 (실도로 아님)",
        driveable = true,
    )
}

fun isStraightFallbackRoute(summary: String?): Boolean {
    return summary?.contains("직선 근사") == true
}

fun noteKakaoRouteFailure(route: Route, code: String): Route {
    val detail = when {
        code == "http-429" || code == "http-403" -> "카카오 일일 한도 또는 권한 오류"
        code == "http-401" -> "카카오 REST 키 오류"
        code == "http-599" -> "카카오 응답이 늦어 시간 초과"
        code.startsWith("code-") -> "이 구간 자동차 경로를 찾지 못함"
        else -> "카카오 길찾기 실패"
    }
    return route.copy(summary = "직선 근사 경로 (실도로 아님 · $detail)")
}

/** 바다로 끊긴 출발·도착. 지도에는 점만 찍고 선은 그리지 않는다. */
fun disconnectedEndpointsRoute(origin: NamedPlace, destination: NamedPlace): Route {
    return Route(
        id = "unreachable:${origin.name}->${destination.name}",
        origin = origin,
        destination = destination,
        polyline = listOf(origin, destination),
        distanceM = 0.0,
        durationS = 0.0,
        tollKrw = 0.0,
        summary = "자동차 경로 없음",
        driveable = false,
    )
}
