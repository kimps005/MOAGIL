# Android 차량 앱

`mockDebug`는 Jetpack Compose 차량용 시연 앱입니다. 가입 없는 이동 생성, 승객 초대 QR, 2초 서버 폴링, Android Keystore 암호화 드라이버 토큰, 우선 승객과 순서 계산·수동 조정, 모의 안내의 성공/실패 재현·재시도 및 단계별 하차를 제공합니다. 안내 결과를 확인하기 전에는 하차·종료할 수 없고, 종료는 확인 대화상자를 거칩니다. 차량 상태는 기본 `UNKNOWN`이며, `정차(시연)` 스위치를 켜기 전 운전자 변경 작업을 비활성화합니다.

## 빌드

필요 환경: JDK 17, Android SDK API 34 및 Build Tools 34.0.0. 실제 SDK의 이용 약관을 승인한 사용자 환경에서 저장소 루트 `vehicle-app` 폴더로 이동해 실행합니다.

```powershell
.\gradlew.bat testMockDebugUnitTest assembleMockDebug
```

APK 경로: `app/build/outputs/apk/mock/debug/app-mock-debug.apk`. SDK와 로컬 주소 설정은 `local.properties` 또는 환경 변수에 두며 Git에 추가하지 않습니다. 이 모듈은 Google Android SDK를 사용하는 것이므로 처음 빌드하는 개발자는 공식 SDK 라이선스와 필요한 패키지를 설치해야 합니다.

## 서버 연결

- Android 에뮬레이터 기본 API 주소: `http://10.0.2.2:8080`
- 실제 스마트폰/차량 기기: 같은 LAN의 PC 서버 주소 (예: `http://192.168.0.10:8080`)
- API 주소와 QR 주소가 다르면 서버의 `MOAGIL_PUBLIC_BASE_URL`을 휴대폰이 접근할 수 있는 LAN 주소로 지정합니다.

HTTP 허용은 `mockDebug` manifest overlay에 한정됩니다. 실제 서비스와 차량에서 사용하지 마세요. 실제 위치, 주행 상태, 지도 길찾기와 Pleos SDK는 구현되지 않았습니다. 상세 상태는 [`../docs/validation.md`](../docs/validation.md)를 참고하세요.
