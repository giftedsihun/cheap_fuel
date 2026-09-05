package com.fueloptimizer.domain

import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/**
 * 출발 시각에 따른 정체 배수.
 * 카카오 미래운행정보가 있으면 그 값을 쓰고, 없으면 평일 출퇴근 시간대만
 * 거친 계수로 우회 시간과 도착 시각을 늘린다. 거리는 바꾸지 않는다.
 */
fun congestionFactorAt(departAt: Date, highwayHeavy: Boolean): Double {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul")).apply { time = departAt }
    val dow = cal.get(Calendar.DAY_OF_WEEK)
    val weekend = dow == Calendar.SATURDAY || dow == Calendar.SUNDAY
    val hour = cal.get(Calendar.HOUR_OF_DAY)
    if (weekend) {
        if (hour in 11..17) return if (highwayHeavy) 1.08 else 1.12
        return 1.0
    }
    if (hour in 7..8) return if (highwayHeavy) 1.18 else 1.45
    if (hour in 17..19) return if (highwayHeavy) 1.22 else 1.5
    if (hour in 9..10) return if (highwayHeavy) 1.06 else 1.15
    return 1.0
}
