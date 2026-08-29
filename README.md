# Cheap Fuel - 연료 최적화 경로 탐색기

고유가 시대, 주유소 가격과 이동 비용을 동시에 고려하여 **최소 비용 주유 경로**를 찾아주는 Android 앱입니다.

## 그래프 알고리즘

```
노드(Node) = 주유소
엣지(Edge) = 두 주유소 사이 도로 구간 (거리 km)

상태 전이: (현재 노드, 남은 연료) -> 최소 누적 비용
- Dijkstra (최단 경로) + DP (탱크 내 연료 상태 관리)
- 각 주유소에서 "다음 노드까지 필요한 양만 주유"하는 전략
```

## 주요 기능

- **시작/도착 주유소 선택** (샘플 10개 또는 오피넷 API)
- **차량 연비 & 탱크 용량** 입력
- **Dijkstra + DP** 기반 최소비용 경로 탐색
- **결과**: 총 비용, 총 거리, 경로 순서, 주유 내역
- **무료 지도** (OpenStreetMap + Leaflet.js, API 키 불필요)
- **오피넷 API** 연동 가능 (API 키 입력만으로 실데이터)

## 프로젝트 구조

```
cheap_fuel/
├── android-app/                # Android 앱 (Jetpack Compose + Kotlin)
│   ├── app/src/main/
│   │   ├── java/com/fueloptimizer/
│   │   │   ├── MainActivity.kt
│   │   │   ├── graph/
│   │   │   │   ├── FuelGraph.kt         # 그래프 자료구조
│   │   │   │   └── FuelRouteFinder.kt   # Dijkstra + DP 알고리즘
│   │   │   ├── data/
│   │   │   │   └── SampleStations.kt    # 샘플 주유소 데이터
│   │   │   ├── network/
│   │   │   │   ├── OpinetApi.kt         # Retrofit API 정의
│   │   │   │   ├── OpinetModels.kt      # API 데이터 모델
│   │   │   │   ├── OpinetRepository.kt  # Repository 패턴
│   │   │   │   └── NetworkModule.kt     # OkHttp + Retrofit
│   │   │   └── ui/screens/
│   │   │       ├── MainViewModel.kt     # ViewModel (MVVM)
│   │   │       ├── InputScreen.kt       # 입력 화면
│   │   │       ├── ResultScreen.kt       # 결과 화면
│   │   │       └── FreeMapScreen.kt     # Leaflet 지도 (WebView)
│   │   └── assets/
│   │       └── freemap.html             # OpenStreetMap + Leaflet
│   ├── build.gradle.kts
│   └── settings.gradle.kts
│
├── python-prototype/           # Python 프로토타입 (테스트/검증용)
│   ├── graph.py               # Dijkstra + DP (Python)
│   ├── data.py                # 샘플 데이터 + 오피넷 파싱
│   ├── map.py                 # Folium 지도 시각화
│   ├── opinet_scraper.py      # 오피넷 API 크롤러
│   └── main.py                # CLI 인터페이스
│
└── README.md
```

## 설치 및 빌드

### Android 앱

```bash
# Android SDK, Java 17 필요
cd android-app
./gradlew assembleDebug

# APK 위치
# android-app/app/build/outputs/apk/debug/app-debug.apk
```

### Python 프로토타입

```bash
cd python-prototype
pip install -r requirements.txt
python main.py
```

## 사용법

1. **앱 실행** → 시작/도착 주유소 선택
2. **연비** (km/L) 입력, 예: `12`
3. **탱크 용량** (L) 입력, 예: `50`
4. **Find cheapest route** 버튼
5. 결과: 총 비용 + 경로 + 주유 내역
6. **Map 탭**: 경로가 무료 지도에 표시됨

## 오피넷 API 연동 (선택)

1. https://www.opinet.co.kr 에서 API 키 발급
2. 앱 실행 → "Load real stations" → API 키 입력
3. 실제가격 기반 최적 경로 탐색

## 기술 스택

| 레이어 | 기술 |
|--------|------|
| Android UI | Jetpack Compose + Material 3 |
| 아키텍처 | MVVM (ViewModel) |
| 네트워크 | Retrofit + OkHttp + Gson |
| 지도 | OpenStreetMap + Leaflet.js (WebView) |
| 알고리즘 | Dijkstra + DP (PriorityQueue) |
| 빌드 | Gradle 8.5 + Kotlin 1.9 |

## 라이선스

MIT License
