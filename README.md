# 모아길 (MOAGIL)

여러 동승자가 모바일 화면에서 하차 위치를 등록하면, 운전자가 실제 도로망을 기준으로 순서를 비교하고 차량 내비에 다음 목적지를 보내는 공동 이동 시연 앱입니다.

> **상태** — 웹과 서버는 OpenStreetMap/OSRM 도로망으로 순서를 비교하며 실시간 교통은 반영하지 않습니다. Pleos 에뮬레이터에서는 선택 목적지와 같은 값이 `onDrivingInfo`로 들어오는 것을 확인했습니다. 현재 변경에서는 차량 위치·속도·기어를 읽어 출발 위치와 정차 잠금을 적용하는 코드를 추가했지만, 이 변경분의 APK 빌드와 Pleos 에뮬레이터 검증은 아직 완료되지 않았습니다. 기존 검증 기록은 [검증 문서](docs/validation.md)를 참고하세요.

## 실행하기

필요 환경: Python 3.12 이상, 최신 Chrome/Edge 등 브라우저.

```powershell
.\Start-MOAGIL.ps1
```

차량 화면은 [http://localhost:8080](http://localhost:8080)에서 엽니다. 서버와 웹은 외부 패키지 없이 실행됩니다. 브라우저 참가자는 초대 QR을 스캔해 별명과 핀 위치를 등록할 수 있습니다.

같은 Wi-Fi의 휴대폰에서는 QR 주소에 PC의 사설 IP를 쓸 수 있습니다(예: `http://192.168.0.10:8080`). 다른 네트워크의 휴대폰에서는 PC 서버를 외부에서 접속할 수 있는 HTTPS 주소나 임시 터널에 연결해야 합니다. 서버를 시작하기 전에 QR 주소를 지정합니다.

```powershell
$env:MOAGIL_PUBLIC_BASE_URL = 'https://사용할-공개-주소'
.\Start-MOAGIL.ps1
```

Android 에뮬레이터는 기본 서버 주소 `http://10.0.2.2:8080`을 사용합니다. 같은 Wi-Fi가 아니어도 QR 참여는 외부 공개 주소가 있으면 가능합니다.

## 빠른 시연

1. 차량 화면을 열면 새 세션과 큰 초대 QR이 자동으로 준비됩니다.
2. 참가자가 QR을 스캔해 장소를 검색하고 핀을 조정한 뒤 목적지를 등록합니다. 위치가 추가될 때마다 서버가 추천 순서를 다시 계산합니다.
3. 등록 후 왼쪽 지도와 오른쪽 세션·추천 순서를 확인합니다. 추가 참가자도 같은 QR 링크로 등록할 수 있습니다.
4. Mock 앱은 시연용 정차 스위치를 켭니다. Pleos 앱은 **차량 연결**에서 권한을 허용하고, 실제 차량 속도 0 및 P 기어가 확인되면 순서 조정과 안내 시작이 활성화됩니다.
5. 각 하차를 확인하고 마지막 목적지에서 이동을 종료합니다.

운전자 웹 화면은 QR 대기 화면에서 시작하며, 첫 위치 등록 뒤 지도와 현재 세션·추천 순서 화면으로 전환됩니다.

## 테스트

- `python -m unittest discover -s backend/tests -v` — 경로 계산과 세션 규칙
- `npm run test:ui` — Chrome에서 QR 화면과 위치별 자동 재계산 확인
- `npm run test:e2e` — 실행 중인 로컬 서버와 전체 참가 흐름 확인 (영상은 저장하지 않음)

## 경로 계산과 차량 내비

- 서버는 OSRM 도로 거리표를 사용해 승객 1~4명의 모든 순서를 계산합니다. 4명이면 최대 24가지이며, 한 명을 첫 번째로 고정하면 6가지입니다.
- Pleos NaviHelper는 서버가 선택한 하차 목적지를 한 번에 하나씩 차량 내비로 보냅니다. Pleos SDK가 24개 후보를 평가하는 방식은 사용하지 않습니다.
- 기본 OSRM은 실제 도로망을 사용하지만 실시간 교통은 반영하지 않습니다. 최근 서울 시연 좌표의 24개 순서 계산은 3.10초였습니다. 이는 공개 API 한 번의 측정이며 모든 네트워크에서 5초 이내를 보장하지 않습니다.
- Naver Maps 지도 표시와 Directions 5 경로 비교도 선택할 수 있습니다. 지도 Client ID와 경로 제공자는 서로 독립 설정입니다. Naver 자격 증명이 없어 실제 네이버 계정 호출은 아직 검증되지 않았습니다.

## 목적지 검색

- 참가자는 검색 버튼이나 키보드 Enter로 장소를 찾습니다. 검색 결과를 고르면 지도에 핀이 놓이고, 핀을 끌거나 지도에서 새 위치를 눌러 조정할 수 있습니다.
- 검색은 서버가 Photon 공개 검색 API에 요청하며, 검색어 결과를 6시간 메모리 캐시하고 상위 요청 간격을 조절합니다. 공개 데모 API는 사용량 제한이나 가용성 보장이 없으므로 시연용으로만 사용하세요.
- 지도 아래쪽의 OpenStreetMap 저작자 표시는 지도 데이터 출처를 나타냅니다.

## Naver Maps 설정

1. [Naver Cloud Maps 가이드](https://guide.ncloud-docs.com/docs/maps-app)에서 애플리케이션을 등록하고 **Web Dynamic Map**, **Mobile Dynamic Map**을 켭니다. 네이버 길찾기로 순서를 비교하려는 경우에만 **Directions 5**도 활성화합니다.
2. 웹 호스트와 Android 패키지를 등록합니다. 패키지는 mock `kr.moagil.vehicle.mock`, Pleos `kr.moagil.vehicle`입니다.
3. 발급된 Client ID를 저장소 루트 `.env`의 `MOAGIL_NAVER_CLIENT_ID`와 `vehicle-app/local.properties`의 `naver.map.clientId=`에 넣습니다. `MOAGIL_NAVER_CLIENT_SECRET`은 서버에서 Directions 5를 쓸 때만 `.env`에 넣습니다. `.env`와 `local.properties`는 Git에 올리지 마세요.
4. 서버를 재시작하고 `/api/config`에서 `mapProvider`를 확인합니다. 기본 순서 계산은 계속 OSRM을 사용합니다. Naver Directions 5를 시험할 때만 `MOAGIL_ROUTING_PROVIDER=naver`를 설정하세요.

Directions 5는 네 명의 순서를 한 번 계산할 때 최대 24개 경로 요청을 합니다. 사용 요금과 한도는 Naver Cloud 콘솔에서 확인하세요.

## Android/Pleos 앱

두 Android 변형은 `vehicle-app/`에서 관리합니다. 에뮬레이터용 x86_64 Pleos APK는 [`apk/MOAGIL-pleos-debug-x86_64.apk`](apk/MOAGIL-pleos-debug-x86_64.apk), 일반 Android 기기용 ARM64 APK는 [`apk/MOAGIL-pleos-debug-arm64-v8a.apk`](apk/MOAGIL-pleos-debug-arm64-v8a.apk)입니다. Mock 앱과 체크섬은 [`apk/README.md`](apk/README.md)를 참고하세요.

```powershell
cd vehicle-app
.\gradlew.bat testMockDebugUnitTest assembleMockDebug assemblePleosDebug
```

에뮬레이터는 `http://10.0.2.2:8080`으로 PC 서버에 접속합니다. Pleos 앱은 NaviHelper, FusedLocation, 차량 속도와 파워트레인 권한을 사용합니다. 차량 위치·속도·P 기어 연동은 현재 APK에서 아직 확인되지 않았습니다. 앱 권한은 Pleos Playground에서 승인된 범위에 있어야 합니다.

## 확인된 항목과 제한

| 항목 | 상태 |
|---|---|
| 모바일 참가·서버 동기화·4명 제한 | 구현 및 브라우저 E2E 확인 |
| OSRM 순서 계산과 실제 도로 형상 | 구현 및 E2E 확인 |
| Naver Maps / Directions 5 | 연결 코드 준비, API 키가 없어 실제 계정 호출 미검증 |
| Pleos 내비 목적지 전달 | 에뮬레이터 Navigation 화면에서 확인, `onDrivingInfo`로 선택 목적지 일치 확인 |
| Pleos `onRoutingCompleted` | 에뮬레이터 테스트에서 미수신. `onDrivingInfo` 확인으로 목적지 적용 여부는 검증 |
| Pleos POI 상세 API | 비어 있는 `poiId`를 받아 부가 조회가 HTTP 400. 경로 화면과 목적지 콜백에는 영향 없음 |
| 차량 위치·속도·기어 코드 | Pleos FusedLocation 및 AAOS CarPropertyManager 연동 추가. 이 변경분은 빌드/AVD 미검증 |
| 차량 센서 수신·실차 주행·실제 스마트폰 외부 QR | 미검증 |

Pleos NaviHelper 문서는 `onRouteStarted`를 탐색 시작, `onRoutingCompleted`를 탐색 완료로 구분합니다. 현 에뮬레이터에서는 완료 이벤트가 오지 않아, 앱은 요청 후 내비 주행 정보에 요청한 목적지가 동일하게 들어왔는지 추가 확인합니다. POI 상세 오류를 없애려면 유효한 Pleos POI ID를 제공하는 검색 흐름이 필요하며, 현재 사용하는 NaviHelper 문서에는 POI 검색 API가 포함되어 있지 않습니다. 자세한 실행 기록은 [`docs/validation.md`](docs/validation.md)에 있습니다.

시연 영상은 요청에 따라 촬영하지 않았습니다. [`docs/DEMO.md`](docs/DEMO.md)는 추후 시연을 위한 스토리보드입니다.

## 문서

- [구성 및 상태 모델](docs/architecture.md)
- [검증 기록](docs/validation.md)
- [Android 앱과 빌드 안내](vehicle-app/README.md)
- [APK 다운로드와 체크섬](apk/README.md)
- [시연 스토리보드](docs/DEMO.md)
