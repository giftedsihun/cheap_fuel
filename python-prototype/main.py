"""
main.py
Fuel Route Optimizer CLI

User inputs fuel efficiency and tank capacity.
Dijkstra+DP computes the minimum fuel cost route.
Renders the result on a Folium map.
"""
from __future__ import annotations
import sys
from typing import List, Tuple

from graph import FuelGraph, FuelPlan, find_min_fuel_route
from data import create_sample_graph, load_sample_stations, OpinetStation
from opinet_scraper import fetch_opinet_stations, build_config_interactive, OpinetConfig
from map import create_map, open_in_browser


def list_stations(stations: List[OpinetStation]) -> None:
    print("\n=== Available Stations ===")
    for i, s in enumerate(stations):
        print(f"  {i+1}. {s.name} ({s.address})  Gasoline: {int(s.price_whi):,} KRW/L")


def get_input_station(prompt: str, stations: List[OpinetStation]) -> OpinetStation:
    while True:
        try:
            num = int(input(prompt).strip()) - 1
            if 0 <= num < len(stations):
                return stations[num]
            print(f"Please enter a number between 1 and {len(stations)}.")
        except ValueError:
            print("Please enter a valid number.")


def print_plan(plan: FuelPlan) -> None:
    print("\n" + "=" * 60)
    print("         Optimal Route Result")
    print("=" * 60)
    print(f"  Total Distance : {plan.total_distance:.1f} km")
    print(f"  Total Cost     : {int(plan.total_cost):,} KRW")
    print("-" * 60)
    print("  Path:")
    for i, pid in enumerate(plan.path):
        sep = " -> " if i < len(plan.path) - 1 else ""
        print(f"    {pid}{sep}", end="")
    print()
    if plan.refuels:
        print("\n  Refuel Stops:")
        for sid, liters, price in plan.refuels:
            print(f"    {sid}: {liters:.1f}L @ {int(price):,} KRW/L = {int(liters*price):,} KRW")
    else:
        print("\n  (No refuel needed)")
    print("=" * 60)


def load_graph() -> Tuple[FuelGraph, List[OpinetStation]]:
    use_opinet = input("Use Opinet real data? (y/n, default=n): ").strip().lower()
    if use_opinet == "y":
        config = build_config_interactive()
        stations = fetch_opinet_stations(OpinetConfig(
            api_key=config.api_key,
            sido_code=config.sido_code,
            sigungu_code=config.sigungu_code,
            fuel_type=config.fuel_type,
        ))
    else:
        print("\nUsing sample data (Seoul/Gyeonggi/Incheon/Suwon stations).")
        stations = load_sample_stations()
    if not stations:
        print("Failed to load station data.")
        sys.exit(1)
    graph = create_sample_graph()
    return graph, stations


def get_user_params() -> Tuple[float, float]:
    while True:
        try:
            fe = float(input("\nVehicle fuel efficiency (km/L, e.g. 12): ").strip())
            if fe > 0:
                break
            print("Please enter a positive number.")
        except ValueError:
            print("Please enter a number.")
    while True:
        try:
            cap = float(input("Fuel tank capacity (L, e.g. 50): ").strip())
            if cap > 0:
                break
            print("Please enter a positive number.")
        except ValueError:
            print("Please enter a number.")
    return fe, cap


def main():
    print("=" * 60)
    print("  Fuel Route Optimizer (Dijkstra + DP)")
    print("  Minimum-cost refueling path for high gas prices")
    print("=" * 60)

    graph, stations = load_graph()
    list_stations(stations)

    start = get_input_station("\nStart station number: ", stations)
    goal = get_input_station("Destination station number: ", stations)
    fe, cap = get_user_params()

    print(f"\nSearching... ({start.name} -> {goal.name})")
    plan = find_min_fuel_route(graph, start.id, goal.id, fe, cap)

    if plan is None:
        print(f"\nNo reachable path to '{goal.name}'.")
        print("Try increasing tank capacity or adding more stations.")
        sys.exit(1)

    print_plan(plan)

    map_path = f"fuel_map_{start.id}_{goal.id}.html"
    map_url = create_map(graph, plan, start.id, goal.id, output_path=map_path)
    print(f"\nMap saved: {map_url}")
    open_in_browser(map_path)

    print("\nMap opened in browser.")
    print("To port to Android: translate graph/data/map logic to Kotlin/Java.")


if __name__ == "__main__":
    main()