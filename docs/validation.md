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
| Android SDK | Android Studio SDK에 API 34 / Build Tools 34.0.0 설치 | PASS · 기존 SDK 라이선스 승인 기록을 사용해 Gradle이 필요한 패키지를 설치 |
| 새 GitHub clone 재현 | commit `c87b24b` 새 clone에서 Android Gradle 명령 | PASS · 41개 task 실행, 1개 단위 테스트 통과, APK 재생성. 이 PC에 설치된 Android SDK와 Gradle dependency cache 사용 |
| Android 단위 테스트 및 빌드 | `vehicle-app`: `.\gradlew.bat testMockDebugUnitTest assembleMockDebug --no-daemon` | PASS · Kotlin 컴파일 및 APK 생성, mock 내비 1개 단위 테스트 통과 |
| Pleos Connect 에뮬레이터 | ADB `emulator-5554` · 앱 설치/실행, 기본 주소 `10.0.2.2:8080` | PASS · 서버 연결, 시연 승객 4명 동기화, 24개 순서 계산, 안내 실패 차단·재시도, 4단계 하차, 종료 확인 및 서버 COMPLETED 상태. 치명 예외 없음 |
| APK 산출물 | `apk/MOAGIL-mock-debug.apk` · 24,347,967 bytes | PASS · 에뮬레이터 설치 및 실행 확인 |

## 환경상 미검증

| 기준 | 상태 | 이유 / 다음 확인 |
|---|---|---|
| 별도 휴대폰에서 QR 스캔 및 LAN 동기화 (AC02) | 미검증 | 실제 스마트폰이 연결되지 않았습니다. 같은 Wi-Fi에서 QR 스캔과 5초 이내 동기화를 확인해야 합니다. |
| API/browser 통합 (AC03/06/09/10) | PASS · 로컬 Chrome E2E | 모바일 등록/동기화, 인증, 중복 재시도, 4명 제한, version 충돌, 경로 순서/수동 조정, 단계별 mock 안내를 확인했습니다. 실제 휴대폰 재접속 및 LAN 동작은 포함하지 않습니다. |
| 완전히 빈 PC에서 Android 최초 설치/빌드 (AC01 일부) | 부분 확인 | GitHub 새 clone에서 빌드와 테스트는 통과했습니다. Android Studio SDK와 Gradle dependency cache가 없는 새 PC에서의 최초 다운로드까지 검증하지 않았습니다. |
| 네이버 Directions 5 / Pleos SDK 실제 연동 (P1) | BLOCKED / 미구현 | Pleos Connect 에뮬레이터에서 MOAGIL mock 앱은 실행했습니다. 네이버 인증 정보와 Pleos SDK 라이브러리/API 접근 권한이 없어 실제 SDK 초기화·목적지 요청·결과 콜백은 시험하지 않았습니다. |

## 범위 판단

- 브라우저 UI, SQLite 서버, 토큰 해시, idempotency 및 순열 계산은 P0 mock 경로로 구현하고 검증했습니다.
- Kotlin/Compose 차량 앱을 API 34로 빌드해 mockDebug APK를 만들고 사용자가 제공한 Pleos Connect 에뮬레이터에 설치·실행했습니다. 에뮬레이터에서 서버 연결부터 이동 종료까지 mock 흐름을 확인했습니다. 새 GitHub clone 빌드도 통과했습니다. 실제 휴대폰 AC02와 SDK가 없는 완전한 빈 PC에서의 최초 설치는 미검증이므로 PRD의 P0 전체 완료로 판정하지 않습니다.
- 이 저장소는 PRD의 권장 FastAPI/SQLAlchemy 및 React/Vite 대신 Python 표준 라이브러리 HTTP/SQLite와 정적 HTML/CSS/JavaScript를 사용합니다. 키 없는 mock 흐름은 E2E로 검증했으며, 원래 기술 스택과 빈 환경 Android APK 빌드 조건은 충족하지 못했습니다.
- 브라우저 검증 런타임은 Node.js 24.22.1 / pnpm 11.25.0입니다. PRD에 명시된 Node.js 20 LTS 환경에서의 별도 재검증은 아직 하지 않았습니다.
- 영상은 승인된 브라우저 기반 시연에서 생성하며, 휴대폰 패널은 시연용 가상 브라우저입니다. 촬영 전 QR 토큰이 가려져 있는지 확인합니다.
