package com.fueloptimizer.data

import com.fueloptimizer.graph.FuelGraph
import com.fueloptimizer.graph.Station

data class StationData(
    val id: String,
    val name: String,
    val address: String,
    val lat: Double,
    val lon: Double,
    val priceWhi: Double,
    val priceDis: Double,
    val brand: String
) {
    fun toStation(fuelType: String = "whi") = Station(
        id = id,
        name = name,
        lat = lat,
        lon = lon,
        pricePerLiter = when (fuelType) {
            "dis" -> priceDis
            else -> priceWhi
        }
    )
}

object SampleStations {
    val all = listOf(
        StationData("S001", "Seoul Station", "Seoul Jung-gu", 37.5546, 126.9706, 1780.0, 1680.0, "SK"),
        StationData("S002", "Namdaemun Market", "Seoul Jung-gu", 37.5630, 126.9780, 1750.0, 1650.0, "GS"),
        StationData("S003", "Euljiro 1-ga", "Seoul Jung-gu", 37.5652, 126.9860, 1790.0, 1690.0, "HDO"),
        StationData("S004", "Jongno 3-ga", "Seoul Jongno-gu", 37.5707, 126.9913, 1760.0, 1660.0, "S-OIL"),
        StationData("S005", "Gangnam Station", "Seoul Gangnam-gu", 37.4980, 127.0276, 1850.0, 1750.0, "SK"),
        StationData("S006", "Yeoksam-dong", "Seoul Gangnam-gu", 37.5010, 127.0380, 1820.0, 1720.0, "GS"),
        StationData("S007", "Jamsil", "Seoul Songpa-gu", 37.5133, 127.1000, 1800.0, 1700.0, "HDO"),
        StationData("S008", "Pangyo", "Gyeonggi Seongnam", 37.3945, 127.1110, 1770.0, 1670.0, "S-OIL"),
        StationData("S009", "Suwon Station", "Gyeonggi Suwon", 37.2635, 127.0286, 1740.0, 1640.0, "SK"),
        StationData("S010", "Incheon Airport", "Incheon Jung-gu", 37.4530, 126.4410, 1880.0, 1780.0, "GS")
    )

    fun createSampleGraph(fuelType: String = "whi", maxEdgeDist: Double = 25.0): FuelGraph {
        val graph = FuelGraph()
        for (s in all) {
            graph.addStation(s.toStation(fuelType))
        }
        val ids = all.map { it.id }
        for (i in ids.indices) {
            for (j in i + 1 until ids.size) {
                val dist = graph.distance(ids[i], ids[j])
                if (dist <= maxEdgeDist) {
                    graph.addEdge(ids[i], ids[j], dist)
                }
            }
        }
        return graph
    }
}
