# 아키텍처

## 구성

```text
스마트폰 브라우저 ── 초대 토큰 / 참가자 토큰 / JSON ──┐
  /join/{sessionId}                      │
  참가자 진행 화면 · 목적지 수정          │
                                         ▼
Kotlin/Compose 또는 브라우저 대체 화면 ─ driver token ─ Python HTTP API ─ SQLite / Supabase PostgreSQL
  2초 폴링 / mock guide                                     ├─ sessions / passengers
  Pleos NaviHelper handoff (Pleos flavor)                   └─ Kakao Mobility route permutation engine
  local SVG map + QR (브라우저)
```

초기 PRD의 차량 화면 기술을 반영해 Kotlin/Compose `mockDebug` 앱을 추가했습니다. 로컬 서버는 Python 3.12 표준 라이브러리와 SQLite를 사용하고, Vercel에서는 정적 HTML/CSS/JavaScript와 Python 함수로 배포하며 Supabase PostgreSQL에 세션을 저장합니다. PostgreSQL 배포에서만 `psycopg` 드라이버를 사용합니다. PRD의 FastAPI/SQLAlchemy 및 React/Vite 스택은 채택하지 않았으며 구현 범위와 제한을 분리해 기록합니다.

Android 차량 앱은 `vehicle-app/app`에 있으며 OkHttp로 같은 자체 API를 호출하고 서버 version 검사를 적용합니다. 운전자 token은 Android Keystore AES-GCM 키로 암호화해 앱 개인 저장소에 보관합니다. `mockDebug`는 시연용 정차 스위치와 모의 내비를 씁니다. `pleosDebug`는 차량 화면에 별도 지도를 표시하지 않고 세로형 모아길 UI만 제공합니다. Pleos 기본 내비와 나란히 쓰려면 동승자 창 앱 라이브러리에서 모아길을 실행해야 하며 창 배치는 Pleos 시스템이 관리합니다. NaviHelper에 최적화된 순서대로 경유지 FIRST·SECOND와 목적지를 보내고, 현재 구간의 정차를 마치면 이어지는 구간을 다시 요청합니다. FusedLocation의 차량 위치를 세션 출발지로 동기화하고, AAOS `CarPropertyManager`에서 차량 속도와 기어를 1초 간격으로 읽어 속도가 거의 0이며 P 기어일 때만 운전자 조작을 허용합니다. 이 연동을 위한 FusedLocation, CAR_SPEED, CAR_POWERTRAIN 권한이 필요합니다. Android cleartext 트래픽 허용은 debug 변형에만 있습니다.

## 세션 및 권한

- 초대 및 운전자 토큰은 `secrets.token_urlsafe(32)`로 생성합니다. SQLite 또는 Supabase PostgreSQL에는 SHA-256 해시만 보관합니다.
- 초대 토큰은 `join-info`와 등록 요청 body로 보내며, URL에서 읽은 뒤 `history.replaceState`로 주소 표시줄에서 제거합니다.
- 등록 시 참가자 브라우저에만 참가자 토큰을 `sessionStorage`에 보관합니다. 서버는 등록 요청의 idempotency key 해시를 참가자 인증에도 사용하며, 참가자별 진행 조회와 목적지 수정은 `Authorization` 헤더로 인증합니다. API 응답에는 본인 정보만 포함합니다.
- 목적지는 `COLLECTING` 또는 `PLANNED` 상태에서 본인 참가자 토큰으로 수정할 수 있습니다. 수정하면 추천 경로를 지우고 다시 계산하도록 세션을 `COLLECTING` 상태로 되돌립니다.
- 동승자 세션은 SQLite 트랜잭션 또는 PostgreSQL advisory-lock 트랜잭션에서 4명 제한을 검사합니다. 재시도는 idempotency key와 payload 해시를 사용합니다.
- 차량 변경 요청은 세션 version을 확인합니다. stale version은 `409`와 새 스냅샷 재조회로 처리합니다.
- 서버 로그는 요청 경로를 출력하지 않아 초대 토큰/이름/좌표를 기록하지 않습니다.
- 초대 확인/참가 등록 요청은 IP 기준 분당 30회로 제한하고 429/`Retry-After`를 반환합니다. 단일 프로세스 데모용 메모리 카운터이며 다중 서버·인터넷 운영 방어로 간주하지 않습니다.
- Vercel 배포는 플랫폼 HTTPS를 사용합니다. Kakao JS 허용 도메인에는 배포별 미리보기 주소가 아니라 고정 프로젝트 `*.vercel.app` 주소를 등록합니다.

## 상태 흐름

```text
COLLECTING ── plan ──> PLANNED ── start ──> ACTIVE ── all stops ──> COMPLETED
     │                    │                    │
     └──── cancel ────────┴────── cancel ──────┴──> CANCELLED
```

세션은 2시간 뒤 만료됩니다. 모집 중 변경은 계획 snapshot을 지웁니다. 이동 중에는 동승자 수정/삭제를 막고, 현재 순서의 하차 완료만 처리합니다. 동승자 진행 화면은 5초 간격으로 본인 상태를 갱신합니다.

## 경로 계산

모든 순서는 Python `itertools.permutations`로 생성합니다. 우선 승객을 지정하면 첫 번째 순서로 고정합니다. Kakao Mobility 다중 출발지 API에서 실시간 시간·거리 행렬을 받아 순서를 비교하고, 다중 출발지 응답에서 누락된 구간은 Kakao 전체 길찾기 API로 보완합니다. 무료 도로를 고르면 행렬과 선택 경로 요청에 통행료 회피 옵션을 보내고, Kakao가 통행료 0원을 확인해 주지 않으면 경로 저장을 거부합니다. 선택 경로의 상세 형상과 구간 시간을 조회하고 route snapshot에 저장합니다. Kakao API 키가 없거나 요청이 실패하면 다른 경로 제공자로 전환하지 않고 오류를 반환합니다. Kakao Mobility에는 API 사용 한도와 초과 요금이 적용될 수 있습니다. Pleos NaviHelper에는 각 구간 요청마다 `FREE` 또는 `SHORT_TIME` 옵션을 전달합니다.

## 사용자 안전

Mock 변형은 앱 로컬 `정차(시연)` 토글에서만 조작을 허용합니다. Pleos 변형은 현재 차량 속도와 P 기어가 읽히지 않으면 조작을 잠급니다. 실제 차량에서 권한과 신호가 제공되는지는 Pleos 에뮬레이터 및 실차 환경에서 별도로 검증해야 합니다. 같은 LAN에서 서로 다른 기기 접근이 실패하면 네트워크 주소와 Windows 방화벽을 운영자가 확인해야 합니다. 자동 도착 감지는 없습니다.
