"""
map.py
Folium map visualization for optimal fuel route + stations
"""
from __future__ import annotations
from typing import Optional
import webbrowser
from pathlib import Path

import folium
from folium.plugins import MarkerCluster

from graph import FuelGraph, FuelPlan


def _get_station_color(sid: str, plan: Optional[FuelPlan], start_id: str, goal_id: str) -> str:
    if not plan:
        return "blue"
    if sid == start_id:
        return "green"
    if sid == goal_id:
        return "red"
    refuel_ids = {r[0] for r in plan.refuels}
    if sid in refuel_ids:
        return "orange"
    return "gray"


def create_map(
    graph: FuelGraph,
    plan: Optional[FuelPlan],
    start_id: str,
    goal_id: str,
    output_path: str = "fuel_map.html",
    tile: str = "OpenStreetMap",
) -> str:
    if not graph.stations:
        raise ValueError("Graph has no stations.")

    refs = graph.stations
    start = refs.get(start_id)
    goal = refs.get(goal_id)
    if not start or not goal:
        raise ValueError("Start/goal node not in graph.")

    center_lat = (start.lat + goal.lat) / 2
    center_lon = (start.lon + goal.lon) / 2

    m = folium.Map(location=[center_lat, center_lon], zoom_start=10, tiles=tile)

    cluster = MarkerCluster(name="Stations").add_to(m)

    for sid, s in graph.stations.items():
        popup_html = (
            f"<b>{s.name}</b><br>"
            f"Price: {int(s.price_per_liter):,} KRW/L<br>"
            f"Location: ({s.lat:.4f}, {s.lon:.4f})"
        )
        folium.Marker(
            location=[s.lat, s.lon],
            popup=folium.Popup(popup_html, max_width=200),
            icon=folium.Icon(color=_get_station_color(sid, plan, start_id, goal_id)),
            tooltip=f"{s.name} ({int(s.price_per_liter):,} KRW/L)",
        ).add_to(cluster)

    if plan:
        route_coords = []
        for pid in plan.path:
            st = refs.get(pid)
            if st:
                route_coords.append([st.lat, st.lon])
        folium.PolyLine(
            route_coords,
            weight=4,
            color="#e74c3c",
            opacity=0.8,
            tooltip=f"Optimal Route: {int(plan.total_cost):,} KRW",
        ).add_to(m)

        folium.Marker(
            location=[start.lat, start.lon],
            popup=f"<b>START</b><br>{start.name}",
            icon=folium.Icon(color="green", icon="play"),
        ).add_to(m)
        folium.Marker(
            location=[goal.lat, goal.lon],
            popup=f"<b>GOAL</b><br>{goal.name}",
            icon=folium.Icon(color="red", icon="flag"),
        ).add_to(m)

    folium.LayerControl().add_to(m)
    m.save(output_path)
    return str(Path(output_path).resolve())


def open_in_browser(path: str) -> None:
    webbrowser.open(Path(path).resolve().as_uri())
