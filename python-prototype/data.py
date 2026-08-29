"""
data.py
샘플 주유소 데이터 + 오피넷 API 데이터 구조
"""
from __future__ import annotations
from dataclasses import dataclass
from typing import List, Dict, Optional
import json


@dataclass
class OpinetStation:
    """오피넷 주유소 데이터 표준 포맷"""
    id: str
    name: str
    brand: str
    lat: float
    lon: float
    address: str
    price_whi: float   # 휘발유
    price_dis: float   # 경유
    price_lpg: float   # LPG
    updated_at: str    # ISO8601

    def to_station(self, fuel_type: str = "whi") -> "Station":
        """graph.Station 으로 변환"""
        from graph import Station
        price_map = {
            "whi": self.price_whi,
            "dis": self.price_dis,
            "lpg": self.price_lpg,
        }
        return Station(
            id=self.id,
            name=self.name,
            lat=self.lat,
            lon=self.lon,
            price_per_liter=price_map.get(fuel_type, self.price_whi),
        )


SAMPLE_STATIONS = [
    {
        "id": "S001",
        "name": "서울역 주유소",
        "brand": "SK에너지",
        "lat": 37.5546,
        "lon": 126.9706,
        "address": "서울특별시 중구 통일로 1",
        "price_whi": 1780,
        "price_dis": 1680,
        "price_lpg": 1100,
        "updated_at": "2026-08-29T09:00:00",
    },
    {
        "id": "S002",
        "name": "남대문시장 주유소",
        "brand": "GS칼텍스",
        "lat": 37.5630,
        "lon": 126.9780,
        "address": "서울특별시 중구 남대문시장4길 28",
        "price_whi": 1750,
        "price_dis": 1650,
        "price_lpg": 1090,
        "updated_at": "2026-08-29T09:00:00",
    },
    {
        "id": "S003",
        "name": "을지로입구 주유소",
        "brand": "현대오일뱅크",
        "lat": 37.5652,
        "lon": 126.9860,
        "address": "서울특별시 중구 을지로 100",
        "price_whi": 1790,
        "price_dis": 1690,
        "price_lpg": 1110,
        "updated_at": "2026-08-29T09:00:00",
    },
    {
        "id": "S004",
        "name": "종로3가 주유소",
        "brand": "S-OIL",
        "lat": 37.5707,
        "lon": 126.9913,
        "address": "서울특별시 종로구 종로 157",
        "price_whi": 1760,
        "price_dis": 1660,
        "price_lpg": 1080,
        "updated_at": "2026-08-29T09:00:00",
    },
    {
        "id": "S005",
        "name": "강남역 주유소",
        "brand": "SK에너지",
        "lat": 37.4980,
        "lon": 127.0276,
        "address": "서울특별시 강남구 테헤란로 100",
        "price_whi": 1850,
        "price_dis": 1750,
        "price_lpg": 1150,
        "updated_at": "2026-08-29T09:00:00",
    },
    {
        "id": "S006",
        "name": "역삼동 주유소",
        "brand": "GS칼텍스",
        "lat": 37.5010,
        "lon": 127.0380,
        "address": "서울특별시 강남구 역삼로 200",
        "price_whi": 1820,
        "price_dis": 1720,
        "price_lpg": 1130,
        "updated_at": "2026-08-29T09:00:00",
    },
    {
        "id": "S007",
        "name": "잠실 주유소",
        "brand": "현대오일뱅크",
        "lat": 37.5133,
        "lon": 127.1000,
        "address": "서울특별시 송파구 올림픽로 240",
        "price_whi": 1800,
        "price_dis": 1700,
        "price_lpg": 1120,
        "updated_at": "2026-08-29T09:00:00",
    },
    {
        "id": "S008",
        "name": "판교 주유소",
        "brand": "S-OIL",
        "lat": 37.3945,
        "lon": 127.1110,
        "address": "경기도 성남시 분당구 판교역로 152",
        "price_whi": 1770,
        "price_dis": 1670,
        "price_lpg": 1090,
        "updated_at": "2026-08-29T09:00:00",
    },
    {
        "id": "S009",
        "name": "수원역 주유소",
        "brand": "SK에너지",
        "lat": 37.2635,
        "lon": 127.0286,
        "address": "경기도 수원시 팔달구 덕영대로 924",
        "price_whi": 1740,
        "price_dis": 1640,
        "price_lpg": 1070,
        "updated_at": "2026-08-29T09:00:00",
    },
    {
        "id": "S010",
        "name": "인천공항 주유소",
        "brand": "GS칼텍스",
        "lat": 37.4530,
        "lon": 126.4410,
        "address": "인천광역시 중구 공항로 272",
        "price_whi": 1880,
        "price_dis": 1780,
        "price_lpg": 1180,
        "updated_at": "2026-08-29T09:00:00",
    },
]


def load_sample_stations() -> List[OpinetStation]:
    return [OpinetStation(**s) for s in SAMPLE_STATIONS]


def create_sample_graph(fuel_type: str = "whi") -> "FuelGraph":
    """샘플 데이터로 FuelGraph 생성 (직선거리 기반 임시 엣지)"""
    from graph import FuelGraph, Station

    stations = load_sample_stations()
    graph = FuelGraph()

    for s in stations:
        graph.add_station(s.to_station(fuel_type))

    ids = [s.id for s in stations]
    for i in range(len(ids)):
        for j in range(i + 1, len(ids)):
            dist = graph.distance(ids[i], ids[j])
            if dist <= 25.0:
                graph.add_edge(ids[i], ids[j], dist)

    return graph


OPINET_API_URL = "https://www.opinet.co.kr/api/avgSidoPrice.do"
OPINET_STATION_SEARCH = "https://www.opinet.co.kr/api/searchStation.do"


@dataclass
class OpinetConfig:
    """오피넷 API 설정"""
    api_key: str
    sido_code: str = ""    # 시도 코드 (빈값=전체)
    sigungu_code: str = "" # 시군구 코드
    fuel_type: str = "B027"  # B027=휘발유, B034=경유, B031=LPG


def parse_opinet_json(data: dict, fuel_type: str = "B027") -> List[OpinetStation]:
    """오피넷 API 응답 파싱 (예시 구조)"""
    stations = []
    for item in data.get("result", {}).get("oilPriceList", []):
        try:
            stations.append(OpinetStation(
                id=item.get("UNI_ID", ""),
                name=item.get("OS_NM", ""),
                brand=item.get("POLL_DIV_CO", ""),
                lat=float(item.get("LAT", 0)),
                lon=float(item.get("LON", 0)),
                address=item.get("ADDR", ""),
                price_whi=float(item.get("PRICE", 0)),
                price_dis=float(item.get("PRICE", 0)),
                price_lpg=float(item.get("PRICE", 0)),
                updated_at=item.get("UPD_DT", ""),
            ))
        except (ValueError, KeyError):
            continue
    return stations


def save_stations_json(stations: List[OpinetStation], path: str) -> None:
    with open(path, "w", encoding="utf-8") as f:
        json.dump([s.__dict__ for s in stations], f, ensure_ascii=False, indent=2)


def load_stations_json(path: str) -> List[OpinetStation]:
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    return [OpinetStation(**d) for d in data]