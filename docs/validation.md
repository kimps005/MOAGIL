# 검증 기록

마지막 갱신: 2026-10-30 (Asia/Seoul)

## 완료한 검사

| 구분 | 실행 | 결과 |
|---|---|---|
| Backend syntax | 번들 Python 3.12.14로 `py_compile backend/domain.py backend/server.py backend/tests/test_domain.py` | PASS |
| Route unit tests | `python -m unittest discover -s backend/tests -v` | PASS · 5개 |
| Frontend syntax | `node --check web/app.js` | PASS |
| Browser E2E | Playwright + Chrome, localhost mock server | PASS · 모바일 참가 등록/동기화, 좌표 직접 입력, 인증·정보 경계, 중복 재시도, 4명 제한, version 충돌, 정차 게이트, 24→6 순열, 수동 순서, 단계별 완료 |
| 시연 영상 | `node demo/run-demo.cjs --record` | PASS · `demo/video/MOAGIL-demo.webm`, QR/토큰 가림 보호 모드 |
| Android 빌드 도구 | 공식 SHA-256을 확인한 JDK 17 / Gradle 8.7, Gradle Wrapper 생성 | PASS |

## 환경상 미검증

| 기준 | 상태 | 이유 / 다음 확인 |
|---|---|---|
| 별도 휴대폰에서 QR 스캔 및 LAN 동기화 (AC02) | 미검증 | 실제 스마트폰이 연결되지 않았습니다. 같은 Wi-Fi에서 QR 스캔과 5초 이내 동기화를 확인해야 합니다. |
| API/browser 통합 (AC03/06/09/10) | PASS · 로컬 Chrome E2E | 모바일 등록/동기화, 인증, 중복 재시도, 4명 제한, version 충돌, 경로 순서/수동 조정, 단계별 mock 안내를 확인했습니다. 실제 휴대폰 재접속 및 LAN 동작은 포함하지 않습니다. |
| Android mock debug 빌드 및 설치 (AC01/07/08/12) | 차단 | `:app:compileMockDebugKotlin` 실행 시 SDK location을 찾을 수 없어 중단됐습니다. Android SDK/API 34 및 Build Tools 34.0.0, Emulator/ADB가 없습니다. SDK 패키지를 설치하기 전에 Android SDK 라이선스 동의가 필요하며, 아직 동의하지 않았습니다. APK와 Kotlin 컴파일 결과는 생성되지 않았습니다. |
| 네이버 Directions 5 / Pleos SDK (P1) | BLOCKED / 미구현 | 네이버 인증 정보, Pleos SDK 접근 권한 및 공식 에뮬레이터가 제공되지 않았습니다. 실제 SDK 메서드나 결과 이벤트를 추정하지 않았습니다. |

## 범위 판단

- 브라우저 UI, SQLite 서버, 토큰 해시, idempotency 및 순열 계산은 P0 mock 경로로 구현했습니다.
- Kotlin/Compose 차량 앱과 Gradle 빌드 설정은 `vehicle-app/`에 작성했고 Wrapper/JDK는 준비했습니다. Android SDK 이용 약관과 API 패키지 설치가 없어 네이티브 소스는 컴파일되지 않았고 APK도 없습니다. 따라서 PRD의 P0 전체 및 AC01/AC07/AC12는 완료로 판정하지 않습니다.
- 영상은 승인된 브라우저 기반 시연에서 생성하며, 휴대폰 패널은 시연용 가상 브라우저입니다. 촬영 전 QR 토큰이 가려져 있는지 확인합니다.
