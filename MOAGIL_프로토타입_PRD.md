# 모아길(MOAGIL) 프로토타입 PRD

**버전 1.0 · 작성 2026-10-29 · 대상: 새 개발 채팅의 자율 개발자 · 상태: 구현 명세, 구현 완료 보고서 아님**

> 새 채팅에 이 파일을 첨부하고 “이 PRD에 따라 모아길을 끝까지 개발해줘”라고 요청한다. 개발자는 아래의 P0 전체를 실행·검증하고, 가능한 범위에서 P1 실제 연동을 검증한다. 문서에 없는 SDK 메서드, 응답, 권한 또는 성공 결과를 추측해 만들지 않는다. 이 파일 작성만으로 저장소, APK, 서버 또는 시연 영상이 만들어진 것은 아니다.

## 0. 의사결정과 근거의 경계

**모아길은 여러 동승자가 QR로 모바일 웹에 접속해 각자의 하차 지점을 지정하고, 차량 화면에서 최적 하차 순서를 확인한 뒤 내비게이션으로 이어가는 공동 이동 서비스다.** 대상은 모임 후 귀가, 가족 이동 등 한 차량으로 여러 사람을 내려주는 일반 운전자다. 운전자의 최종 목적지도 전체 순서 계산에 포함한다.

기획의 확정 내용: 동승자 최대 4명, 앱 설치·회원가입 없는 QR 참여, 지도 핀, 최대 4! = 24가지 순서 비교, 특정 승객 먼저 하차 및 수동 순서 조정, 마지막 하차에서 종료 또는 운전자 최종 목적지, 내장 내비게이션 연동 목표. 출발 후 동승자에 의한 위치 수정 및 미래 교통 예측은 후속 기능이다. 운전자 입력 부담과 대화 감소는 **검증할 가설**이다.

**완료 기준을 두 층으로 나눈다.** P0는 키 없이 실행되는 Android 차량 화면 + 스마트폰 웹 + 서버, 실제 QR 연결/동기화/순열 계산을 포함하는 동작형 프로토타입이다. 교통, 위치, 지도 배경 및 내비가 모의이면 화면·영상·README에 명시한다. P1은 실제 교통 및 Pleos 연동을 공식 환경에서 개별 검증해야만 성공으로 표시한다. P0가 완성되어도 실시간 교통 기반 추천이나 실차 연동이 완성된 것은 아니다. P1에 필요한 계정·인증·장비를 확보하지 못하면 P0를 끝내고 정확한 차단 사유와 재현 절차를 남긴다.

근거: 사용자의 「현대자동차 공모전」 대화에서 접근 가능했던 메시지 전체(2026-10-29, 해당 대화 6턴, 더 오래된 페이지 없음), 「프로토타입 PRD 작성」, 「공모전 내용 정리」 및 관련 Codex 정리 대화. 해당 대화의 첨부 중 기대효과 양식 이미지를 직접 확인했고, 설명회 이미지 15장의 기존 정리 문서도 확인했다. 일부 과거 첨부 원본이나 그 대화의 누락 내용은 이 문서에서 확인했다고 가정하지 않는다. 공식 자료는 §18. 과거 대화의 구체적 SDK 호출 예시는 공식 문서로 재확인한 것만 채택한다. 실시간 교통·최적 경로는 외부 제공자 결과와 시각에 종속된다.

## 1. 목표, 범위, 성공 기준

| 단계 | 범위 | 완료 판정 |
|---|---|---|
| P0 필수 | Android 차량 앱, QR/링크, 별도 휴대폰 웹 핀 등록, 4명 제한 및 서버 동기화, 정차 상태에서 출발·최종 목적지 선택, 순열 계산·고정·수동 순서, 모의 내비 및 단계별 하차, 재접속, 시연 데이터·테스트·APK·README | §14의 P0 수용 기준 전부 통과. 모의임을 명시 |
| P1 조건부 | Directions 5 실제 현재 교통 경로 비교; 공식 NaviHelper 연결·콜백·가용 범위 확인; 실제 지도/검색과 차량 위치 연결 | 계정/자격/기기/문서 이용 가능 시만 구현하고 실제 응답과 환경 증거 기록. P0와 별도 결과 |
| P2 로드맵 | 이동 중 동승자 하차 지점 수정 제안 및 운전자 확인, 남은 경로 재계산 | §13 설계만. P0에 구현했다고 표시 금지 |
| 제외 | 미래 교통 AI 예측, 계정/결제, 자주 쓰는 주소, 자동 정차·운전자 주행 중 텍스트 입력, 실차 운행 시험, 영업·상용 서비스 | 만들지 않음 |

사용자에게 노출할 목표는 **“추천은 선택한 데이터와 제약 조건 안에서 총 예상 소요시간이 가장 짧은 순서”**다. 모의 데이터는 실제 도로, 거리, 교통, 법적 정차 가능 여부를 나타내지 않는다. 위치와 표시 지점의 차이, 내비게이션 자체 경로와 외부 추천 소요시간의 차이가 있을 수 있다.

## 2. 실행 환경과 정확한 기술 선택

개발 기준은 Windows 11, PowerShell, Git, JDK 17, Android Studio 및 Android SDK API 34, Python 3.12, Node.js 20 LTS 계열이다. 실환경 진입 전 도구 버전과 다운로드 가용성을 검증하고 호환성 문제는 대체 버전과 사유를 잠금 파일·README에 기록한다. 최소 Android API 28, 타깃/컴파일 API 34. 일반 Android 에뮬레이터에서 P0, 공식 Pleos Connect 에뮬레이터에서 P1을 확인한다.

| 구성 | P0 선택 | 책임 |
|---|---|---|
| 차량 앱 | Kotlin 1.9.24, Android Gradle Plugin 8.5.2, Gradle 8.7, Compose BOM 2024.06.00, Material 3, coroutines, OkHttp, Kotlin serialization, ZXing core | 차량 화면·QR·드라이버 토큰·화면 상태·모의 내비. 네트워크 I/O는 백그라운드 |
| 서버 | Python 3.12, FastAPI, Uvicorn, Pydantic v2, SQLAlchemy 2, SQLite, httpx | 세션·접근 제어·DB 트랜잭션·순서 산출·외부 API 프록시 |
| 동승자 웹 | React 18 + TypeScript + Vite, npm, SVG 기반 오프라인 모의 지도 | PIN·이름·하차 좌표 선택 및 제출. 빌드 결과는 서버에서 같은 출처로 제공 |
| 테스트 | pytest, httpx/ASGI 테스트, Vitest, Android JUnit + Compose UI test, 최소 하나의 Playwright 브라우저 흐름 | 로직, 권한, 모바일~서버~차량 연동 검증 |
| 지도 | 기본 자체 SVG 데모 지도: 실제 지리와 무관하다고 표시. 클릭·드래그 핀과 좌표 변환 구현 | 네트워크/키 없는 반복 시연. 실제 위치 검색·지도가 아니다 |

각 의존성의 정확한 패치 버전을 구현 시 lockfile(`package-lock.json`, Python 고정 requirements, Gradle wrapper 및 버전 카탈로그)에 고정한다. 버전 호환 확인 후 생성하고 lockfile을 커밋한다. 배포 가능한 제품 설계가 아니라 로컬 단일 인스턴스 시연이다.

## 3. 아키텍처와 저장소

```text
스마트폰 브라우저 ──HTTPS(또는 격리된 데모 LAN의 HTTP)──┐
  QR /join/{공개세션ID}?invite=초대토큰                    │
                                                           ▼
Android 차량 앱 ─── 드라이버 토큰 / JSON / 2초 폴링 ── FastAPI ─ SQLite
  Compose + 실제 QR                                       ├─ MockRouteProvider
  NavigationPort -> MockNavigation                       └─ NaverDirectionsProvider (P1)
                 -> PleosNavigation (P1 빌드 조건부)
```

서버는 정적 웹과 API를 한 출처로 제공한다. 기본 동기화는 2초 간격 폴링(백그라운드 중단, 지수 백오프 2→4→8→최대 15초, 복구 시 최신 스냅샷 교체)이다. WebSocket을 필수로 만들지 않는다. P0에서는 실제 별도 기기가 접근할 수 있는 동일 사설 네트워크에 서버를 띄운다. `localhost`, `127.0.0.1`, Android 에뮬레이터 전용 `10.0.2.2`를 QR에 넣지 않는다. Android에서도 동일 LAN 주소를 쓰거나 에뮬레이터에서 별도 API 주소를 지정한다. 방화벽에 막히면 원인을 표시하고 운영자가 로컬 환경을 설정한다.

```text
MOAGIL/
├── README.md                   # 실행·실제/모의 표·APK
├── .gitignore .env.example
├── vehicle-app/                # 독립 Gradle wrapper 및 app 모듈
│   └── app/src/{main,mock,pleos}/  # pleos는 실제 SDK 입수 후에만 유효
├── passenger-web/              # React/Vite, npm lock
├── backend/                    # app, tests, requirements.txt
├── docs/{PRD.md,architecture.md,validation.md,DEMO.md}
├── demo/{fixtures,screenshots}/
└── apk/README.md               # 실제 APK 생성 후 파일/Release 안내
```

`docs/PRD.md`는 본 문서 사본. 사용자 홈이나 기존 저장소를 임의 덮어쓰지 말고 작업 위치에 새 폴더를 생성한다. Git 초기화, `.gitignore`, 첫 커밋까지 진행한다. GitHub 저장소 생성·push는 현재 인증과 사용자의 권한/공개 선호가 확인되면 수행한다. 공개 범위가 불명확하면 기본은 비공개로 시도하고, 인증 또는 계정 선택이 막히면 로컬 완료 후 사용자에게 정확한 필요 정보와 명령을 전달한다. 비밀값, 토큰, `local.properties`, DB, 개인 주소, keystore, `.env` 및 빌드 산출물의 무분별한 커밋 금지. GitHub 작성이나 배포가 이 PRD를 실행하라는 사용자의 요청에 포함되어 있어도 인증 또는 서비스 권한을 만들어낼 수는 없다.

## 4. 화면과 사용자 여정

### 4.1 차량 화면

차량 화면은 가로형 1280×720 기준, 화면 크기 변형 대응, 큰 버튼·고대비·스크롤 가능한 한 화면 정보. 모든 메시지는 한국어. 예시 버튼 최소 48dp 이상; 실제 적용 시 최신 차량 UX 정책 확인. 색상만으로 상태를 표현하지 않는다.

1. **시작**: `새 이동 만들기`, `시연 데이터로 시작`(명시된 데모 전용), 연결 상태 및 `모의 위치 · 모의 교통 · 모의 내비` 배지. 데모 스위치는 mock 빌드에서만 제공.
2. **동승자 모집**: 서버에서 생성한 QR, 짧은 URL/세션 만료 시각, 0/4 목록. 승객 이름·선택 지점 라벨·접수 상태 표시. 운전자는 잘못 등록된 승객을 삭제할 수 있다. 세션 만료/연결 실패/가득 참을 명시. 초대 토큰 원문을 로그/스크린샷에 남기지 않는다.
3. **출발·최종 목적지**: P0는 `시연 출발지`(고정) 또는 오프라인 지도 좌표 선택. `마지막 하차 지점에서 종료`(기본) 또는 운전자 최종 목적지 선택. 앱은 최소 1명 등록 시 계산 가능. 실제 위치가 없는데 차량 실시간 위치라고 쓰지 않는다.
4. **순서 확인**: 이름과 하차 순서, 전체 시간·거리, 데이터 출처/조회 시각, 비교 순열 수, 경유별 ETA가 지원되지 않으면 `구간별 ETA 미제공`. `특정 승객 먼저` 옵션과 위/아래 수동 조정. 제약/입력 변경 시 기존 계산을 무효화. 계산 중/실패/재시도 명확히 표시. 추천 결과와 사용자가 수정한 순서를 구분하고 선택된 순서의 비용도 동일 provider로 계산한다.
5. **확정 및 안내**: 정차 확인이 유효할 때 `경로 확정` 후 첫 목적지에 안내 요청. 성공은 내비의 실제 성공 이벤트 또는 mock 성공 상태만 표시. `하차 완료`는 정차 확인 후 운전자가 누르고 다음 목적지 요청 전 다시 확인한다. 자동 도착 판정은 P0에 없다. 마지막 승객 이후 최종 목적지가 있으면 그곳을 안내하고 완료. 없으면 이동 종료. 현재 승객/남은 수/다음 목적지/상태 표시.
6. **완료/종료**: `이동 종료`는 확인 다이얼로그, 서버 종료 및 참여 링크 비활성화. 명시적인 `새 이동`만 생성한다.

### 4.2 승객 웹

1. 카메라 앱으로 QR 스캔 → 스마트폰 기본 브라우저의 참여 페이지. 로그인/설치 불필요. 세션 유효·인원 수 확인.
2. 별명 1~20자 입력, 자체 SVG 데모 지도에서 터치해 핀을 선택하거나 끌어 조정. 대체 좌표 입력도 지원하며 위도 -90~90, 경도 -180~180 검사. 좌표는 WGS84 `lat`/`lon`으로 저장. 사용자에게 `시연 지도 · 실제 지형 아님`, `지정 위치는 안전·합법적 정차 지점 보장 안 됨`을 표시. 실제 주소 검색 또는 주행 가능한 길은 지원하지 않는다고 명시.
3. `이 위치 등록` → 서버가 원자적으로 등록. 성공 화면에는 본인 별명과 위치만, `등록 완료 · 운전자 화면에서 확인` 표시. 중복 탭/재시도는 같은 idempotency key로 한 명만 등록. 등록 후 이동 중 변경 불가. 링크 만료·정원 초과·마감·네트워크 장애는 명확한 오류와 재시도 안내.
4. 다른 동승자 목적지, 운전자 토큰, 다른 동승자의 접속 권한을 표시하지 않는다. 참여 초대 링크는 등록 권한을 주므로 타인과 공유 금지 안내.

### 4.3 모의 지도 좌표 매핑

화면상의 고정 사각형은 예를 들어 경도 `[126.94,127.08]`, 위도 `[37.46,37.59]`의 가상 투영이다. SVG `viewBox="0 0 1000 1000"`, 좌상 `(0,0)`에서 `lon=126.94+x/1000*0.14`, `lat=37.59-y/1000*0.13`; 터치 좌표를 SVG 변환 행렬로 변환 후 0~1000으로 클램프한다. 확대나 실제 도로 정합을 제공하는 지도가 아니다. 시연 시 서울 일대처럼 보이는 위경도를 사용해도 지리적 정확성을 암시하지 않는다. WCAG 고려 키보드 포커스, 화살표 이동/좌표 입력과 텍스트 레이블을 제공한다.

## 5. 상태, 안전, 권한

서버 세션 상태는 `COLLECTING → PLANNED → ACTIVE → COMPLETED`, 언제든 허용된 상태에서 `CANCELLED` 또는 만료 `EXPIRED`. `PLANNED`는 계산 스냅샷 저장 성공 이후. 참가자 등록/삭제, 출발·최종 목적지·우선 승객 변경 시 `COLLECTING`으로 되돌리고 계획 무효화. `ACTIVE` 이후 승객의 신규 등록·수정·삭제는 409; 운전자 하차 완료만 진행. `COMPLETED/CANCELLED/EXPIRED`는 불변. 상태 변경은 서버에서 트랜잭션과 version 비교로 원자 처리한다.

**P0 안전 모델**: mock 빌드에 `정차 상태 시뮬레이션` 토글, 기본 OFF(불명). OFF/불명일 때 계획 확정, 순서 수동 편집, 목적지 변경, 하차 완료, 세션 종료 등 운전자 상호작용 차단. 명시적 `정차(시연)` 전환 후에만 사용 가능하며 모의라는 문구 유지. QR 표시와 읽기 전용 상태 조회는 가능. 이는 실제 안전 감지가 아니다. 실차 빌드에서 mock 정차 스위치로 안전 정책을 우회하지 않는다. 실제 상태를 공인 SDK/AAOS 및 정책으로 검증하기 전에는 `UNKNOWN`으로 차단하고 실차 주행 사용 금지. 플랫폼 안전 제약에 따라 UI가 추가 차단될 수 있다. 동승자 모바일 조작은 운전자가 하지 않는다는 전제이며 실제 운전 중 시연 촬영 금지.

오프라인 SVG 핀은 운전 가능 경로·합법적 하차 지점을 보증하지 않는다. 내비 요청은 운전자가 확인한 뒤 진행한다. 자동 설정이나 무조건 차량 제어를 성공한 것처럼 표시하지 않는다.

## 6. 데이터 모델과 API 계약

다음은 **모아길 자체 API**이며 Pleos SDK API가 아니다. JSON은 UTF-8, ISO-8601 UTC(`Z`), ID는 UUIDv4, 초대/드라이버 토큰은 32바이트 이상 암호학적 난수의 URL-safe 문자열. DB에는 토큰 해시만 저장한다. 차량 `Authorization: Bearer <driverToken>`; 초대 토큰은 참가 검증 요청 body에 전달. QR 링크의 invite는 초기 진입 후 `history.replaceState`로 주소에서 제거하고 메모리 보관, 링크와 쿼리 문자열 서버 액세스 로그·분석·외부 참조에 노출하지 않는다. 웹 응답에 `Referrer-Policy: no-referrer`, `Cache-Control: no-store`; 외부 스크립트/CDN 금지. HTTPS 권장, HTTP는 격리된 데모 LAN에 한정하고 사용자에게 노출 범위를 알린다.

DB 개념: `Session(id, state, version, createdAt, expiresAt, driverTokenHash, inviteTokenHash, startPoint, finalPoint?, priorityPassengerId?, selectedOrder?, activeIndex, routeSnapshot?)`; `Passenger(id, sessionId, displayName, point, createdAt, idempotencyKeyHash)`; `Point(lat, lon, label)`; `RouteSnapshot(order[], durationMs, distanceM?, tollKrw?, evaluatedCount, dataSource, fetchedAt, providerVersion, staleAt, basisVersion)`; 필요 시 idempotency 결과 테이블. 값은 소수 6자리까지 정규화, 라벨 80자 이하. session 기본 TTL 2시간; 닫히거나 만료되면 참여 불가. 정리 작업은 프로세스 시작 및 요청 시 만료 확인, 24시간 이후 삭제; 개인 정보 최소 수집, 로그에 이름·좌표·토큰·QR 전체 URL 금지. SQLite WAL, 외래키 ON, 트랜잭션 내 상태 및 인원 재검증. 동시 다섯 번째 요청은 반드시 거부한다. 단일 서버 인스턴스만 지원.

| 메서드·경로 | 권한 | 요청 / 결과 |
|---|---|---|
| `POST /api/sessions` | 없음, 로컬 데모 전용 | `{}` → 201 `{sessionId,driverToken,inviteToken,expiresAt,version}`. 원문 토큰은 이 응답에서만 반환; 차량에 안전하게 보관하고 invite는 QR 구성용 메모리에 저장 |
| `POST /api/sessions/{id}/join-info` | invite body | `{inviteToken}` → `{state,remaining,expiresAt}`. 다른 승객 정보 없음 |
| `POST /api/sessions/{id}/passengers` | invite body | `{inviteToken,displayName,point,idempotencyKey}` → 201 `{passengerId,displayName,point}`; 동일 키 동일 payload 재시도는 기존 결과, 다른 payload는 409 |
| `GET /api/sessions/{id}` | driver | 세션·승객·최신 계획의 전체 스냅샷, `version` 포함 |
| `DELETE /api/sessions/{id}/passengers/{passengerId}` | driver | `If-Match: <version>`; 모집 중 삭제, 200 스냅샷 |
| `PUT /api/sessions/{id}/settings` | driver | `If-Match`; `{startPoint,finalPoint:null|Point,priorityPassengerId:null|id}`; 참가자 검증 후 계획 무효화 |
| `POST /api/sessions/{id}/plan` | driver | `{expectedVersion}` → 계산 스냅샷 및 새 version. 계산 중 데이터 변경 시 409, 이전 결과 저장 금지 |
| `POST /api/sessions/{id}/select-order` | driver | `{expectedVersion,order:[승객ID...]}`; 정확한 순열·우선 제약 검증 후 동일 provider로 비용 평가, snapshot 갱신 |
| `POST /api/sessions/{id}/start` | driver | `{expectedVersion}` → PLANNED에서만 ACTIVE. 차량 정차 확인은 앱에서 시행; 서버는 물리적 정차 판별하지 않음 |
| `POST /api/sessions/{id}/complete-stop` | driver | `{expectedVersion,passengerId}` 현재 순서 승객만 완료; 마지막 후 최종 목적지가 있으면 final 단계 진입 |
| `POST /api/sessions/{id}/complete-trip` | driver | `{expectedVersion}` 최종 단계 또는 마지막 승객 완료 후 COMPLETED |
| `POST /api/sessions/{id}/cancel` | driver | `{expectedVersion}` → CANCELLED |
| `GET /api/health` | 없음 | `{status:"ok",mode:"mock"|"real"}`. 키와 개인정보 미포함 |

오류 형식은 `{error:{code,message,requestId}}`, HTTP 400/422 잘못된 입력, 401 토큰 오류, 404 세션 없음, 409 상태/정원/version/중복 충돌, 410 만료, 429 제한, 503 외부 제공자 불가. 토큰 비교는 constant-time, join은 IP·세션 기준 제한 예: 분당 30회, 실패 메시지는 인증 정보 상세 누출 금지. 수정 가능 요청은 모두 트랜잭션에서 `version` 확인·증가; 409 시 차량은 새 스냅샷을 받아 사용자에게 변경을 알린다. P0는 회원가입 없는 로컬 데모의 접근 제어이며 인터넷에 공개 운영할 보안 수준이라고 주장하지 않는다.

## 7. 경로 계산 명세: 순열과 교통

### 7.1 공통

승객 수 `n`은 1~4. 출발 `S`, 승객 `P`, 선택적 운전자 목적지 `F`. 모든 permutation을 생성하되 priority가 있으면 첫 요소로 고정하고 나머지만 순열화한다. 4명 무제약 24개, 선행 고정 6개. `n=1`은 1개; 0 또는 5명은 계산 거부. 모든 승객은 고유 ID로 구분한다. 동일 좌표는 서로 다른 승객으로 인정하지만 특정 제공자가 동일 지점을 거절할 수 있다. 계산 전에 입력 스냅샷·version을 고정한다.

`cost(order)= provider의 해당 순서 전체 경로 예상 시간(ms)`; F 없으면 마지막 승객이 goal, F 있으면 F가 goal. 거리·통행료는 provider가 반환하는 경우만 저장; 0과 알 수 없음(null)을 구분. 정렬: 시간 오름차순, 동률이면 거리(알 수 없는 거리는 뒤), 다시 승객 ID의 사전순. 비교 완료된 유효 후보 전체 가운데 고른다. 제공자 실패·시간 초과·유효하지 않은 좌표가 하나라도 있으면 **전체 비교 실패**로 처리하고 최적이라고 표시하지 않는다. 사용자가 재시도하거나 명시적으로 모의 모드로 다시 계산한다. 이전 snapshot을 조용히 재사용하거나 실제/모의 결과를 섞지 않는다. 데이터가 기준 시간 이후 5분 경과하면 `오래된 결과` 표시 및 확정 전 재계산 요구. 실제 교통 결과는 각 호출 시각이 다르므로 동일 시점 스냅샷으로 보장하지 않는다. 시점·불확실성을 표시하고 미래 교통 예측이라 부르지 않는다.

### 7.2 P0 결정적 모의 계산

`RouteProvider.evaluate(points: ordered list[Point]) -> RouteEstimate(durationMs, distanceM?, tollKrw?, source, fetchedAt)`는 앱 내부 계약. mock은 경로의 각 이웃 쌍에 대해 Haversine 직선거리 `d`(지구 반경 6,371,000m)를 구한다. `distanceM = round(sum(d)*1.25)`, `durationMs = round(distanceM / (30_000/3600) * 1000)`; 0m도 가능. `tollKrw=null`. 표시 문구 `시연용 직선거리 추정 · 실제 도로/교통 아님`. 시작과 최종 목적지 포함. Python의 반올림 규칙을 하나로 정하고 테스트에서 기대값을 고정한다. 실제 방향·통행 제한·신호·주행 가능성을 반영하지 않는다. 수동 순서도 같은 계산. 경로가 아니라 점을 연결한 선으로 표시할 때도 `시연용 직선`이라고 써야 한다.

테스트용 비용 행렬(분, 위경도와 별개의 순수 단위 테스트): 행/열 `S,A,B,C,D,F`; 비대각 원소는 아래와 같다. 순서 `A→B→C→D`는 1+1+1+1+1 = 5분, 유일한 최적. `B` 먼저 고정하면 최솟값은 22분으로 `B→A→C→D`와 `B→C→A→D`가 동률이다. 승객 ID를 각각 A/B/C/D로 지정한 테스트에서는 사전순 규칙에 따라 전자를 선택한다. 이 행렬은 mock Haversine 구현의 산출값이 아니라 순열 엔진에 주입하는 테스트 제공자다.

```text
    S  A  B  C  D  F
S   0  1  4  7  9  9
A   9  0  1  7  7  9
B   9  9  0  1  7  9
C   9  9  9  0  1  9
D   9  9  9  9  0  1
F   9  9  9  9  9  0
```

### 7.3 P1 네이버 Directions 5 실시간 교통

공식 [Directions 5](https://api.ncloud-docs.com/docs/en/application-maps-directions5)는 최댓값 5개의 경유지, `start/goal/waypoints` 경도·위도 순서, `trafast` 옵션, `summary.duration` 밀리초 및 거리 미터를 문서화한다. 각 순서에 대해 **전체 경로 1회** 요청: F가 있으면 goal=F, 경유지=모든 승객(4개 이하); F가 없으면 goal=마지막 승객, 경유지=앞의 승객들. 즉 최대 24개 호출. `goal`에 콜론으로 승객을 여러 명 넣으면 대안 목적지 선택이므로 금지. 도로가 아닌 좌표, 동일 시작·종료, 인증, 호출 한도, 요금에 대비한다.

호출을 순서대로 최대 동시 2개, 요청당 8초 타임아웃, 전체 120초, 최대 24 호출/계산, 429/5xx만 1회 지수 백오프 재시도(상한 별도 계수), 생성 시작 전 예상 호출 수와 비용 가능성을 표시한다. 인증키는 서버 환경 변수만. 공식 문서와 실제 응답을 바탕으로 endpoint·헤더·경유지 처리와 버전/약관을 구현·검증한다. 반환 경로가 입력 경유 순서대로 수행되는지 공식 자료와 샘플 응답으로 확인할 것. 순서 유지가 검증되지 않으면 전체 순서 비교의 실제 provider는 **차단**하고 기존 mock 유지; 무작정 다중 경유를 확정 구현으로 적지 않는다. 거리/시간/통행료는 동일 option과 동일 출발·종착으로 비교한다. 자동차 경로의 미래 교통 예측, 시간 절약량 실증, 교통 정확도 보장은 하지 않는다. 각 승객 ETA는 전체 요약만으로 계산하지 않는다.

실제 제공자 장애는 `ROUTE_UNAVAILABLE`과 원인 범주만 표시한다. 키가 없으면 P0 시작에 영향 없음. 사용자가 실시간 모드를 명시해 선택한 상태에서 실패하면 mock으로 자동 전환하지 않는다. 지도, 경로 서비스의 표시·저장·요금·사용 정책을 검토한다.

## 8. 차량 연동 포트와 실 SDK 검증

다음은 **우리 앱 인터페이스**다. SDK 인터페이스가 아니다.

```kotlin
interface NavigationPort {
    suspend fun navigateTo(point: AppPoint): NavigationResult
    suspend fun close()
}
sealed interface NavigationResult {
    data object Confirmed : NavigationResult
    data class Rejected(val reason: String) : NavigationResult
    data object Unavailable : NavigationResult
}
interface PositionPort { suspend fun currentPosition(): AppPoint? }
interface DrivingStatePort { suspend fun state(): DrivingState } // PARKED, MOVING, UNKNOWN
```

P0 `MockNavigationPort`: 사용자가 요청할 때 500ms 뒤 확정 또는 시연용 실패를 반환, 별도 `모의 내비 안내` 화면에 목적지 표시. 앱 배지와 성공 문구에 `모의` 포함. `PositionPort`는 설정된 데모 지점만 반환, `DrivingStatePort`는 mock 토글만. SDK가 없는 환경에서도 `mockDebug` 및 테스트가 빌드되어야 한다.

P1 `PleosNavigationPort`: 공식 [NaviHelper 소개](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro)와 [목적지](https://document.pleos.ai/api-reference/connect-sdk-pleos/NaviHelper/destinations), [경유지 요청](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/route-with-waypoints), [이벤트](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/NaviHelper/event-listeners)를 **구현 시 다시 확인**한다. v3.0.5 문서에는 `initialize`, `addListener`, `requestRoute`, `requestRouteWithWaypoints`, `removeListener`, `release`가 기재돼 있다. 각각의 정확한 패키지, dependency, 초기화 파라미터, 객체 생성자, 권한, 결과 콜백 의미는 설치된 버전에 맞게 원문으로 검증해 구현한다. 이 PRD는 임의 POI ID나 성공 이벤트를 만들어내지 않는다.

기본 연동 전략은 `requestRoute`로 확정된 첫 목적지 한 개 요청 → 공식 성공/실패 결과 확인 → 정차 상태에서 운전자가 하차 완료 확인 → 다음 목적지 요청. 마지막에는 운전자 F. 각 단계에서 실패·타임아웃·내비 앱 미설치·취소를 표시하고 운전자의 명시적 재시도를 허용한다. 이미 활성화된 내비를 덮어쓰는 효과는 문서·환경에서 확인하고 사용자의 확정을 받는다. 전체 다중 경유 요청은 SDK 문서에 존재하지만 최대 개수·임의 좌표·순서 보존·중간 하차 이벤트·기존 내비 동작은 검증 전 미확정; 검증 성공 시 대안으로만 추가. 요청을 보냈다는 사실과 길 안내가 시작됐다는 사실을 구분한다. 이벤트가 모호하면 `처리 확인 불가`로 둔다. 요청 단위 timeout 15초, 콜백 경합과 앱 종료 시 정리 및 중복 이벤트 방지.

실제 차량 위치: FusedLocation의 제공 상태·권한·정확도 확인 전은 미구현. 공식 프로젝트 가이드에서 FusedLocation은 제공 준비 상태로 설명되는 반면 다른 소개에서 모듈을 열거하는 등 페이지별 시점이 다를 수 있다. 현재 콘솔/문서가 우선이며 SDK 존재 = 사용 승인/구동 완료가 아니다. 필요하면 공식 NaviHelper의 현재 위치 조회도 검증하되 별도 위치 API를 추정하지 않는다. Vehicle SDK/AAOS를 통한 정차 상태도 데이터 정의·권한·동작을 실제 확인 전 UNKNOWN. 특정 퍼미션을 지어내지 않는다.

**검증 기록 표**: 기능, 문서 URL/버전/확인일, 개발자 계정의 접근 상태, 에뮬레이터 버전, 코드 위치, 입력 좌표/비개인 시나리오, 결과 이벤트, 실행 일시, PASS/FAIL/BLOCKED, 재현 단계. mock은 별도 PASS로 기록. SDK 접근 상태는 [프로젝트 생성과 접근 신청](https://document.pleos.ai/en/docs/pleos-only/create-my-project)과 실제 계정에서 확인한다. 권한 승인이 필요한 영역은 승인 없이 사용하지 않는다.

## 9. 초기 시연 데이터와 재현 절차

`demo/fixtures`에는 실제 집 주소가 아닌 예시 4명 A/B/C/D와 출발 S, 최종 F의 좌표만 둔다. 예: S `(37.525,127.000)`, A `(37.545,126.970)`, B `(37.550,127.030)`, C `(37.500,127.050)`, D `(37.480,126.980)`, F `(37.510,126.950)` (`lat,lon`). 결과 순서는 알고리즘으로 산출하며 미리 임의 숫자를 박지 않는다. `시연 데이터로 시작`은 기존 세션을 삭제하지 않고 새 세션을 만든다. 실제 QR 흐름을 증명할 때는 자동 채우기를 쓰지 않고 휴대폰에서 별명과 핀을 직접 등록한다. 가짜 서울 도로 그림에는 `시연 지도`를 표시한다.

1. 서버와 Android 앱을 실행하고 같은 네트워크의 휴대폰에서 참여 주소 접근 확인.
2. `새 이동` → 휴대폰이 QR 스캔 → A와 위치 등록 → 2초 폴링으로 차량에 반영. 다른 기기 또는 독립 브라우저로 B/C/D를 등록; 시연 시 한 대를 반복 사용해도 그 사실을 숨기지 않는다.
3. 출발지 및 F 선택 → 추천 24개 → B 먼저 고정 → 6개 → 수동 순서 변경 → `정차(시연)` → 확정.
4. `모의 내비 안내`에서 첫 목적지 확인 → `하차 완료` → 다음 목적지 → F → 완료. 어떤 부분이 모의인지 항상 보여준다.
5. 만료/오프라인/5번째 참가 거부를 따로 재현. 기본 시연의 결과는 DB 초기화 후 재현 가능해야 한다.

## 10. 네트워크, 개발환경, 설치와 실행

개발자가 실제로 프로젝트를 만든 후 README에 검증된 명령을 복사해 넣는다. 아래는 프로젝트가 **생성된 이후** 기준 명령이다. 처음부터 실행 가능한 기존 저장소가 있다는 뜻이 아니다.

```powershell
# 저장소 위치에서. 사전에 Python 3.12, Node 20, JDK 17, Android Studio 설치.
cd passenger-web
npm ci
npm run build
cd ..\backend
py -3.12 -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
Copy-Item .env.example .env
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

`.env.example`는 `APP_MODE=mock`, `PUBLIC_BASE_URL=http://<개발PC의_LAN_IP>:8000`, `DATABASE_URL=sqlite:///./moagil.db`, `ROUTE_PROVIDER=mock`, `SESSION_TTL_MINUTES=120`을 설명한다. 꺾쇠는 사람이 교체할 예시이며 배포용 기본값이 아니다. 서버는 유효한 `PUBLIC_BASE_URL`이 없거나 `localhost`이면 QR 생성을 거절하고 설정 방법을 안내한다. 노트북 LAN IPv4는 `ipconfig`에서 확인하고, 휴대폰도 같은 네트워크에 연결. Android 에뮬레이터에서의 서버 URL이 다르면 차량 앱의 디버그 설정에 별도 API base URL을 넣되 QR의 공개 URL과 분리한다. Android mock debug만 `networkSecurityConfig`로 사설 HTTP를 허용; release/실서비스에는 HTTPS 및 실제 신뢰 가능한 인증서가 필요하다. 개발용 HTTP가 출시 준비 상태라고 주장하지 않는다. HTTPS 사용 시 휴대폰과 Android가 인증서를 신뢰해야 한다.

```powershell
# 다른 PowerShell 창, 저장소 루트
cd vehicle-app
.\gradlew.bat testMockDebugUnitTest assembleMockDebug
adb devices
adb install -r .\app\build\outputs\apk\mock\debug\app-mock-debug.apk
```

Gradle variant와 산출물 경로는 실제 구현된 이름을 유지한다. Playwright를 포함한 테스트 명령도 README에 정확히 기재. Android Studio Device Manager에서 일반 Android 에뮬레이터를 실행한 후 설치한다. debug APK는 개발·시연용 서명이며 release라고 허위 명명하지 않는다. 서명된 release는 사용자 소유 키를 안전하게 설정한 뒤 생성하고 보관한다. 실제 공모전 이름·서명 규칙은 제출 직전 공식 안내 재확인.

**Pleos 에뮬레이터**: [공식 설치 지침](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)에 따라 Windows/Linux SDK Update Site를 등록하고 Android API 34 Pleos Connect 시스템 이미지, Automotive 디바이스, Cold boot, RAM 4GB를 설정한다. 문서의 기준은 에뮬레이터 2.1.0이고 현재 사용 가능한 버전 및 하드웨어 요구사항을 확인한다. 프로젝트 생성/SDK 권한/인증은 공식 절차를 따른다. P0 빌드가 독점 SDK 다운로드에 의존하지 않도록 source set 또는 별도 모듈을 격리하며 `pleos` 빌드는 실제 SDK 취득 후에만 활성화한다. 설치·라이선스·계정이 막혔다면 원인과 대안만 기록한다.

## 11. 오류와 복구 UX

| 상황 | 처리 |
|---|---|
| 서버가 꺼짐/동일망 아님 | 연결 실패·재연결; 마지막 정보는 `오래됨` 표시, 변경은 성공 확인 전 반영 금지 |
| QR 유출/정원 초과/만료 | 취소 및 새 세션 발급; 5번째 등록은 원자적으로 거부; 만료 안내 |
| 중복 전송/네트워크 끊김 | 동일 idempotency key 재시도로 중복 방지, 다른 payload 충돌 |
| 운전자와 승객의 동시 변경 | version 충돌 409, 최신 스냅샷 표시 후 재확인 |
| 등록 중 계획 변경 | 계획 결과 저장 시 version 재검증; 다르면 실패 후 재계산 |
| 좌표/경로 불가 | 어떤 목적지에 문제가 있는지 가능한 범위로 표시, 수정 후 다시 계산 |
| 외부 API 실패/한도 | 비교 전체 실패, 출처 표시된 별도 mock을 명시 선택 가능 |
| 내비 요청 실패/콜백 미확인 | 다음 단계로 자동 이동 금지, 오류·재시도·모의 전환 가능 여부 표시 |
| 앱 재시작 | driver token을 Android 앱 전용 저장소에 보관; 만료 전 재접속. 토큰 원문이 없으면 새 세션. 앱 데이터 삭제 시 복구 불가 |
| 서버 재시작 | SQLite에서 상태 복구, 만료 정리 후 동작. SQLite 파일 보존하지 않는 배포는 재시작 시 세션 소실 안내 |

## 12. 시각 디자인과 접근성

차량은 어두운 남색 배경, 흰 글자, 청록색 주요 버튼과 오렌지 경고를 사용한다. 왼쪽에 주요 작업, 오른쪽에 목록/요약을 두되 작은 화면에서는 세로 배치. 과도한 애니메이션, 긴 텍스트, 외부 지도의 실제성 암시는 배제한다. 스마트폰은 흰 배경의 이름 입력, 큰 지도, 하단 고정 `위치 등록` 버튼. 상태 배지와 시각 장애 대응 텍스트·의미 있는 콘텐츠 설명 및 키보드 접근성을 제공한다. QR의 실제 URL은 마스킹한 짧은 수동 접속 안내로 표시하되 참가 토큰을 화면 캡처나 로그에 의도치 않게 노출하는 위험을 설명한다. QR 자체에는 유효한 초대 토큰이 포함되므로 촬영 후 세션을 취소한다.

## 13. 동승자 수정 기능 로드맵 — P2, 미구현

출발 전 본인 수정도 P0에 포함하지 않는다. P2에서만: 참가자별 무작위 수정 토큰 발급, 다른 승객 정보 접근 불가; 본인 미하차 상태·세션 ACTIVE에서 새 위치를 **제안**. 제안은 현재 위치를 덮어쓰지 않고 버전과 함께 보관. 차량은 새 제안 알림을 읽기 전용으로 표시, 안전하게 정차한 운전자가 수락/거절. 수락 시 이미 하차한 승객을 제외하고 현재 확인된 출발 위치 및 남은 목적지에 대해 재계산한 후 새로운 계획을 먼저 미리보기; 운전자 확정 및 내비 연동 성공 시 적용. 동시에 제안이 바뀌거나 version이 달라지면 재계산. 권한 만료·토큰 분실·연결 복구·재전송·취소·중복·주소 보호·위치 보관기간·내비 실패 롤백 테스트 필요. 제안 거부/계산 실패/내비 실패 시 기존 확정 경로 보존. 주행 중 자동 재설정·자동 수락 없음. 대용량 예측 모델이나 미래 교통 예측은 별도 연구 과제다.

## 14. 테스트 및 수용 기준

| ID | 수용 기준 | 검증 |
|---|---|---|
| AC01 | 빈 환경에서 문서 명령으로 서버, 웹, Android mock debug 빌드 성공; 키 불필요 | 클린 체크아웃 실제 명령·버전 기록 |
| AC02 | 별도 휴대폰이 QR을 스캔해 이름·핀을 등록하고 차량 앱에 5초 이내 보임(정상 LAN) | 실제 기기 또는 별도 네트워크 클라이언트 E2E; 영상 증거 |
| AC03 | 4명까지 등록되고 동시 요청 중 5번째가 거부, 동일 키 재시도는 중복 없음 | 서버 병렬 통합 테스트 |
| AC04 | 1~4명 각각 n!, 4명 24개, 한 명 먼저 고정 6개, F 포함/미포함; 행렬 테스트 최적 A-B-C-D 5분 | 단위 테스트, 전체 후보 독립 브루트포스 검증 |
| AC05 | 다른 F, 비대칭 비용, 동점, 동일 좌표, 잘못된 순열, 등록/계획 충돌, 외부 일부 실패를 정확히 처리 | 단위/통합 테스트; 모든 후보 실패 시 성공 금지 |
| AC06 | 수동 순서와 추천 결과의 비용·출처가 일관되고 입력 변경/5분 경과 시 오래된 계획 확정 불가 | API/UI 테스트 |
| AC07 | UNKNOWN/MOVING에서 운전자 변경·확정·하차 완료 막힘. 모의 정차에서만 흐름 진행 | Android UI 테스트 |
| AC08 | 내비 mock은 요청/성공/실패 표시 및 다음 승객/F 완료를 지원; P1은 공식 성공 콜백을 검증한 경우만 성공 | mock 자동 테스트, P1 에뮬레이터 증거 |
| AC09 | 참여자는 타인의 상세·driver token을 볼 수 없음; 초대·driver 인증, 만료, 종료, 409 및 개인정보 로그 검증 | 서버 통합 테스트, 로그 검사 |
| AC10 | 서버·앱 재시작 후 유효 세션 재접속, 끊김과 재연결 상태 표시 | 수동 및 자동 통합 테스트 |
| AC11 | 목업·모의 지도/교통/위치/내비의 상태가 앱·영상·README에 일치 | 화면 및 산출물 점검 |
| AC12 | APK 설치·실행, README의 필수 4항목, 소스 및 APK 위치, 테스트 결과와 한계 기재 | 에뮬레이터 설치 및 깨끗한 체크아웃 재현 |

P1 성공 판정 추가: 실제 네이버 응답과 24개 순서/순서 유지/단위·조회 시각 확인, 실패 시 전체 계산 실패; Pleos 에뮬레이터에서 SDK 초기화·실제 목적지 요청·결과 콜백·다음 목적지 요청·자원 정리의 증거. PASS할 때만 실시간/실제 내비라고 말한다. P0 통과 여부와 P1 통과 여부를 절대 합산하지 않는다. 시연용 HTTP는 격리된 LAN에서만 확인한다.

## 15. 시연 영상과 발표

권장 2분 30초~3분(공식 시간 규정이라는 뜻 아님). 녹화 전 네트워크·휴대폰·에뮬레이터·주소·정차 표시를 확인하고 새 세션 생성. 화면에 모의 표시를 유지하고 촬영이 끝나면 QR 세션을 종료한다.

| 시간 | 장면 | 내레이션/검증 포인트 |
|---|---|---|
| 0:00–0:20 | 정차된 상황에서 여러 승객의 위치를 묻는 예시 | “여러 목적지를 운전자가 일일이 모으고 조율해야 합니다.” |
| 0:20–0:40 | 차량 화면에서 이동 생성·QR | “각자 QR로 참여합니다.” 데이터 출처 배지 노출 |
| 0:40–1:05 | 스마트폰 실제 스캔·핀 등록, 차량 목록 변화 | 실제 네트워크 전달 장면. 지도는 시연용임을 말함 |
| 1:05–1:30 | 4명과 최종 목적지, 24가지 추천 | “시연용 시간으로 24가지 순서를 비교합니다.” 실제 데이터일 때만 실시간이라 말함 |
| 1:30–1:50 | B 먼저 고정 6개 및 수동 조정 | 제약이 계산과 결과에 반영됨 |
| 1:50–2:20 | 정차 확인, 경로 확정, 모의 내비, 하차 완료 후 다음 | 모의/실제 연동의 표기를 유지. 끊김이나 미구현 기능은 편집으로 성공처럼 보이지 않음 |
| 2:20–2:40 | 마지막 목적지·완료·한계 | “운전자 목적지까지 고려합니다. 실제 도로·Pleos 연동은 검증 상태에 따라 표시합니다.” |

숏폼을 만들 경우 별도 30초~1분 세로 영상, 문제→QR→순서 추천→효과의 구성을 권장. 공모전 페이지에서 최신 해시태그·권리 요건을 재확인하고 공개 게시/제출은 사용자가 명시적으로 요청할 때 진행한다. 측정하지 않은 시간 절약·안전 개선 효과를 사실로 말하지 않는다.

## 16. 저장소/제출 체크리스트

- [ ] README에 앱명·한 줄 소개·실행법·시험 환경과 설정·APK 위치/릴리스 링크, 실제/모의 기능 표, 한계·테스트 결과·영상 링크 기재.
- [ ] GitHub에 소스와 실행 가능한 APK 함께 제공; 비공개면 공식 심사 계정 초대 및 별도 접근 확인. 링크와 파일명은 최신 규정 확인. 심사 계정의 쓰기 권한 범위를 확인.
- [ ] 서명된 공식 접수 양식·동의서, 팀원 CC, 공모전 메일 및 마감 전 접근 점검. 영상과 코드·APK는 1차 선택 가산점이며 2차 PT 대상자는 PT 전에 실제 동작 앱 완성 요구. [공식 FAQ](https://appdevchallenge.auto-hmg.io/faq).
- [ ] 현재 알려진 1차 접수 마감 2026-11-15, 1차 발표 11-23, PT 12-03~04, 최종 발표 12-18, 전시/시상 2027-02는 변동 가능; 제출 전에 [공식 사이트](https://appdevchallenge.auto-hmg.io/)에서 날짜·시각 재확인.
- [ ] debug APK와 release 서명 상태를 정확히 설명. 영상/지도/음원의 사용 권리 확인, 세션 토큰·실제 주소·API 키·개인정보 제거. PRD는 공모전의 공식 양식 자체가 아니다.

## 17. 새 채팅 개발자에게: 자율 작업 지시

1. 이 PRD 전체와 현재 작업 폴더·도구·기존 변경 사항을 확인한다. 이 문서의 명세와 실제 공모전·SDK 문서가 다르면 최신 공식 문서를 근거로 검증 기록에 차이를 적는다. 제품 행동은 user의 후속 지시가 우선이다.
2. 의존성/계정이 없어도 P0를 끝까지 만들 수 있게 `mock` 경로부터 구성한다. 서버 모델/API/토큰/순열, 웹 참가, Android QR·폴링·계획·모의 내비 순으로 연결한다. P0 요구사항을 순차 구현하고 코드·잠금 파일·README·검증기록·시연 자료를 만든다.
3. 테스트부터 필요한 수준으로 작성·실행한다. 실제 별도 휴대폰 없이 자동 테스트만 수행했다면 AC02를 미검증으로 남긴다. 임의 값으로 체크하지 않는다. 오류가 있으면 고치고 영향을 받은 검증을 재실행한다.
4. `docs/validation.md`에 명령, 실행일, 실제 결과, 오류, 차단 이유, P0/P1 구분을 적고 스크린샷은 기밀을 제거한다. 환경 부족으로 막힌 P1은 구체적 요구 사항·다음 명령을 기록하고 P0의 대체 흐름을 완성한다.
5. 네이버 계정/API 키나 Pleos SDK/에뮬레이터가 이미 제공되면 사용 범위를 공식 문서로 확인하고 실제 연동을 검증한다. 없는 인증정보를 만들거나 사용자에게 비용을 발생시키지 않는다. 외부 연결, 공개 업로드, 저장소 공개 여부는 사용자가 제공한 권한과 선호를 따른다.
6. `.gitignore`를 확인하고 Git 커밋한다. 인증된 GitHub가 있다면 권한과 공개 범위에 맞게 저장소를 생성/연결하고 푸시한다. 불가하면 로컬 저장소·APK·명령·차단 사유를 전달한다. 서명키는 요청 또는 기존 사용자 제공 상황에서만 사용한다.
7. 최종 답변에 실행 주소, APK 파일 경로, 테스트 결과, 실제/모의 표, 미완료 및 차단 사유, 재현 방법을 제공한다. `성공`은 실제 검사된 것만 적는다. 시연 영상 촬영은 환경이 허용되고 지시가 있을 때 수행하며, 그렇지 않으면 촬영 가능한 앱과 스토리보드를 완성한다.

## 18. 공식 출처와 검증 범위

2026-10-29 확인. 문서는 변할 수 있다. 링크는 구현 시작 시 재확인한다.

1. [현대자동차그룹 공모전 발표](https://www.hyundaimotorgroup.co.kr/ko/news/hyundai-motor-company-kia-vehicle-app-development-contest) 및 [공모전 FAQ](https://appdevchallenge.auto-hmg.io/faq): 목표, 참가·심사·제출, SDK 명단. 기획서·서명 동의서와 선택 개발 산출물의 구분.
2. [Pleos Connect SDK 개요](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/), [NaviHelper 소개](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro): SDK 구성과 목적지/경유지 연동 및 이벤트. 문서 기능 존재는 권한·실차·에뮬레이터 성공의 증거가 아니다.
3. [NaviHelper 목적지](https://document.pleos.ai/api-reference/connect-sdk-pleos/NaviHelper/destinations), [경유지 포함 요청](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/route-with-waypoints), [이벤트 리스너](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/NaviHelper/event-listeners): 문서화된 요청 및 결과 확인. 임의 좌표와 성공의 의미는 별도 확인.
4. [Pleos 에뮬레이터 설치](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator), [SDK 접근 신청](https://document.pleos.ai/en/docs/pleos-only/create-my-project): API 28 이상, Gradle 8 이상, API 34 에뮬레이터 및 접근 상태. 실제 상태는 계정과 시점에 종속.
5. [NAVER Cloud Directions 5](https://api.ncloud-docs.com/docs/en/application-maps-directions5): 실시간 교통 반영, 출발·목적지·최대 5 경유지, 거리/시간 단위, 제공자 에러. 실제 비용·한도·순서 보존·이용 허용 범위는 별도 확인.

**프로토타입은 효과를 제안하고 시연하는 도구다. 사용자 수, 주의 분산 감소, 이동시간 절약, 실제 차량 적용 및 사업성은 검증 전 가설이다.**
