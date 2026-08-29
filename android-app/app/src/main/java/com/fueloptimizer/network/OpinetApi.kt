package com.fueloptimizer.network

import retrofit2.http.GET
import retrofit2.http.Query

interface OpinetApi {
    @GET("api/aroundAll.do")
    suspend fun getStationsByArea(
        @Query("code") apiKey: String,
        @Query("x") x: Double,
        @Query("y") y: Double,
        @Query("radius") radius: Int = 3000,
        @Query("prodcd") fuelCode: String = "B027",
        @Query("sort") sort: String = "1",
        @Query("resultType") resultType: String = "json"
    ): OpinetResponse

    @GET("api/avgSidoPrice.do")
    suspend fun getAvgSidoPrice(
        @Query("code") apiKey: String,
        @Query("sido") sido: String? = null,
        @Query("prodcd") fuelCode: String = "B027",
        @Query("resultType") resultType: String = "json"
    ): OpinetResponse

    @GET("api/lowTop10.do")
    suspend fun getLowestPriceStations(
        @Query("code") apiKey: String,
        @Query("area") area: String? = null,
        @Query("prodcd") fuelCode: String = "B027",
        @Query("resultType") resultType: String = "json"
    ): OpinetResponse
}
