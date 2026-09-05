package com.fueloptimizer.network

import com.fueloptimizer.domain.CorridorSample
import com.fueloptimizer.domain.FuelKind
import com.fueloptimizer.domain.Route
import com.fueloptimizer.domain.Station
import com.fueloptimizer.domain.sampleCorridorCenters
import com.google.gson.annotations.SerializedName
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 오피넷 실가격 연동 (원본 providers/opinet 포트).
 * KATEC 좌표계는 proj4 없이 수동 변환 (Bessel 타원체 + 7파라미터 측지계 변환).
 */

data class OpinetAroundRow(
    @SerializedName("UNI_ID") val uniId: String = "",
    @SerializedName("OS_NM") val osNm: String = "",
    @SerializedName("PRICE") val price: String = "",
    @SerializedName("GIS_X_COOR") val x: Double = 0.0,
    @SerializedName("GIS_Y_COOR") val y: Double = 0.0,
    @SerializedName("POLL_DIV_CO") val brand: String = ""
)

data class OpinetAroundResponse(
    @SerializedName("RESULT") val result: OpinetCodeResult? = null
)

data class OpinetCodeResult(
    @SerializedName("OIL") val oil: List<OpinetAroundRow>? = null,
    @SerializedName("OIL_CNT") val count: Int = 0
)

data class OpinetOilPrice(
    @SerializedName("PRODCD") val prodCd: String = "",
    @SerializedName("PRICE") val price: Double = 0.0
)

data class OpinetDetailRow(
    @SerializedName("UNI_ID") val uniId: String = "",
    @SerializedName("SELF_YN") val selfYn: String = "",
    @SerializedName("GPOLL_DIV_CO") val brand: String = "",
    @SerializedName("VAN_ADR") val vanAdr: String = "",
    @SerializedName("NEW_ADR") val newAdr: String = "",
    @SerializedName("CAR_WASH_YN") val carWashYn: String = "",
    @SerializedName("OIL_PRICE") val oilPrice: List<OpinetOilPrice>? = null
)

data class OpinetDetailBody(@SerializedName("OIL") val oil: List<OpinetDetailRow>? = null)

data class OpinetDetailResponse(@SerializedName("RESULT") val result: OpinetDetailBody? = null)

interface OpinetApiService {
    @GET("api/aroundAll.do")
    suspend fun aroundAll(
        @Query("out") out: String = "json",
        @Query("certkey") certKey: String,
        @Query("x") x: Double,
        @Query("y") y: Double,
        @Query("radius") radius: Int,
        @Query("sort") sort: Int = 1,
        @Query("prodcd") prodCd: String
    ): OpinetAroundResponse

    @GET("api/detailById.do")
    suspend fun detailById(
        @Query("out") out: String = "json",
        @Query("certkey") certKey: String,
        @Query("id") id: String
    ): OpinetDetailResponse
}

fun opinetProdCode(fuelKind: FuelKind): String = when (fuelKind) {
    FuelKind.gasoline -> "B027"
    FuelKind.premium -> "B034"
    FuelKind.diesel -> "D047"
    FuelKind.lpg -> "K015"
}

/** KATEC 투영 + Bessel 타원체 + WGS84 7파라미터 변환. */
object Katec {
    private const val LAT0 = 38.0
    private const val LON0 = 128.0
    private const val K0 = 0.9999
    private const val FE = 400000.0
    private const val FN = 600000.0
    // Bessel 1841
    private const val A_BESSEL = 6377397.155
    private const val F_BESSEL = 1 / 299.1528128
    // WGS84
    private const val A_WGS = 6378137.0
    private const val F_WGS = 1 / 298.257223563
    // towgs84 (위치벡터 규약, 초→라디안, ppm→비율)
    private const val DX = -115.80
    private const val DY = 474.99
    private const val DZ = 674.11
    private val RX = 1.16 / 3600 * PI / 180
    private val RY = -2.31 / 3600 * PI / 180
    private val RZ = -1.63 / 3600 * PI / 180
    private const val DS = 6.43e-6

    private fun e2(a: Double, f: Double): Double = 2 * f - f * f

    private data class Ecef(val x: Double, val y: Double, val z: Double)

    private fun geodeticToEcef(latDeg: Double, lngDeg: Double, a: Double, f: Double): Ecef {
        val e2v = e2(a, f)
        val lat = latDeg * PI / 180
        val lng = lngDeg * PI / 180
        val n = a / sqrt(1 - e2v * sin(lat).pow(2))
        return Ecef(
            n * cos(lat) * cos(lng),
            n * cos(lat) * sin(lng),
            n * (1 - e2v) * sin(lat)
        )
    }

    private fun ecefToGeodetic(e: Ecef, a: Double, f: Double): Pair<Double, Double> {
        val e2v = e2(a, f)
        val lng = atan(e.y / e.x)
        val p = sqrt(e.x * e.x + e.y * e.y)
        var lat = atan(e.z / (p * (1 - e2v)))
        repeat(5) {
            val n = a / sqrt(1 - e2v * sin(lat).pow(2))
            lat = atan((e.z + e2v * n * sin(lat)) / p)
        }
        return lat * 180 / PI to lng * 180 / PI
    }

    private fun shiftToBessel(e: Ecef): Ecef {
        // WGS84 → Bessel: 역변환 (부호 반전)
        val s = 1 - DS
        return Ecef(
            -DX + s * (e.x - RZ * e.y + RY * e.z),
            -DY + s * (RZ * e.x + e.y - RX * e.z),
            -DZ + s * (-RY * e.x + RX * e.y + e.z)
        )
    }

    private fun shiftToWgs(e: Ecef): Ecef {
        val s = 1 + DS
        return Ecef(
            DX + s * (e.x + RZ * e.y - RY * e.z),
            DY + s * (-RZ * e.x + e.y + RX * e.z),
            DZ + s * (RY * e.x - RX * e.y + e.z)
        )
    }

    private fun tmForward(latDeg: Double, lngDeg: Double): Pair<Double, Double> {
        val e2v = e2(A_BESSEL, F_BESSEL)
        val ep2 = e2v / (1 - e2v)
        val lat = latDeg * PI / 180
        val lng = lngDeg * PI / 180
        val lat0 = LAT0 * PI / 180
        val lon0 = LON0 * PI / 180
        val n = A_BESSEL / sqrt(1 - e2v * sin(lat).pow(2))
        val t = tan(lat).pow(2)
        val c = ep2 * cos(lat).pow(2)
        val a = (lng - lon0) * cos(lat)
        fun m(phi: Double): Double {
            return A_BESSEL * (
                (1 - e2v / 4 - 3 * e2v.pow(2) / 64 - 5 * e2v.pow(3) / 256) * phi -
                    (3 * e2v / 8 + 3 * e2v.pow(2) / 32 + 45 * e2v.pow(3) / 1024) * sin(2 * phi) +
                    (15 * e2v.pow(2) / 256 + 45 * e2v.pow(3) / 1024) * sin(4 * phi) -
                    (35 * e2v.pow(3) / 3072) * sin(6 * phi)
                )
        }
        val x = FE + K0 * n * (
            a + (1 - t + c) * a.pow(3) / 6 +
                (5 - 18 * t + t * t + 72 * c - 58 * ep2) * a.pow(5) / 120
            )
        val y = FN + K0 * (
            m(lat) - m(lat0) + n * tan(lat) * (
                a * a / 2 + (5 - t + 9 * c + 4 * c * c) * a.pow(4) / 24
                )
        )
        return x to y
    }

    private fun tmInverse(x: Double, y: Double): Pair<Double, Double> {
        val e2v = e2(A_BESSEL, F_BESSEL)
        val ep2 = e2v / (1 - e2v)
        val m0 = run {
            val lat0 = LAT0 * PI / 180
            A_BESSEL * (
                (1 - e2v / 4 - 3 * e2v.pow(2) / 64 - 5 * e2v.pow(3) / 256) * lat0 -
                    (3 * e2v / 8 + 3 * e2v.pow(2) / 32 + 45 * e2v.pow(3) / 1024) * sin(2 * lat0) +
                    (15 * e2v.pow(2) / 256 + 45 * e2v.pow(3) / 1024) * sin(4 * lat0) -
                    (35 * e2v.pow(3) / 3072) * sin(6 * lat0)
                )
        }
        val m = m0 + (y - FN) / K0
        val mu = m / (A_BESSEL * (1 - e2v / 4 - 3 * e2v.pow(2) / 64 - 5 * e2v.pow(3) / 256))
        val e1 = (1 - sqrt(1 - e2v)) / (1 + sqrt(1 - e2v))
        val phi1 = mu +
            (3 * e1 / 2 - 27 * e1.pow(3) / 32) * sin(2 * mu) +
            (21 * e1.pow(2) / 16 - 55 * e1.pow(4) / 32) * sin(4 * mu) +
            (151 * e1.pow(3) / 96) * sin(6 * mu)
        val n1 = A_BESSEL / sqrt(1 - e2v * sin(phi1).pow(2))
        val t1 = tan(phi1).pow(2)
        val c1 = ep2 * cos(phi1).pow(2)
        val r1 = A_BESSEL * (1 - e2v) / (1 - e2v * sin(phi1).pow(2)).pow(1.5)
        val d = (x - FE) / (n1 * K0)
        val lat = phi1 - (n1 * tan(phi1) / r1) * (
            d * d / 2 - (5 + 3 * t1 + 10 * c1 - 4 * c1 * c1) * d.pow(4) / 24
            )
        val lng = LON0 * PI / 180 + (
            d - (1 + 2 * t1 + c1) * d.pow(3) / 6 +
                (5 - 2 * c1 + 28 * t1 - 3 * c1 * c1) * d.pow(5) / 120
            ) / cos(phi1)
        return lat * 180 / PI to lng * 180 / PI
    }

    /** WGS84 위경도 → KATEC (m). */
    fun toKatec(lat: Double, lng: Double): Pair<Double, Double> {
        val ecef = geodeticToEcef(lat, lng, A_WGS, F_WGS)
        val bessel = shiftToBessel(ecef)
        val (bLat, bLng) = ecefToGeodetic(bessel, A_BESSEL, F_BESSEL)
        return tmForward(bLat, bLng)
    }

    /** KATEC (m) → WGS84 위경도. */
    fun fromKatec(x: Double, y: Double): Pair<Double, Double> {
        val (bLat, bLng) = tmInverse(x, y)
        val ecef = geodeticToEcef(bLat, bLng, A_BESSEL, F_BESSEL)
        val wgs = shiftToWgs(ecef)
        return ecefToGeodetic(wgs, A_WGS, F_WGS)
    }
}

class OpinetStationProvider(private val certKey: String) {
    private val api: OpinetApiService by lazy {
        val client = OkHttpClient.Builder()
            .callTimeout(20, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder()
            .baseUrl("https://www.opinet.co.kr/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpinetApiService::class.java)
    }

    suspend fun findAlongRoute(
        route: Route,
        fuelKind: FuelKind,
        corridorHalfWidthM: Double
    ): List<Station> = withContext(Dispatchers.IO) {
        if (certKey.isBlank()) return@withContext emptyList()
        val radius = corridorHalfWidthM.coerceIn(1000.0, 5000.0).toInt()
        val samples: List<CorridorSample> =
            sampleCorridorCenters(route.polyline, corridorHalfWidthM, radius.toDouble())
        val semaphore = Semaphore(12)
        val prodCd = opinetProdCode(fuelKind)
        val rows = samples.map { sample ->
            async {
                semaphore.acquire()
                try {
                    val (kx, ky) = Katec.toKatec(sample.point.lat, sample.point.lng)
                    runCatching {
                        api.aroundAll(
                            certKey = certKey,
                            x = String.format("%.1f", kx).toDouble(),
                            y = String.format("%.1f", ky).toDouble(),
                            radius = radius,
                            prodCd = prodCd
                        ).result?.oil.orEmpty()
                    }.getOrElse { emptyList() }
                } finally {
                    semaphore.release()
                }
            }
        }.awaitAll().flatten().distinctBy { it.uniId }

        // 상세 보강 (유종별 가격 + 셀프/세차)
        val detailSemaphore = Semaphore(12)
        rows.map { row ->
            async {
                detailSemaphore.acquire()
                try {
                    hydrate(row, fuelKind)
                } finally {
                    detailSemaphore.release()
                }
            }
        }.awaitAll().filterNotNull()
    }

    private suspend fun hydrate(row: OpinetAroundRow, fuelKind: FuelKind): Station? {
        val detail = runCatching {
            api.detailById(certKey = certKey, id = row.uniId).result?.oil?.firstOrNull()
        }.getOrNull()
        val prices = mutableMapOf<FuelKind, Double>()
        detail?.oilPrice?.forEach { op ->
            FuelKindMapper.fromOpinetCode(op.prodCd)?.let { prices[it] = op.price }
        }
        if (prices[fuelKind] == null) {
            row.price.toDoubleOrNull()?.let { prices[fuelKind] = it } ?: return null
        }
        val (lat, lng) = Katec.fromKatec(row.x, row.y)
        if (abs(lat) < 1 || abs(lng) < 1) return null
        val brandCode = detail?.brand?.ifBlank { row.brand } ?: row.brand
        return Station(
            id = "opinet-${row.uniId}",
            name = row.osNm,
            brand = BrandMapper.fromCompanyCode(brandCode),
            isSelfService = detail?.selfYn == "Y",
            lat = lat,
            lng = lng,
            prices = prices,
            priceUpdatedAt = nowIso(),
            address = detail?.newAdr?.ifBlank { detail?.vanAdr }?.ifBlank { null },
            hasCarWash = detail?.carWashYn == "Y"
        )
    }

    fun nowIso(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("Asia/Seoul")
        return sdf.format(java.util.Date())
    }
}

/** 오프라인 목 주유소 제공자 (키 없을 때). */
class MockStationProvider {
    fun findAlongRoute(): List<Station> = MockStations.sampleStations
}
