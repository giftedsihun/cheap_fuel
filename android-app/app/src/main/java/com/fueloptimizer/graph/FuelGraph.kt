package com.fueloptimizer.graph

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class Station(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val pricePerLiter: Double
)

data class Edge(
    val to: String,
    val distanceKm: Double
)

class FuelGraph {
    val stations: MutableMap<String, Station> = linkedMapOf()
    val adj: MutableMap<String, MutableList<Edge>> = linkedMapOf()

    fun addStation(station: Station) {
        stations[station.id] = station
        adj.getOrPut(station.id) { mutableListOf() }
    }

    fun addEdge(fromId: String, toId: String, distanceKm: Double, bidirectional: Boolean = true) {
        adj.getOrPut(fromId) { mutableListOf() }.add(Edge(toId, distanceKm))
        if (bidirectional) {
            adj.getOrPut(toId) { mutableListOf() }.add(Edge(fromId, distanceKm))
        }
    }

    fun distance(a: String, b: String): Double {
        val sa = stations[a] ?: return Double.MAX_VALUE
        val sb = stations[b] ?: return Double.MAX_VALUE
        val r = 6371.0
        val lat1 = Math.toRadians(sa.lat)
        val lon1 = Math.toRadians(sa.lon)
        val lat2 = Math.toRadians(sb.lat)
        val lon2 = Math.toRadians(sb.lon)
        val dlat = lat2 - lat1
        val dlon = lon2 - lon1
        val h = sin(dlat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dlon / 2).pow(2)
        return 2 * r * asin(sqrt(h))
    }
}
