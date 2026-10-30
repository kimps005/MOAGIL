# 아키텍처

## 구성

```text
스마트폰 브라우저 ── 초대 토큰 / JSON ──┐
  /join/{sessionId}                      │
                                         ▼
Kotlin/Compose 또는 브라우저 대체 화면 ─ driver token ─ Python stdlib HTTP server ─ SQLite
  2초 폴링 / mock guide                                     ├─ sessions / passengers
  Pleos NaviHelper handoff (Pleos flavor)                   └─ OSRM / 선택형 NAVER route permutation engine
  local SVG map + QR (브라우저)
```

초기 PRD의 차량 화면 기술을 반영해 Kotlin/Compose `mockDebug` 앱을 추가했습니다. 서버는 Python 3.12 표준 라이브러리와 SQLite, 참가 웹 및 브라우저 대체 차량 화면은 정적 HTML/CSS/JavaScript로 구성하여 서버 실행에 외부 런타임 패키지가 필요하지 않습니다. PRD의 FastAPI/SQLAlchemy 및 React/Vite 스택은 채택하지 않았으며 구현 범위와 제한을 분리해 기록합니다.

Android 차량 앱은 `vehicle-app/app`에 있으며 OkHttp로 같은 자체 API를 호출하고, Compose의 정차 게이트와 서버 version 검사를 함께 적용합니다. 운전자 token은 Android Keystore AES-GCM 키로 암호화해 앱 개인 저장소에 보관합니다. `mockDebug`의 `NavigationPort`는 mock 안내를 제공하고, `pleosDebug`는 공식 NaviHelper SDK를 이용해 선택한 하차 목적지 하나를 차량 내 내비에 요청합니다. Pleos 요청에는 SDK 콜백 대기 제한을 두지 않고 진행 경과 시간과 완료/오류 시간을 표시합니다. 콜백이 오기 전에는 하차 확인을 막습니다. 에뮬레이터에서는 `CAUSE_SEARCH_ROUTE` 오류 콜백이 15.5초 후 돌아왔으며 경로 성공은 아직 검증되지 않았습니다. `DrivingStatePort`와 정차 스위치는 두 변형 모두 시연 상태이며 차량 위치·차속 센서를 읽지 않습니다. Android cleartext 트래픽 허용은 debug 변형에만 있습니다. JDK 17과 SDK API 34에서 mock 단위 테스트 및 mock/Pleos APK 빌드가 성공했습니다.

## 세션 및 권한

- 초대 및 운전자 토큰은 `secrets.token_urlsafe(32)`로 생성합니다. SQLite에는 SHA-256 해시만 보관합니다.
- 참가자의 토큰은 `join-info`와 등록 요청 body로 보내며 URL에서 읽은 뒤 `history.replaceState`로 주소 표시줄에서 제거합니다.
- 동승자 세션은 원자적 SQLite 트랜잭션에서 4명 제한을 검사합니다. 재시도는 idempotency key와 payload 해시를 사용합니다.
- 차량 변경 요청은 세션 version을 확인합니다. stale version은 `409`와 새 스냅샷 재조회로 처리합니다.
- 서버 로그는 요청 경로를 출력하지 않아 초대 토큰/이름/좌표를 기록하지 않습니다.
- 초대 확인/참가 등록 요청은 IP 기준 분당 30회로 제한하고 429/`Retry-After`를 반환합니다. 단일 프로세스 데모용 메모리 카운터이며 다중 서버·인터넷 운영 방어로 간주하지 않습니다.
- HTTP는 격리된 시연 LAN에 한정합니다. 인터넷 운영을 위한 보안 인증·TLS·다중 서버는 포함되지 않습니다.

## 상태 흐름

```text
COLLECTING ── plan ──> PLANNED ── start ──> ACTIVE ── all stops ──> COMPLETED
     │                    │                    │
     └──── cancel ────────┴────── cancel ──────┴──> CANCELLED
```

세션은 2시간 뒤 만료됩니다. 모집 중 변경은 계획 snapshot을 지웁니다. 이동 중에는 동승자 수정/삭제를 막고, 현재 순서의 하차 완료만 처리합니다.

## 경로 계산

모든 순서는 Python `itertools.permutations`로 생성합니다. 우선 승객을 지정하면 첫 번째 순서로 고정합니다. 기본 OSRM provider는 도로 거리표를 받아 순서를 평가한 다음 선택 경로의 도로 형상을 조회합니다. 선택형 NAVER Directions 5 provider는 두 요청 워커로 후보 경로 비용을 조회합니다. Mock provider는 Haversine 직선거리에 1.25 보정치를 곱하고 고정 시속 30km/h로 시간을 추정합니다. 2026-10-30 샘플 세션의 24개 순서 전체 API 요청은 3.10초였습니다. 공개 서비스는 SLA/실시간 교통을 보장하지 않습니다. 통행료는 `null`입니다.

## 사용자 안전

운전자 동작은 앱 로컬 `정차(시연)` 토글이 켜진 상태에서만 활성화됩니다. 이는 실제 차량 상태를 읽지 않습니다. 같은 LAN에서 서로 다른 기기 접근이 실패하면 네트워크 주소와 Windows 방화벽을 운영자가 확인해야 합니다. 자동 도착 감지와 실제 내비 호출은 없습니다.
