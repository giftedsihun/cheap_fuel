package com.fueloptimizer.network

import com.google.gson.annotations.SerializedName

data class OpinetResponse(
    @SerializedName("RESULT")
    val result: OpinetResult?
)

data class OpinetResult(
    @SerializedName("oilPriceList")
    val oilPriceList: List<OpinetOilPrice>?,
    @SerializedName("avgPriceList")
    val avgPriceList: List<OpinetAvgPrice>?
)

data class OpinetOilPrice(
    @SerializedName("UNI_ID")
    val uniId: String?,
    @SerializedName("OS_NM")
    val osNm: String?,
    @SerializedName("SIGUNCD")
    val sigunCd: String?,
    @SerializedName("SIGUN_NM")
    val sigunNm: String?,
    @SerializedName("POLL_DIV_CO")
    val pollDivCo: String?,
    @SerializedName("LPG_YN")
    val lpgYn: String?,
    @SerializedName("MAINT_YN")
    val maintYn: String?,
    @SerializedName("CAR_WASH_YN")
    val carWashYn: String?,
    @SerializedName("CVS_YN")
    val cvsYn: String?,
    @SerializedName("SELF_DIV_CD")
    val selfDivCd: String?,
    @SerializedName("SELF_NM")
    val selfNm: String?,
    @SerializedName("R93")
    val r93: String?,
    @SerializedName("R95")
    val r95: String?,
    @SerializedName("R97")
    val r97: String?,
    @SerializedName("PGAS")
    val pgas: String?,
    @SerializedName("Dgas")
    val dgas: String?,
    @SerializedName("LPG")
    val lpg: String?,
    @SerializedName("LAT")
    val lat: String?,
    @SerializedName("LON")
    val lon: String?,
    @SerializedName("NEW_ADDR")
    val newAddr: String?,
    @SerializedName("OLD_ADDR")
    val oldAddr: String?,
    @SerializedName("TEL")
    val tel: String?,
    @SerializedName("KATIS_MG2_YN")
    val katisMg2Yn: String?,
    @SerializedName("KATIS_ZY_YN")
    val katisZyYn: String?,
    @SerializedName("KATIS_US_YN")
    val katisUsYn: String?,
    @SerializedName("KATIS_VT_YN")
    val katisVtYn: String?,
    @SerializedName("KATIS_EF_YN")
    val katisEfYn: String?,
    @SerializedName("KATIS_MG_YN")
    val katisMgYn: String?,
    @SerializedName("KATIS_LI_YN")
    val katisLiYn: String?,
    @SerializedName("KATIS_SH_YN")
    val katisShYn: String?,
    @SerializedName("KATIS_CP_YN")
    val katisCpYn: String?,
    @SerializedName("KATIS_BD_YN")
    val katisBdYn: String?,
    @SerializedName("UPD_DT")
    val updDt: String?
)

data class OpinetAvgPrice(
    @SerializedName("SIDO_NM")
    val sidoNm: String?,
    @SerializedName("PRICE")
    val price: String?,
    @SerializedName("DIFF")
    val diff: String?,
    @SerializedName("GASOLINE")
    val gasoline: String?,
    @SerializedName("PREMIUM_GASOLINE")
    val premiumGasoline: String?,
    @SerializedName("KEROSENE")
    val kerosene: String?,
    @SerializedName("DIESEL")
    val diesel: String?,
    @SerializedName("LPG")
    val lpg: String?
)
