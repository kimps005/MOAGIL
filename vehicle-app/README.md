# Android 차량 앱

이 모듈에는 두 빌드 변형이 있습니다.

- `mockDebug` (`kr.moagil.vehicle.mock`): Compose 차량용 시연 앱입니다. 이동 생성, QR 참가, 서버 폴링, 순서 계산·수동 편집, 모의 안내 성공/실패·재시도 및 단계별 하차를 제공합니다.
- `pleosDebug` (`kr.moagil.vehicle`): 같은 흐름에서 현재 선택한 하차 목적지 하나를 Pleos Connect NaviHelper에 전달합니다. 요청/완료 대기 제한은 5초이며, 완료 콜백 전에는 하차 완료를 처리하지 않습니다. 공개된 SDK 문서에서 경로 비용 행렬 API를 확인하지 못해 여러 순서 평가는 서버 OSRM이 담당합니다.

둘 다 운전자 동작 전에 `정차(시연)`을 켜야 합니다. 이 스위치는 실제 차량 센서가 아닙니다. Pleos 에뮬레이터에서 SDK를 연결하고 내비 앱을 여는 것까지 확인했지만, 에뮬레이터의 외부 Wi-Fi 연결이 없어서 실제 경로 완료 콜백은 아직 검증되지 않았습니다. 실차 주행에는 사용하지 마세요.

## 빌드

필요 환경: JDK 17, Android SDK API 34 및 Build Tools 34.0.0, Android SDK 라이선스 동의. 저장소 루트의 `vehicle-app` 폴더에서 실행합니다.

```powershell
.\gradlew.bat testMockDebugUnitTest assembleMockDebug assemblePleosDebug
```

SDK 의존성은 공식 Pleos Maven 저장소의 `ai.pleos.playground:NaviHelper:3.0.5`입니다. `pleosDebug` 변형은 `pleos.car.permission.NAVI_ROUTE_SEARCH` 권한을 선언합니다. 에뮬레이터용 Pleos APK 경로는 `app/build/outputs/apk/pleos/debug/app-pleos-x86_64-debug.apk`, ARM64용은 `app/build/outputs/apk/pleos/debug/app-pleos-arm64-v8a-debug.apk`입니다. Mock APK도 같은 하위 경로의 `mock/debug`에 생성됩니다.

네이버 지도 Client ID는 `local.properties`에 `naver.map.clientId=발급받은_Client_ID`로 설정합니다. 기존 `sdk.dir` 줄을 유지하고 이 파일은 Git에 추가하지 않습니다. 네이버 애플리케이션에 지도 앱을 등록할 경우 mock 패키지 `kr.moagil.vehicle.mock`과 Pleos 패키지 `kr.moagil.vehicle`을 필요한 빌드 변형에 맞춰 등록하세요.

## 서버 연결

- Android 에뮬레이터 기본 API 주소: `http://10.0.2.2:8080`
- 실제 스마트폰/차량 기기: PC LAN 주소 또는 외부 HTTPS 도메인/터널 주소
- API 주소와 QR 주소가 다르면 서버의 `MOAGIL_PUBLIC_BASE_URL`을 휴대폰이 접근할 수 있는 주소로 지정합니다. 서로 다른 Wi-Fi를 쓸 때는 외부 HTTPS 도메인이나 터널이 필요합니다.

HTTP 허용은 개발용 manifest overlay에 한정됩니다. Pleos 최종 내비 요청은 NaviHelper SDK를 사용하며, `pleosDebug`에서만 Pleos SDK가 포함됩니다. 기본 서버 경로 비교는 OSRM 도로망이며, 네이버 Directions 5는 서버에서 명시적으로 선택할 수 있습니다. 상세 검증 결과와 미확인 항목은 [`../docs/validation.md`](../docs/validation.md)를 참고하세요.
