# Android 차량 앱

## 앱 변형

- `mockDebug` (`kr.moagil.vehicle.mock`): Compose 시연 앱. 이동 생성, QR 참가·서버 동기화, 경로 계산과 수동 순서 변경, 모의 안내 재시도 및 단계별 하차를 제공합니다.
- `pleosDebug` (`kr.moagil.vehicle`): 같은 화면에서 선택한 다음 하차 목적지를 Pleos Connect NaviHelper에 보냅니다. SDK 완료 콜백 또는 요청한 목적지와 일치하는 내비 주행 정보 콜백을 확인한 뒤에만 하차를 허용합니다.

앱 첫 실행은 빈 이동과 큰 초대 QR을 자동으로 준비합니다. 이전 이동이 저장돼 있으면 다시 연결하며, 진행 중이 아닌 화면의 오른쪽 위 **새 이동**으로 새 QR을 만들 수 있습니다. Mock과 Pleos를 동시에 설치하면 앱이 둘로 보이므로, Pleos 에뮬레이터 테스트에는 `pleosDebug`만 설치하세요. 앱 이름도 각각 **모아길 Mock**, **모아길 Pleos**로 구분됩니다.

둘 다 운전자 동작 전에 `정차(시연)` 상태를 켜야 합니다. 차량 센서는 연결되지 않습니다. Pleos 앱은 선택된 목적지만 전달하고, 가능한 승객 순서는 서버의 OSRM 실제 도로망으로 평가합니다. 에뮬레이터에서는 `시연 지점 B`, 20km, 71분이 Pleos Navigation에 표시됐고 앱의 `onDrivingInfo` 콜백에서 목적지가 확인됐습니다. 경로 완료 전용 `onRoutingCompleted`는 오지 않았습니다. Pleos 앱의 보조 POI 상세 조회는 빈 ID로 HTTP 400을 기록하지만 경로와 목적지 확인은 동작합니다. 상세 기록은 [`../docs/validation.md`](../docs/validation.md)를 참고하세요.

## 빌드

필요 환경: JDK 17, Android SDK API 34 및 Build Tools 34.0.0, Android SDK 라이선스 동의. 저장소 루트의 `vehicle-app` 폴더에서 실행합니다.

```powershell
.\gradlew.bat testMockDebugUnitTest assembleMockDebug assemblePleosDebug
```

Pleos SDK는 공식 Maven 저장소의 `ai.pleos.playground:NaviHelper:3.0.5`입니다. `pleosDebug`만 `pleos.car.permission.NAVI_ROUTE_SEARCH` 권한을 요청합니다. 에뮬레이터용 x86_64 APK와 Android 기기용 ARM64 APK는 `app/build/outputs/apk/pleos/debug/`에 생성됩니다. Mock APK는 `app/build/outputs/apk/mock/debug/`에 생성됩니다.

네이버 Android 지도를 쓰려면 `local.properties`에 `naver.map.clientId=발급받은_Client_ID`를 추가합니다. 기존 `sdk.dir` 줄은 유지하고 이 파일은 Git에 추가하지 않습니다. 네이버 앱 등록 시 mock 패키지 `kr.moagil.vehicle.mock`과 Pleos 패키지 `kr.moagil.vehicle`을 각각 등록하세요.

## 서버 주소

- Android 에뮬레이터: `http://10.0.2.2:8080`
- 스마트폰/차량 기기: PC LAN 주소 또는 외부에서 접속할 수 있는 HTTPS 도메인/터널
- QR 공개 주소가 API 주소와 다르면 서버 환경 변수 `MOAGIL_PUBLIC_BASE_URL`을 설정합니다.

Pleos DNS 기본 전달이 실패하는 AVD는 에뮬레이터를 다음 옵션으로 재시작해 해결했습니다.

```powershell
emulator.exe -avd Pleos_Connect_v2.1.0 -dns-server 172.20.10.1 -no-snapshot-load -no-window -no-audio
```

실제 Navigation route 완료, 실차 센서와 실제 주행은 검증 범위 밖입니다. 앱은 시연용이며 실차 운행에 사용하지 마세요.
