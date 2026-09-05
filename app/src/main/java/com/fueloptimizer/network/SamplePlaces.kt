package com.fueloptimizer.network

import com.fueloptimizer.domain.NamedPlace

object SamplePlaces {
    val all: List<NamedPlace> = listOf(
        NamedPlace(37.4979, 127.0276, "강남역", "서울 강남구 강남대로"),
        NamedPlace(37.4846, 126.9876, "서울역", "서울 용산구 한강대로"),
        NamedPlace(37.4966, 126.8738, "홍대입구", "서울 마포구 양화로"),
        NamedPlace(37.5547, 126.9707, "숙대입구", "서울 용산구 한강대로"),
        NamedPlace(37.5660, 126.9952, "광화문", "서울 종로구 세종대로"),
        NamedPlace(37.5184, 127.0280, "삼성역", "서울 강남구 테헤란로"),
        NamedPlace(37.5033, 127.0448, "선릉역", "서울 강남구 테헤란로"),
        NamedPlace(37.4750, 127.0300, "자영알뜰주유소", "서울 강남구 역삼로"),
        NamedPlace(37.4956, 127.0669, "판교테크노밸리", "경기 성남시 분당구"),
        NamedPlace(35.1796, 129.0756, "부산 서면", "부산 부산진구 중앙대로"),
        NamedPlace(36.3504, 127.3845, "대전역", "대전 동구 중앙로"),
        NamedPlace(35.1596, 126.8526, "광주 송정역", "광주 광산구 송정로")
    )

    fun filter(query: String): List<NamedPlace> {
        if (query.isBlank()) return all
        return all.filter {
            it.name.contains(query, ignoreCase = true) ||
                (it.address ?: "").contains(query, ignoreCase = true)
        }
    }
}
