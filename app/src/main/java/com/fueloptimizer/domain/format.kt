package com.fueloptimizer.domain

import java.text.NumberFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val krwFormat: NumberFormat = NumberFormat.getNumberInstance(Locale.KOREA).apply {
    maximumFractionDigits = 0
}

fun formatKrw(v: Double): String = krwFormat.format(kotlin.math.round(v).toLong()) + "원"

fun formatSignedKrw(v: Double): String {
    val rounded = kotlin.math.round(v).toLong()
    return (if (rounded >= 0) "+" else "−") + krwFormat.format(kotlin.math.abs(rounded)) + "원"
}

fun formatPerLiter(v: Double): String = krwFormat.format(kotlin.math.round(v).toLong()) + "원/L"

fun formatLiters(v: Double): String = String.format(Locale.KOREA, "%.1fL", v)

fun formatKm(v: Double): String = String.format(Locale.KOREA, "%.1fkm", v)

fun formatMinutes(v: Double): String = "${kotlin.math.round(v).toInt()}분"

fun formatSignedMinutes(v: Double): String {
    val m = kotlin.math.round(v).toInt()
    return (if (m >= 0) "+" else "−") + "${kotlin.math.abs(m)}분"
}

fun cashCostKrw(option: RefuelOption): Double = option.normalizedCostKrw - option.timeCostKrw

fun displayStationName(name: String): String =
    name.replace("주식회사", "").replace("㈜", "").replace("(주)", "").trim()

fun stationTradeName(brand: Brand, name: String): String {
    var n = displayStationName(name)
    val prefixes = listOf("SK에너지", "GS칼텍스", "현대오일뱅크", "S-OIL", "에쓰오일", "자영알뜰", "고속도로알뜰", "농협알뜰", "알뜰")
    for (p in prefixes) if (n.startsWith(p)) {
        n = n.removePrefix(p).trim()
        break
    }
    n = n.removeSuffix("주유소").removeSuffix("충전소").trim()
    return n.ifEmpty { displayStationName(name) }
}

fun stationHeading(station: Station): String {
    val label = BRAND_LABEL[station.brand] ?: station.brand.name
    return "$label ${stationTradeName(station.brand, station.name)}".trim()
}

fun relativeTime(iso: String, now: Date = Date()): String {
    val t = parseIsoDate(iso) ?: return iso
    val diffMin = ((now.time - t.time) / 60_000).toInt()
    if (diffMin < 1) return "방금 신고"
    if (diffMin < 60) return "${diffMin}분 전 신고"
    val diffH = diffMin / 60
    if (diffH < 24) return "${diffH}시간 전 신고"
    return "${diffH / 24}일 전 신고"
}

fun seoulTime(date: Date = Date()): String {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"), Locale.KOREA)
    cal.time = date
    return String.format(
        Locale.KOREA, "%02d:%02d",
        cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)
    )
}
