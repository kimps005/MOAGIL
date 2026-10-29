# 검증 기록

마지막 갱신: 2026-10-30 (Asia/Seoul)

## 완료한 검사

| 구분 | 실행 | 결과 |
|---|---|---|
| Backend syntax | 번들 Python 3.12.14로 `py_compile backend/domain.py backend/server.py backend/tests/test_domain.py backend/tests/test_server_guards.py` | PASS |
| Backend unit tests | `python -m unittest discover -s backend/tests -v` | PASS · 14개 (순열·좌표 9, QR 주소/요청 제한 5) |
| Windows 시작 스크립트 | `Start-MOAGIL.ps1` · 임시 포트 8766 · `/api/health` | PASS · Python 3.12.14로 시작되고 `{"status":"ok","mode":"mock"}` 응답 확인 |
| Browser tool install | Node.js 24.22.1 / pnpm 11.25.0 · `pnpm install --frozen-lockfile` | PASS · Playwright 1.62.1 설치, lockfile 변경 없음 |
| Frontend syntax | `node --check web/app.js` | PASS |
| Demo runner syntax | `node --check demo/run-demo.cjs` | PASS |
| Browser E2E | Node.js 24.22.1 / Playwright 1.62.1 + Chrome, `pnpm test:e2e` | PASS · 별도 모바일 브라우저 컨텍스트에서 별명/핀 등록, 동기화, 좌표 검증, 초대 권한/정보 경계, idempotency, 동시 참가 5건 중 정확히 4건 수락, 5번째 거절, 409, 앱 재접속 후 정차 해제, 24→6 순열, 수동 순서, 안내 실패 시 하차 차단/재시도, 단계별 하차, 종료 확인/초대 폐쇄 |
| 시연 영상 | `node demo/run-demo.cjs --record` | PASS · `demo/video/MOAGIL-demo.webm`, QR/토큰 가림, 안내 실패·재시도, 종료 확인 장면 |
| Android 빌드 도구 | 공식 SHA-256을 확인한 JDK 17 / Gradle 8.7, Gradle Wrapper 생성 | PASS |
| Android dependency resolution | `:app:dependencies --configuration mockDebugCompileClasspath --no-daemon` | PASS · Compose/AndroidX/OkHttp/ZXing 버전 해석 성공 |

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
- 이 저장소는 PRD의 권장 FastAPI/SQLAlchemy 및 React/Vite 대신 Python 표준 라이브러리 HTTP/SQLite와 정적 HTML/CSS/JavaScript를 사용합니다. 키 없는 mock 흐름은 E2E로 검증했으며, 원래 기술 스택과 빈 환경 Android APK 빌드 조건은 충족하지 못했습니다.
- 브라우저 검증 런타임은 Node.js 24.22.1 / pnpm 11.25.0입니다. PRD에 명시된 Node.js 20 LTS 환경에서의 별도 재검증은 아직 하지 않았습니다.
- 영상은 승인된 브라우저 기반 시연에서 생성하며, 휴대폰 패널은 시연용 가상 브라우저입니다. 촬영 전 QR 토큰이 가려져 있는지 확인합니다.
