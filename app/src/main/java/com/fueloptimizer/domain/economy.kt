package com.fueloptimizer.domain

import kotlin.math.round

/**
 * 주유 기록으로 실주행 연비를 추정한다.
 * 기록은 기기 안에만 둔다. 최근 기록에 더 큰 가중치를 주는 가중평균이다.
 * 기록이 두 건 미만이면 null을 돌려 사용자가 넣은 값을 유지한다.
 */
fun learnedKmPerLiter(records: List<FillRecord>): Double? {
    val valid = records
        .filter { it.liters > 0.5 && it.kmDriven > 3 }
        .sortedBy { it.at }
    if (valid.size < 2) return null
    var weighted = 0.0
    var weights = 0.0
    valid.forEachIndexed { index, record ->
        val weight = (index + 1).toDouble()
        weighted += (record.kmDriven / record.liters) * weight
        weights += weight
    }
    val estimate = weighted / weights
    if (!estimate.isFinite() || estimate < 3 || estimate > 40) return null
    return round(estimate * 10) / 10
}

fun fillEconomy(record: FillRecord): Double {
    return if (record.liters > 0) record.kmDriven / record.liters else 0.0
}
