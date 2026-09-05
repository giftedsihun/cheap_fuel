# Gas Smart Android

gas-smart (Next.js 웹 앱)의 한국형 주유 최적화 경로 탐색기를 Android 네이티브 앱으로 포팅한 프로젝트입니다.

## 기술 스택
- Kotlin
- Jetpack Compose (Material 3)
- MVVM (ViewModel + StateFlow)
- Compose Navigation
- Retrofit + OkHttp (오피넷 API)

## 기능
- 출발/도착 검색
- 차량 조건 입력 (유종, 연비, 탱크용량, 현재연료, 예비량)
- 주유 정책 (필요한 만큼/가득/정량/정액)
- 시간 가치 및 할인 설정
- 브랜드/셀프/고속도로 진출 필터
- 다회 주유 일정 (Itinerary)
- 우회 손익분기 분석 (BreakEvenDetourKm)
- 정규화 비용 비교
- 토스 스타일 UI

## 빌드
GitHub Actions에서 자동으로 APK 빌드 및 Release에 업로드됩니다.

- 저장소: https://github.com/giftedsihun/gas-smart-android
- Actions: https://github.com/giftedsihun/gas-smart-android/actions

## 구조
```
app/src/main/java/com/fueloptimizer/
├── domain/              # 비용 모델 (순수 Kotlin 함수)
│   ├── types.kt
│   └── cost.kt
├── network/             # 오피넷 API
│   └── OpinetApi.kt
├── presentation/        # UI 레이어
│   ├── MainActivity.kt
│   ├── MainViewModel.kt
│   ├── home/HomeScreen.kt
│   ├── result/ResultScreen.kt
│   ├── settings/SettingsScreen.kt
│   └── navigation/GasSmartNavigation.kt
├── ui/                  # UI 공통
│   ├── theme/Theme.kt
│   └── components/Components.kt
└── FuelApp.kt
```

## 원본 프로젝트
- gas-smart: https://github.com/DIEBR2708/gas-smart
