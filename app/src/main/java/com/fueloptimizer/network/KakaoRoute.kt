package com.fueloptimizer.network

import com.fueloptimizer.domain.Detour
import com.fueloptimizer.domain.DetourComputer
import com.fueloptimizer.domain.FuelKind
import com.fueloptimizer.domain.NamedPlace
import com.fueloptimizer.domain.PointLatLng
import com.fueloptimizer.domain.Route
import com.fueloptimizer.domain.projectOntoPolyline
import com.fueloptimizer.domain.viaRoutePolyline
import com.google.gson.annotations.SerializedName
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore as CoroutineSemaphore
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

data class KakaoFare(@SerializedName("toll") val toll: Int = 0)

data class KakaoSummary(
    @SerializedName("distance") val distance: Int = 0,
    @SerializedName("duration") val duration: Int = 0,
    @SerializedName("fare") val fare: KakaoFare? = null
)

data class KakaoRoad(@SerializedName("vertexes") val vertexes: List<Double>? = null)

data class KakaoSection(@SerializedName("roads") val roads: List<KakaoRoad>? = null)

data class KakaoRouteResult(
    @SerializedName("result_code") val resultCode: Int = -1,
    @SerializedName("result_msg") val resultMsg: String = "",
    @SerializedName("summary") val summary: KakaoSummary? = null,
    @SerializedName("sections") val sections: List<KakaoSection>? = null
)

data class KakaoDirectionsResponse(
    @SerializedName("routes") val routes: List<KakaoRouteResult>? = null
)

interface KakaoDirectionsApi {
    @GET("v1/directions")
    suspend fun directions(
        @Header("Authorization") auth: String,
        @Query("origin") origin: String,
        @Query("destination") destination: String,
        @Query("waypoints") waypoints: String? = null,
        @Query("priority") priority: String = "RECOMMEND",
        @Query("car_fuel") carFuel: String = "GASOLINE",
        @Query("car_hipass") carHipass: Boolean = true,
        @Query("alternatives") alternatives: Boolean = false,
        @Query("road_details") roadDetails: Boolean = true
    ): KakaoDirectionsResponse

    @GET("v1/future/directions")
    suspend fun futureDirections(
        @Header("Authorization") auth: String,
        @Query("origin") origin: String,
        @Query("destination") destination: String,
        @Query("departure_time") departureTime: String,
        @Query("priority") priority: String = "RECOMMEND",
        @Query("car_fuel") carFuel: String = "GASOLINE",
        @Query("car_hipass") carHipass: Boolean = true,
        @Query("alternatives") alternatives: Boolean = false,
        @Query("road_details") roadDetails: Boolean = true
    ): KakaoDirectionsResponse
}

fun kakaoCarFuel(fuelKind: FuelKind): String = when (fuelKind) {
    FuelKind.gasoline, FuelKind.premium -> "GASOLINE"
    FuelKind.diesel -> "DIESEL"
    FuelKind.lpg -> "LPG"
}

class KakaoRouteProvider(private val apiKey: String) : DetourComputer {
    private val api: KakaoDirectionsApi by lazy {
        val client = OkHttpClient.Builder()
            .callTimeout(15, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder()
            .baseUrl("https://apis-navi.kakaomobility.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(KakaoDirectionsApi::class.java)
    }

    private val routeCache = object : LinkedHashMap<String, Route>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Route>): Boolean = size > 40
    }
    private val detourCache = object : LinkedHashMap<String, Detour>(512, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Detour>): Boolean = size > 400
    }
    private val cacheAt = mutableMapOf<String, Long>()
    private var futureDisabled = false

    private fun fresh(key: String): Boolean {
        val at = synchronized(cacheAt) { cacheAt[key] } ?: return false
        return System.currentTimeMillis() - at < 10 * 60 * 1000
    }

    private fun remember(key: String) {
        synchronized(cacheAt) { cacheAt[key] = System.currentTimeMillis() }
    }

    private fun routeCacheKey(o: NamedPlace, d: NamedPlace, fuel: FuelKind, depart: String?): String {
        fun r(v: Double) = String.format(Locale.US, "%.5f", v)
        return "${r(o.lat)},${r(o.lng)}>${r(d.lat)},${r(d.lng)}|$fuel|$depart"
    }

    suspend fun findRoute(
        origin: NamedPlace,
        destination: NamedPlace,
        fuelKind: FuelKind,
        departAt: Date?
    ): Route? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext null
        val departStr = departAt?.let {
            val fmt = SimpleDateFormat("yyyyMMddHHmm", Locale.KOREA)
            fmt.timeZone = TimeZone.getTimeZone("Asia/Seoul")
            fmt.format(it)
        }
        val key = routeCacheKey(origin, destination, fuelKind, departStr)
        synchronized(routeCache) { if (fresh(key)) routeCache[key]?.let { return@withContext it } }

        val auth = "KakaoAK $apiKey"
        val o = "${origin.lng},${origin.lat}"
        val d = "${destination.lng},${destination.lat}"
        val fuel = kakaoCarFuel(fuelKind)

        // 출발 10분~48시간 후면 미래 경로 우선
        val nowMs = System.currentTimeMillis()
        val useFuture = departAt != null && !futureDisabled &&
            departAt.time - nowMs > 10 * 60 * 1000 && departAt.time - nowMs < 48 * 3600 * 1000
        if (useFuture && departStr != null) {
            val r = runCatching {
                api.futureDirections(auth, o, d, departStr, carFuel = fuel, roadDetails = true)
            }.getOrNull()
            val route = r?.routes?.firstOrNull()?.toRoute(origin, destination, departAt)
            if (route != null) {
                synchronized(routeCache) { routeCache[key] = route }
                remember(key)
                return@withContext route
            }
            futureDisabled = true
        }

        val resp = runCatching {
            api.directions(auth, o, d, carFuel = fuel, roadDetails = true)
        }.getOrElse {
            return@withContext runCatching {
                api.directions(auth, o, d, carFuel = fuel, roadDetails = false)
            }.getOrNull()?.routes?.firstOrNull()?.toRoute(origin, destination, departAt)
        }
        val route = resp?.routes?.firstOrNull()?.toRoute(origin, destination, departAt)
        if (route != null) {
            synchronized(routeCache) { routeCache[key] = route }
            remember(key)
        }
        route
    }

    private fun KakaoRouteResult.toRoute(origin: NamedPlace, destination: NamedPlace, departAt: Date?): Route? {
        if (resultCode != 0) return null
        val summary = summary ?: return null
        val polyline = mutableListOf<NamedPlace>()
        sections.orEmpty().forEach { section ->
            section.roads.orEmpty().forEach { road ->
                val v = road.vertexes.orEmpty()
                var idx = 0
                while (idx + 1 < v.size) {
                    polyline.add(NamedPlace(lat = v[idx + 1], lng = v[idx], name = ""))
                    idx += 2
                }
            }
        }
        val line = if (polyline.size >= 2) polyline else listOf(origin, destination)
        return Route(
            id = "kakao-${origin.lat},${origin.lng}-${destination.lat},${destination.lng}",
            origin = origin,
            destination = destination,
            polyline = line,
            distanceM = summary.distance.toDouble(),
            durationS = summary.duration.toDouble(),
            tollKrw = (summary.fare?.toll ?: 0).toDouble(),
            durationIncludesTraffic = departAt != null
        )
    }

    override suspend fun computeDetours(route: Route, stations: List<com.fueloptimizer.domain.Station>): Map<String, Detour> =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) return@withContext emptyMap()
            val semaphore = CoroutineSemaphore(12)
            val baseO = "${route.origin.lng},${route.origin.lat}"
            val baseD = "${route.destination.lng},${route.destination.lat}"
            stations.map { station ->
                async {
                    semaphore.acquire()
                    try {
                        detourViaWaypoint(route, station, baseO, baseD)
                    } finally {
                        semaphore.release()
                    }
                }
            }.awaitAll().filterNotNull().associateBy { it.first }.mapValues { it.value.second }
        }

    private suspend fun detourViaWaypoint(
        route: Route,
        station: com.fueloptimizer.domain.Station,
        baseO: String,
        baseD: String
    ): Pair<String, Detour>? {
        val proj = projectOntoPolyline(PointLatLng(station.lat, station.lng), route.polyline)
        val key = "${route.id}|${station.id}"
        synchronized(detourCache) { if (fresh(key)) detourCache[key]?.let { return key to it } }
        val auth = "KakaoAK $apiKey"
        val resp = runCatching {
            api.directions(
                auth, baseO, baseD,
                waypoints = "${station.lng},${station.lat}",
                carFuel = kakaoCarFuel(FuelKind.gasoline)
            )
        }.getOrNull() ?: return null
        val via = resp.routes?.firstOrNull() ?: return null
        if (via.resultCode != 0) return null
        val s = via.summary ?: return null
        val detour = Detour(
            extraDistanceM = maxOf(0.0, s.distance - route.distanceM),
            extraDurationS = maxOf(0.0, s.duration - route.durationS),
            extraTollKrw = maxOf(0.0, (s.fare?.toll ?: 0) - route.tollKrw),
            alongRouteM = proj.alongM,
            offRouteM = proj.offsetM,
            joinPoint = proj.point,
            source = "routing-api",
            viaPolyline = viaRoutePolyline(route.polyline, PointLatLng(station.lat, station.lng), proj.point, proj.alongM)
        )
        synchronized(detourCache) { detourCache[key] = detour }
        remember(key)
        return key to detour
    }

}

/** 기하학적 폴백 우회 계산 (원본 mock/route-provider.ts 포트). */
class MockRouteProvider : DetourComputer {
    override suspend fun computeDetours(
        route: Route,
        stations: List<com.fueloptimizer.domain.Station>
    ): Map<String, Detour> {
        return stations.associate { station ->
            val hint = station.accessHint
        val proj = projectOntoPolyline(PointLatLng(station.lat, station.lng), route.polyline)
            val offset = proj.offsetM
            val (extra, speedKmh, delayS) = when {
                hint?.onHighway == true -> Triple(260 + offset * 0.6, 52.0, 0.0)
                hint?.requiresHighwayExit == true -> Triple(offset * 2 * 1.35 + 2400, 52.0, 260.0)
                hint?.oppositeSide == true -> Triple(offset * 2 * 1.55 + 700, 30.0, 130.0)
                else -> Triple(offset * 2 * 1.25, 30.0, 45.0)
            }
            val jitter = 0.88 + (fnv1a(station.id) % 1000) / 1000.0 * 0.24
            val extraM = extra * jitter
            station.id to Detour(
                extraDistanceM = extraM,
                extraDurationS = extraM / 1000 / speedKmh * 3600 + delayS,
                extraTollKrw = if (hint?.requiresHighwayExit == true || hint?.onHighway == true) 900.0 else 0.0,
                alongRouteM = proj.alongM,
                offRouteM = offset,
                joinPoint = proj.point,
                source = "geometric-estimate"
            )
        }
    }

    private fun fnv1a(s: String): Long {
        var h = -3750763034362895579L
        for (c in s) {
            h = h xor c.code.toLong()
            h *= 1099511628211L
        }
        return if (h < 0) -h else h
    }
}
