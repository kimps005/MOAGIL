# Android 차량 앱

## 앱 변형

- `mockDebug` (`kr.moagil.vehicle.mock`): Compose 시연 앱. 이동 생성, QR 참가·서버 동기화, 경로 계산과 수동 순서 변경, 모의 안내 재시도 및 단계별 하차를 제공합니다.
- `pleosDebug` (`kr.moagil.vehicle`): 같은 화면에서 선택한 다음 하차 목적지를 Pleos Connect NaviHelper에 보냅니다. SDK 완료 콜백 또는 요청한 목적지와 일치하는 내비 주행 정보 콜백을 확인한 뒤에만 하차를 허용합니다.

앱 첫 실행은 빈 이동과 큰 초대 QR을 자동으로 준비합니다. 이전 이동이 저장돼 있으면 다시 연결하며, 진행 중이 아닌 화면의 오른쪽 위 **새 이동**으로 새 QR을 만들 수 있습니다. Mock과 Pleos를 동시에 설치하면 앱이 둘로 보이므로, Pleos 에뮬레이터 테스트에는 `pleosDebug`만 설치하세요. 앱 이름도 각각 **모아길 Mock**, **모아길 Pleos**로 구분됩니다.

둘 다 운전자 동작 전에 `정차(시연)` 상태를 켜야 합니다. 차량 센서는 연결되지 않습니다. 지도와 도로 경로 계산에는 Kakao만 사용합니다. Pleos 앱은 선택된 순서대로 목적지 하나씩 Pleos NaviHelper에 보냅니다. 각 하차를 모아길에서 확인한 뒤 현재 위치 기준으로 다음 목적지를 요청하며, 최종 목적지는 승객 하차 후 별도로 요청합니다. 무료 도로 선택 시 매 요청에 NaviHelper `FREE` 옵션을 설정합니다. Kakao가 통행료 0원을 확인해 주지 않으면 무료 경로를 저장하지 않습니다. 차량 안내는 Pleos 내비가 담당합니다. Kakao 연결이 실패하면 대체 지도를 표시하지 않고 오류를 보여 줍니다. 기존 테스트와 SDK 콜백 기록은 [`../docs/validation.md`](../docs/validation.md)를 참고하세요.

## 빌드

필요 환경: JDK 17, Android SDK API 34 및 Build Tools 34.0.0, Android SDK 라이선스 동의. 저장소 루트의 `vehicle-app` 폴더에서 실행합니다.

```powershell
.\gradlew.bat testMockDebugUnitTest assembleMockDebug assemblePleosDebug
```

Pleos SDK는 공식 Maven 저장소의 `ai.pleos.playground:NaviHelper:3.0.5`입니다. `pleosDebug`는 목적지 설정과 내비 화면 전환을 위해 `pleos.car.permission.NAVI_ROUTE_SEARCH`, `pleos.car.permission.NAVI_CUSTOM_MAP` 권한을 요청합니다. 에뮬레이터용 x86_64 APK와 Android 기기용 ARM64 APK는 `app/build/outputs/apk/pleos/debug/`에 생성됩니다. Mock APK는 `app/build/outputs/apk/mock/debug/`에 생성됩니다.

기본 연결 주소는 고정 배포 주소 `https://moagil-eta.vercel.app`입니다. 이 주소를 Kakao Developers의 JavaScript SDK 허용 도메인에 등록하면 에뮬레이터와 차량 앱에서 LAN IP가 바뀌어도 같은 허용 주소를 사용합니다. 이전에 저장한 로컬 서버 주소는 새 세션을 만들 때 배포 주소로 자동 전환됩니다. 로컬 서버를 직접 시험할 때만 앱의 서버 주소 설정에서 `http://10.0.2.2:8080` 또는 기기의 LAN 주소를 사용하고 해당 주소를 Kakao 허용 도메인에 추가하세요. 기존 `local.properties`의 `sdk.dir` 줄은 유지하고 이 파일은 Git에 추가하지 않습니다.

## 서버 주소

- Android 에뮬레이터·차량 앱 기본값: `https://moagil-eta.vercel.app`
- Android 에뮬레이터에서 PC 로컬 서버를 직접 시험할 때: `http://10.0.2.2:8080`
- 스마트폰/차량 기기: PC LAN 주소 또는 외부에서 접속할 수 있는 HTTPS 도메인/터널
- QR 공개 주소가 API 주소와 다르면 서버 환경 변수 `MOAGIL_PUBLIC_BASE_URL`을 설정합니다.

호스트의 현재 기본 경로와 DNS를 읽어 에뮬레이터에 전달합니다. DNS 주소를 특정 네트워크 IP로 고정하지 않습니다.

```powershell
$route = Get-NetRoute -DestinationPrefix '0.0.0.0/0' | Sort-Object RouteMetric | Select-Object -First 1
$dns = (Get-DnsClientServerAddress -InterfaceAlias $route.InterfaceAlias -AddressFamily IPv4).ServerAddresses -join ','
emulator.exe -avd Pleos_Connect_v2.1.0 -dns-server $dns -no-snapshot-load -no-window -no-audio
```

실제 Navigation route 완료, 실차 센서와 실제 주행은 검증 범위 밖입니다. 앱은 시연용이며 실차 운행에 사용하지 마세요.
