"""
graph.py
그래프 정의 + Dijkstra + DP 기반 최소 주유 비용 경로 탐색

문제 정의:
- 노드: 주유소 또는 목적지
- 엣지: 두 노드 사이의 도로 구간 (거리 km)
- 상태: (현재 노드, 남은 연료량) -> 최소 누적 비용
- 전이: 현재 주유소에서 0 ~ tank_capacity 까지 자유롭게 주유 가능
        (탱크 한도 + 다음 노드까지 거리 이내)
"""
from __future__ import annotations
from dataclasses import dataclass, field
from typing import Dict, List, Tuple, Optional
import heapq
import math


@dataclass
class Station:
    """주유소 노드"""
    id: str
    name: str
    lat: float
    lon: float
    price_per_liter: float

    def __hash__(self):
        return hash(self.id)


@dataclass
class Edge:
    """노드 간 도로 엣지"""
    to: str
    distance_km: float


@dataclass
class FuelGraph:
    """주유소 그래프"""
    stations: Dict[str, Station] = field(default_factory=dict)
    adj: Dict[str, List[Edge]] = field(default_factory=dict)

    def add_station(self, station: Station) -> None:
        self.stations[station.id] = station
        self.adj.setdefault(station.id, [])

    def add_edge(self, from_id: str, to_id: str, distance_km: float, bidirectional: bool = True) -> None:
        self.adj.setdefault(from_id, []).append(Edge(to=to_id, distance_km=distance_km))
        if bidirectional:
            self.adj.setdefault(to_id, []).append(Edge(to=from_id, distance_km=distance_km))

    def distance(self, a: str, b: str) -> float:
        """두 좌표 사이 직선거리 (km) - 임시 엣지 생성용"""
        sa = self.stations[a]
        sb = self.stations[b]
        R = 6371.0
        lat1, lon1 = math.radians(sa.lat), math.radians(sa.lon)
        lat2, lon2 = math.radians(sb.lat), math.radians(sb.lon)
        dlat, dlon = lat2 - lat1, lon2 - lon1
        h = math.sin(dlat / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(dlon / 2) ** 2
        return 2 * R * math.asin(math.sqrt(h))


@dataclass
class FuelPlan:
    """최적 주유 계획 결과"""
    total_cost: float
    total_distance: float
    path: List[str]
    refuels: List[Tuple[str, float, float]]
    """(station_id, liters_added, price_per_liter)"""


def find_min_fuel_route(
    graph: FuelGraph,
    start_id: str,
    goal_id: str,
    fuel_efficiency_km_per_l: float,
    tank_capacity_l: float,
    initial_fuel_l: float = 0.0,
) -> Optional[FuelPlan]:
    """
    Dijkstra + DP: 시작 -> 목적지까지 최소 비용 경로.
    상태: (cost, node, fuel_left, path, refuels)
    - node 에서 다음 노드까지 갈 만큼 모자라면 추가로 주유 (해당 노드 가격으로)
    - 단, 주유량은 '탱크 한도' 와 '다음 이동에 필요한 연료' 사이에서만 가능
    """
    if start_id not in graph.stations or goal_id not in graph.stations:
        return None

    tank_cap = tank_capacity_l
    km_per_l = fuel_efficiency_km_per_l

    def fuel_needed_for(distance_km: float) -> float:
        return distance_km / km_per_l

    state_count = 0
    pq: List[Tuple[float, int, str, float, List[str], List[Tuple[str, float, float]]]] = []
    heapq.heappush(
        pq,
        (0.0, state_count, start_id, initial_fuel_l, [start_id], [])
    )

    best: Dict[Tuple[str, float], float] = {(start_id, round(initial_fuel_l, 4)): 0.0}

    while pq:
        cost, _, node, fuel, path, refuels = heapq.heappop(pq)
        key = (node, round(fuel, 4))
        if cost > best.get(key, float("inf")):
            continue
        if node == goal_id:
            return FuelPlan(
                total_cost=cost,
                total_distance=sum(
                    _edge_distance(graph, path[i], path[i + 1])
                    for i in range(len(path) - 1)
                ),
                path=path,
                refuels=refuels,
            )

        for edge in graph.adj.get(node, []):
            need = fuel_needed_for(edge.distance_km)
            if need > tank_cap + 1e-9:
                continue
            if fuel + 1e-9 >= need:
                next_cost = cost
                next_fuel = fuel - need
                next_refuels = refuels
            else:
                missing = need - fuel
                station = graph.stations[node]
                add_cost = missing * station.price_per_liter
                next_cost = cost + add_cost
                next_fuel = fuel + (tank_cap - fuel)
                next_refuels = refuels + [(node, missing, station.price_per_liter)]

            next_key = (edge.to, round(next_fuel, 4))
            if next_cost < best.get(next_key, float("inf")) - 1e-9:
                best[next_key] = next_cost
                state_count += 1
                heapq.heappush(
                    pq,
                    (next_cost, state_count, edge.to, next_fuel, path + [edge.to], next_refuels),
                )

    return None


def _edge_distance(graph: FuelGraph, a: str, b: str) -> float:
    for e in graph.adj.get(a, []):
        if e.to == b:
            return e.distance_km
    return 0.0
