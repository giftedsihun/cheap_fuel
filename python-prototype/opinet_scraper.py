"""
opinet_scraper.py
오피넷 API로 실제 주유소 가격 데이터 크롤링
- API 키는 .env 파일에 OPINET_API_KEY 로 보관
- 시도/시군구 코드 매핑 포함
"""
from __future__ import annotations
import os
import time
from typing import List, Optional
import requests
from urllib.parse import urlencode

from data import OpinetConfig, OpinetStation, parse_opinet_json


SIDOS = {
    "전국": "",
    "서울": "01", "부산": "02", "대구": "03", "인천": "04",
    "광주": "05", "대전": "06", "울산": "07", "경기": "08",
    "강원": "09", "충북": "10", "충남": "11", "전북": "12",
    "전남": "13", "경북": "14", "경남": "15", "제주": "16",
    "세종": "02",
}

FUEL_TYPE_MAP = {
    "휘발유": "B027",
    "경유": "B034",
    "LPG": "B031",
}


def get_api_key() -> Optional[str]:
    key = os.environ.get("OPINET_API_KEY", "")
    if not key:
        env_path = os.path.join(os.path.dirname(__file__), ".env")
        if os.path.exists(env_path):
            with open(env_path, encoding="utf-8") as f:
                for line in f:
                    if line.strip().startswith("OPINET_API_KEY="):
                        key = line.strip().split("=", 1)[1].strip()
                        break
    return key or None


def fetch_opinet_stations(
    config: OpinetConfig,
    max_retries: int = 3,
    delay_sec: float = 1.0,
) -> List[OpinetStation]:
    """
    오피넷 API 호출 -> 주유소 리스트 반환
    문서: http://www.opinet.coenet.co.kr/openapi/apiInfo.do
    실제 엔드포인트는 서비스키 필요
    """
    key = config.api_key or (get_api_key() or "")
    if not key:
        raise ValueError("오피넷 API 키가 필요합니다. .env 에 OPINET_API_KEY=… 를 넣거나 인자로 넘기세요.")

    base_url = "https://www.opinet.co.kr/api/avgSidoPrice.do"
    params = {
        "serviceKey": key,
        "sidoCode": config.sido_code,
        "sigunguCode": config.sigungu_code,
        "fuelCls": config.fuel_type,
        "numOfRows": 9999,
        "pageNo": 1,
        "resultType": "json",
    }

    stations: List[OpinetStation] = []
    for attempt in range(max_retries):
        try:
            resp = requests.get(base_url, params=params, timeout=15)
            resp.raise_for_status()
            data = resp.json()
            stations = parse_opinet_json(data, config.fuel_type)
            if stations:
                print(f"[opinet] {len(stations)}개 주유소 수신 (시도={config.sido_code or '전국'})")
            return stations
        except (requests.RequestException, ValueError) as e:
            print(f"[opinet] 시도 {attempt + 1}/{max_retries} 실패: {e}")
            if attempt < max_retries - 1:
                time.sleep(delay_sec * (2 ** attempt))
            else:
                print("[opinet] 모든 재시도 실패 - 샘플 데이터로 대체합니다.")
                return []

    return stations


def build_config_interactive() -> OpinetConfig:
    """CLI 에서 오피넷 API 설정 입력"""
    key = get_api_key()
    if not key:
        key = input("오피넷 API 키를 입력하세요 (Enter=샘플 데이터): ").strip()

    sido = input(f"시도 (기본값=전국 / 예: {', '.join(list(SIDOS)[:5])}): ").strip() or "전국"
    sido_code = SIDOS.get(sido, SIDOS["전국"])

    sigungu = input("시군구 (빈값=전체): ").strip()
    fuel_kor = input(f"연료 종류 (기본값=휘발유 / {list(FUEL_TYPE_MAP)}): ").strip() or "휘발유"
    fuel_code = FUEL_TYPE_MAP.get(fuel_kor, "B027")

    return OpinetConfig(api_key=key, sido_code=sido_code, sigungu_code=sigungu, fuel_type=fuel_code)

