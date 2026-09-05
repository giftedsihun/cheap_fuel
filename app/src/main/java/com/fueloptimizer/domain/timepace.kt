package com.fueloptimizer.domain

/** 화면에는 촉박/적당/여유만 보여 주고, 순위 계산에는 원/분으로 환산한다. */
enum class TimePace { rushed, normal, relaxed }

data class PaceInfo(val label: String, val krwPerMin: Double, val hint: String)

val TIME_PACE: Map<TimePace, PaceInfo> = mapOf(
    TimePace.rushed to PaceInfo("촉박", 400.0, "우회 시간을 비싸게 봅니다. 가까운 곳이 유리해집니다."),
    TimePace.normal to PaceInfo("적당", 150.0, "가격 차이가 분명하면 우회합니다."),
    TimePace.relaxed to PaceInfo("여유", 40.0, "조금 돌아가도 싼 곳을 고릅니다."),
)

val TIME_PACE_ORDER = listOf(TimePace.rushed, TimePace.normal, TimePace.relaxed)

fun timePaceFromKrw(krwPerMin: Double): TimePace {
    if (krwPerMin >= 275) return TimePace.rushed
    if (krwPerMin <= 80) return TimePace.relaxed
    return TimePace.normal
}
