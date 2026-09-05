package com.fueloptimizer.network

import com.fueloptimizer.domain.NamedPlace
import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

data class KakaoKeywordResponse(
    val meta: KakaoMeta?,
    val documents: List<KakaoPlaceDocument>?
)

data class KakaoMeta(
    @SerializedName("total_count") val totalCount: Int = 0,
    @SerializedName("is_end") val isEnd: Boolean = true
)

data class KakaoPlaceDocument(
    @SerializedName("place_name") val placeName: String = "",
    @SerializedName("address_name") val addressName: String = "",
    @SerializedName("road_address_name") val roadAddressName: String = "",
    val x: String = "",
    val y: String = ""
) {
    fun toNamedPlace(): NamedPlace? {
        val lat = y.toDoubleOrNull() ?: return null
        val lng = x.toDoubleOrNull() ?: return null
        if (placeName.isBlank()) return null
        val address = roadAddressName.ifBlank { addressName }.ifBlank { null }
        return NamedPlace(lat = lat, lng = lng, name = placeName, address = address)
    }
}

interface KakaoLocalApi {
    @GET("v2/local/search/keyword.json")
    suspend fun searchKeyword(
        @Header("Authorization") auth: String,
        @Query("query") query: String,
        @Query("size") size: Int = 10
    ): KakaoKeywordResponse
}

object KakaoClient {
    private const val BASE_URL = "https://dapi.kakao.com/"

    private val api: KakaoLocalApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(KakaoLocalApi::class.java)
    }

    suspend fun searchPlaces(apiKey: String, query: String): List<NamedPlace> {
        if (apiKey.isBlank() || query.isBlank()) return emptyList()
        return runCatching {
            api.searchKeyword("KakaoAK $apiKey", query)
                .documents.orEmpty()
                .mapNotNull { it.toNamedPlace() }
        }.getOrElse { emptyList() }
    }
}
