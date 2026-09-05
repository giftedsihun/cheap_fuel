package com.fueloptimizer.network

import com.fueloptimizer.domain.Brand
import com.fueloptimizer.domain.FuelKind
import com.fueloptimizer.domain.OpeningHours
import com.fueloptimizer.domain.AccessHint
import com.fueloptimizer.domain.Station

interface OpinetApi {
    suspend fun getFuelPrices(): FuelPriceResponse
}

data class FuelPriceResponse(
    val body: FuelPriceBody?
)

data class FuelPriceBody(
    val items: List<FuelPriceItem>?
)

data class FuelPriceItem(
    val prodCd: String?,
    val companyCd: String?,
    val ioCd: String?,
    val addr: String?,
    val xcoor: String?,
    val ycoor: String?,
    val price: Double?,
    val regDt: String?
)

object FuelKindMapper {
    private val map = mapOf(
        "B027" to FuelKind.gasoline,
        "B034" to FuelKind.premium,
        "D047" to FuelKind.diesel,
        "K015" to FuelKind.lpg,
    )

    fun fromOpinetCode(code: String?): FuelKind? {
        return map[code]
    }
}

object BrandMapper {
    private val map = mapOf(
        "SKE" to Brand.SKE,
        "GSC" to Brand.GSC,
        "HDO" to Brand.HDO,
        "SOL" to Brand.SOL,
        "RTE" to Brand.RTE,
        "RTX" to Brand.RTX,
        "NHO" to Brand.NHO,
    )

    fun fromCompanyCode(code: String?): Brand {
        return map[code] ?: Brand.ETC
    }
}

object MockStations {
    val sampleStations = listOf(
        Station(
            id = "station_1",
            name = "SK에너지 강남역주유소",
            brand = Brand.SKE,
            isSelfService = false,
            lat = 37.4979,
            lng = 127.0276,
            prices = mapOf(
                FuelKind.gasoline to 1850.0,
                FuelKind.premium to 1950.0,
                FuelKind.diesel to 1800.0,
            ),
            priceUpdatedAt = "2024-01-01T00:00:00",
            openingHours = OpeningHours(allDay = true),
            accessHint = AccessHint(oppositeSide = false, requiresHighwayExit = false, onHighway = false)
        ),
        Station(
            id = "station_2",
            name = "GS칼텍스 삼성점",
            brand = Brand.GSC,
            isSelfService = false,
            lat = 37.4824,
            lng = 127.0378,
            prices = mapOf(
                FuelKind.gasoline to 1830.0,
                FuelKind.premium to 1930.0,
                FuelKind.diesel to 1780.0,
            ),
            priceUpdatedAt = "2024-01-01T00:00:00",
            openingHours = OpeningHours(allDay = true),
            accessHint = AccessHint(oppositeSide = false, requiresHighwayExit = false, onHighway = false)
        ),
        Station(
            id = "station_3",
            name = "현대오일뱅크 역삼점",
            brand = Brand.HDO,
            isSelfService = false,
            lat = 37.4643,
            lng = 127.0198,
            prices = mapOf(
                FuelKind.gasoline to 1870.0,
                FuelKind.premium to 1970.0,
                FuelKind.diesel to 1820.0,
            ),
            priceUpdatedAt = "2024-01-01T00:00:00",
            openingHours = OpeningHours(allDay = true),
            accessHint = AccessHint(oppositeSide = false, requiresHighwayExit = false, onHighway = false)
        ),
        Station(
            id = "station_4",
            name = "S-OIL 서초점",
            brand = Brand.SOL,
            isSelfService = true,
            lat = 37.4850,
            lng = 127.0150,
            prices = mapOf(
                FuelKind.gasoline to 1820.0,
                FuelKind.premium to 1920.0,
                FuelKind.diesel to 1770.0,
            ),
            priceUpdatedAt = "2024-01-01T00:00:00",
            openingHours = OpeningHours(allDay = true),
            accessHint = AccessHint(oppositeSide = false, requiresHighwayExit = false, onHighway = false)
        ),
        Station(
            id = "station_5",
            name = "자영알뜰 주유소",
            brand = Brand.RTE,
            isSelfService = true,
            lat = 37.4750,
            lng = 127.0300,
            prices = mapOf(
                FuelKind.gasoline to 1790.0,
                FuelKind.diesel to 1740.0,
            ),
            priceUpdatedAt = "2024-01-01T00:00:00",
            openingHours = OpeningHours(allDay = false, open = "07:00", close = "22:00"),
            accessHint = AccessHint(oppositeSide = false, requiresHighwayExit = false, onHighway = false)
        )
    )
}