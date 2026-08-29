package com.fueloptimizer.graph

import java.util.PriorityQueue

data class RefuelStop(
    val stationId: String,
    val stationName: String,
    val liters: Double,
    val pricePerLiter: Double
) {
    val cost: Double get() = liters * pricePerLiter
}

data class FuelPlan(
    val totalCost: Double,
    val totalDistance: Double,
    val path: List<String>,
    val refuels: List<RefuelStop>
)

private data class State(
    val cost: Double,
    val counter: Int,
    val node: String,
    val fuel: Double,
    val path: List<String>,
    val refuels: List<RefuelStop>
) : Comparable<State> {
    override fun compareTo(other: State): Int = cost.compareTo(other.cost)
}

object FuelRouteFinder {
    fun findMinFuelRoute(
        graph: FuelGraph,
        startId: String,
        goalId: String,
        fuelEfficiencyKmPerL: Double,
        tankCapacityL: Double,
        initialFuelL: Double = 0.0
    ): FuelPlan? {
        if (graph.stations[startId] == null || graph.stations[goalId] == null) return null

        val kmPerL = fuelEfficiencyKmPerL
        fun fuelNeededFor(distanceKm: Double): Double = distanceKm / kmPerL

        var counter = 0
        val pq = PriorityQueue<State>()
        val start = graph.stations[startId]!!
        pq.add(State(0.0, counter++, startId, initialFuelL, listOf(startId), emptyList()))

        val best = mutableMapOf<Pair<String, Double>, Double>()
        best[startId to roundFuel(initialFuelL)] = 0.0

        while (pq.isNotEmpty()) {
            val cur = pq.poll()
            val key = cur.node to roundFuel(cur.fuel)
            val recorded = best[key]
            if (recorded != null && cur.cost > recorded + 1e-9) continue

            if (cur.node == goalId) {
                val totalDist = pathDistance(graph, cur.path)
                return FuelPlan(cur.cost, totalDist, cur.path, cur.refuels)
            }

            for (edge in graph.adj[cur.node].orEmpty()) {
                val need = fuelNeededFor(edge.distanceKm)
                if (need > tankCapacityL + 1e-9) continue

                if (cur.fuel + 1e-9 >= need) {
                    val nextCost = cur.cost
                    val nextFuel = cur.fuel - need
                    val nextKey = edge.to to roundFuel(nextFuel)
                    if (nextCost < (best[nextKey] ?: Double.MAX_VALUE) - 1e-9) {
                        best[nextKey] = nextCost
                        pq.add(State(nextCost, counter++, edge.to, nextFuel, cur.path + edge.to, cur.refuels))
                    }
                } else {
                    val missing = need - cur.fuel
                    val station = graph.stations[cur.node] ?: continue
                    val addCost = missing * station.pricePerLiter
                    val nextCost = cur.cost + addCost
                    val nextFuel = cur.fuel + (tankCapacityL - cur.fuel)
                    val newRefuel = RefuelStop(
                        stationId = cur.node,
                        stationName = station.name,
                        liters = missing,
                        pricePerLiter = station.pricePerLiter
                    )
                    val nextKey = edge.to to roundFuel(nextFuel)
                    if (nextCost < (best[nextKey] ?: Double.MAX_VALUE) - 1e-9) {
                        best[nextKey] = nextCost
                        pq.add(State(
                            nextCost, counter++, edge.to, nextFuel,
                            cur.path + edge.to,
                            cur.refuels + newRefuel
                        ))
                    }
                }
            }
        }
        return null
    }

    private fun roundFuel(f: Double): Double = (f * 10000.0).toLong() / 10000.0

    private fun pathDistance(graph: FuelGraph, path: List<String>): Double {
        if (path.size < 2) return 0.0
        var total = 0.0
        for (i in 0 until path.size - 1) {
            for (e in graph.adj[path[i]].orEmpty()) {
                if (e.to == path[i + 1]) {
                    total += e.distanceKm
                    break
                }
            }
        }
        return total
    }
}
