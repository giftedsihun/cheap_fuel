package com.fueloptimizer.network

import com.fueloptimizer.data.StationData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: Throwable) : Result<Nothing>()
    data object Loading : Result<Nothing>()
}

object OpinetRepository {
    private val api = NetworkModule.opinetApi

    private val brandMap = mapOf(
        "SK에너지" to "SK",
        "GS칼텍스" to "GS",
        "현대오일뱅크" to "HDO",
        "S-OIL" to "S-OIL",
        "현대icard" to "HDO",
        "SK-gas" to "SK",
        "GS-Gas" to "GS"
    )

    suspend fun getStationsByLocation(
        apiKey: String,
        lat: Double,
        lon: Double,
        radius: Int = 5000,
        fuelCode: String = "B027"
    ): Result<List<StationData>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getStationsByArea(
                apiKey = apiKey,
                x = lon,
                y = lat,
                radius = radius,
                fuelCode = fuelCode
            )
            val stations = response.result?.oilPriceList?.mapNotNull { price ->
                val latVal = price.lat?.toDoubleOrNull() ?: return@mapNotNull null
                val lonVal = price.lon?.toDoubleOrNull() ?: return@mapNotNull null
                if (latVal == 0.0 && lonVal == 0.0) return@mapNotNull null

                val r95Price = price.r95?.toDoubleOrNull()
                    ?: price.r93?.toDoubleOrNull()
                    ?: return@mapNotNull null

                val dieselPrice = price.dgas?.toDoubleOrNull() ?: 0.0

                val brand = brandMap[price.pollDivCo] ?: price.pollDivCo ?: "UNKNOWN"
                val name = price.osNm ?: "Unknown Station"
                val address = price.newAddr ?: price.oldAddr ?: ""

                StationData(
                    id = price.uniId ?: name.hashCode().toString(),
                    name = name,
                    address = address,
                    lat = latVal,
                    lon = lonVal,
                    priceWhi = r95Price,
                    priceDis = dieselPrice,
                    brand = brand
                )
            } ?: emptyList()
            Result.Success(stations)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun getLowestPriceStations(
        apiKey: String,
        area: String = "서울",
        fuelCode: String = "B027"
    ): Result<List<StationData>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getLowestPriceStations(
                apiKey = apiKey,
                area = area,
                fuelCode = fuelCode
            )
            val stations = response.result?.oilPriceList?.mapNotNull { price ->
                val latVal = price.lat?.toDoubleOrNull() ?: return@mapNotNull null
                val lonVal = price.lon?.toDoubleOrNull() ?: return@mapNotNull null
                if (latVal == 0.0 && lonVal == 0.0) return@mapNotNull null

                val r95Price = price.r95?.toDoubleOrNull()
                    ?: price.r93?.toDoubleOrNull()
                    ?: return@mapNotNull null

                val dieselPrice = price.dgas?.toDoubleOrNull() ?: 0.0
                val brand = brandMap[price.pollDivCo] ?: price.pollDivCo ?: "UNKNOWN"
                val name = price.osNm ?: "Unknown Station"
                val address = price.newAddr ?: price.oldAddr ?: ""

                StationData(
                    id = price.uniId ?: name.hashCode().toString(),
                    name = name,
                    address = address,
                    lat = latVal,
                    lon = lonVal,
                    priceWhi = r95Price,
                    priceDis = dieselPrice,
                    brand = brand
                )
            } ?: emptyList()
            Result.Success(stations)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun getSeoulAreaStations(
        apiKey: String,
        fuelCode: String = "B027"
    ): Result<List<StationData>> = withContext(Dispatchers.IO) {
        getStationsByLocation(apiKey, 37.5665, 126.9780, radius = 20000, fuelCode = fuelCode)
    }
}
