# 모아길 (MOAGIL)

여러 동승자가 각자의 하차 지점을 등록하면, 운전자가 가능한 하차 순서를 확인하고 단계별 안내를 이어가는 시연용 공동 이동 프로토타입입니다.

> 웹 참가 화면은 실제 지도 타일을 표시하고, 서버는 실제 도로망으로 하차 순서를 계산합니다. 기본 OSRM 샘플 측정에서 4명(24개 순서) 평가와 선택 경로 형상 조회까지 3.34초가 걸렸습니다. 네이버 Maps Client ID를 설정하면 웹과 Android에 네이버 지도를 표시하며, 네이버 Directions 5는 선택형 경로 비교입니다. Android `pleosDebug` 변형은 Pleos NaviHelper로 선택된 하차 지점 하나를 차량 내 내비에 전달합니다. 에뮬레이터에서 SDK 요청은 내비 앱을 열었지만 외부 네트워크가 오프라인이라 경로 완료는 확인하지 못했습니다. 차량 센서·실차 정차 판정·실차 주행 검증은 포함되지 않으므로 실차 운행에 사용하지 마세요.

## 빠른 실행

요구 환경: Python 3.12 이상, 현대 브라우저. 서버와 웹은 외부 패키지 없이 실행됩니다.

```powershell
.\Start-MOAGIL.ps1
```

또는 저장소 루트에서:

```powershell
python backend/server.py
```

차량 화면은 `http://localhost:8080`에서 열립니다. 같은 PC나 같은 LAN에서는 차량 화면의 QR을 스캔할 수 있습니다. 다른 Wi-Fi/모바일 데이터에서는 PC 서버를 공개 HTTPS 도메인이나 임시 터널에 연결해야 합니다. 임시 터널은 PC와 터널을 켜 둔 동안만 동작하며 주소가 바뀔 수 있습니다. 초대 링크를 가진 사람은 이동에 참가할 수 있으니 테스트용 좌표를 사용하고, 이동을 끝내면 세션을 닫으세요.

Android 에뮬레이터는 PC 서버에 `http://10.0.2.2:8080`으로 접근할 수 있습니다. QR 링크에는 실제 휴대폰이 접근할 수 있는 PC의 사설 IPv4 주소가 필요하므로, 서버 실행 전 PowerShell에서 다음처럼 설정하세요.

```powershell
$env:MOAGIL_PUBLIC_BASE_URL = 'http://192.168.0.10:8080' # ipconfig에서 확인한 PC 주소로 교체
.\Start-MOAGIL.ps1
```

이 설정은 QR의 공개 주소만 바꿉니다. 차량 앱의 API 서버 주소와 분리되어 있어 에뮬레이터 연결과 휴대폰 QR 참여를 함께 시험할 수 있습니다.

## 시연 순서

1. **시연 데이터로 시작**을 누르면 예시 출발지와 네 명의 테스트 승객이 들어갑니다. 실제 위치 데이터는 아닙니다.
2. **정차 상태 확인**을 눌러 `정차(시연)`으로 바꿉니다. 이 토글은 차량 센서가 아닙니다.
3. **가능한 순서 계산**을 누릅니다. 우선 승객이 없으면 4명 기준 24개, 한 명을 먼저 내리도록 지정하면 6개 순서를 비교합니다. 서버는 OSRM 거리표 한 번으로 순위를 계산하고, 선택한 순서의 실제 경로 형상을 조회합니다.
4. 위/아래 버튼으로 순서를 조정한 뒤 **경로 확정 · 모의 안내**를 누릅니다.
5. 각 단계에서 운전자가 **하차 완료 · 시연**을 누르고 마지막에 이동을 마칩니다.

실제 참가 흐름은 **빈 이동 만들기**로 새 세션을 만든 뒤, 인터넷에서 접속할 수 있는 QR을 스마트폰으로 스캔해 별명과 핀을 등록하면 됩니다. 참가 웹은 이름과 지정 좌표를 차량 화면에 표시합니다.

## 네이버 지도·경로 연결

네이버 지도와 Directions 5 연결 코드가 웹·Android 차량 앱·서버에 구현되어 있습니다. 네이버 계정 키는 저장소나 채팅에 올리지 말고 PC에만 보관하세요.

1. [네이버 클라우드 Maps 안내](https://guide.ncloud-docs.com/docs/maps-app)에 따라 **Menu → All Services → AI·NAVER API → Application → Application 등록**으로 이동해 `Maps`를 선택하고 **Web Dynamic Map**, **Mobile Dynamic Map**을 켭니다. 서버에서 네이버 길찾기로 순서를 비교해 보고 싶을 때만 **Directions 5**도 선택합니다.
2. 서비스 환경에 웹 주소 `http://localhost`, `http://127.0.0.1`과 사용할 공개 HTTPS 도메인의 호스트를 등록합니다. 포트와 경로는 적지 않습니다. Android mock 변형은 `kr.moagil.vehicle.mock`, Pleos 변형은 `kr.moagil.vehicle` 패키지로 등록합니다.
3. Application 등록 후 **인증 정보**에서 Client ID를 복사합니다. 저장소 루트 `.env.example`을 `.env`로 복사해 `MOAGIL_NAVER_CLIENT_ID`를 채웁니다. 서버가 `.env`를 자동으로 읽습니다. Naver Directions를 선택했다면 그때만 `MOAGIL_NAVER_CLIENT_SECRET`도 채웁니다. Secret은 브라우저나 APK에 넣지 않습니다.
4. `vehicle-app/local.properties`의 `naver.map.clientId=` 뒤에 같은 Client ID를 넣고 Android 앱을 다시 빌드합니다. `sdk.dir` 줄은 유지하세요. 이 PC에는 Android SDK 경로가 이미 준비되어 있습니다.
5. 서버를 재시작하고 `/api/config`에서 `mapProvider: naver`, `routingProvider: osrm`인지 확인한 다음 시연 이동을 새로 만듭니다. 브라우저 지도는 `.env`의 Client ID를, Android 지도는 `local.properties`의 Client ID를 사용합니다. 기본 순서 비교는 실제 OSRM 도로망을 사용합니다. Naver Directions 비교를 시험하려면 `.env`에서 `MOAGIL_ROUTING_PROVIDER=naver`를 명시하고 Directions 5와 Secret을 설정하세요.

네이버 지도 Client ID와 경로 비교 제공자는 서로 독립적으로 설정됩니다. 기본 OSRM은 실제 도로망을 사용하지만 실시간 교통은 반영하지 않습니다. Naver Directions는 선택형 대안이며 네 명일 때 한 번의 순서 계산으로 최대 24회 API 요청을 합니다. 요금과 무료 이용 조건은 계정 유형에 따라 달라질 수 있으니 콘솔에서 한도와 알림을 확인하세요. 실제 핀 좌표는 서버를 거쳐 선택한 경로 서비스로 전송됩니다. 시연에는 공개 테스트 좌표를 쓰세요. [Directions 5 API 문서](https://api.ncloud-docs.com/docs/ai-naver-mapsdirections-driving)

## 기능과 검증 범위

| 영역 | 현재 구현 |
|---|---|
| 차량 화면 | Kotlin/Compose `mockDebug` 앱과 브라우저 화면에서 실제 지도, 도로 경로, 경로 순서, 상태, 모의 내비 단계를 표시; `pleosDebug`는 선택된 하차 목적지를 Pleos 내비에 전달 |
| 동승자 웹 | QR 참여, 별명, 실제 지도 핀 선택/드래그, 좌표 입력, idempotent 등록 |
| 서버 | 세션·토큰 해시·4명 제한·SQLite 상태 저장·2초 폴링·version 충돌 처리 |
| 경로 순서 | 1~4명 모든 순열, 우선 승객 고정, 동률 정렬, Naver Directions 5 또는 OSM/OSRM 도로시간 기준 |
| 안전 표시 | 정차(시연) 확인 전 운전자 변경·계산·안내·하차 동작 차단 |
| 모의 안내 복구 | 성공/실패 재현, 결과 미확인 시 하차·종료 차단, 다시 요청과 종료 확인 |
| Naver Maps / Directions 5 | 웹·Android·서버 연동 구현. NCP 인증 정보 없이는 네이버 계정 인증과 실제 응답을 검증할 수 없음 |
| Pleos NaviHelper | 공식 SDK 빌드·설치·권한 및 내비 앱으로 요청 전달 확인. 외부 네트워크가 없는 에뮬레이터에서 경로 완료 콜백은 미확인; 상세 내용은 [`docs/validation.md`](docs/validation.md) 참고 |
| Android APK | `vehicle-app/`의 Compose `mockDebug`와 `pleosDebug` 앱 및 ABI별 APK. 파일·빌드 안내는 [`apk/README.md`](apk/README.md) 참고 |

### 모의 기능 안내

- Naver/OSM 지도와 실제 경로 형상을 표시하지만 안전한 정차 지점이나 차량 내비 안내를 보장하지 않습니다.
- 네이버 Directions 선택 시 실시간 교통을 반영합니다. OSM/OSRM 선택 시 실제 도로망을 쓰지만 실시간 교통은 포함되지 않습니다. Pleos 내비 안내는 계산된 순서를 평가하지 않고 현재 선택된 목적지만 전달합니다.
- `MOAGIL_ROUTING_PROVIDER=mock`이면 직선거리와 고정 시속으로 계산합니다.
- 차량 정차 상태와 위치는 시연값입니다. `mockDebug`는 모의 내비를 쓰고, `pleosDebug`는 NaviHelper에 목적지를 요청합니다. 실제 경로 완료 콜백은 아직 확인하지 못했습니다.
- Naver Client Secret은 서버 환경변수로만 사용하고, 외부 키나 좌표는 저장소에 넣지 마세요.

## 개발 및 확인

```powershell
# 순열 엔진 단위 테스트
python -m unittest discover -s backend/tests -v

# 브라우저 E2E 확인에 필요한 Playwright 도구
pnpm install

# 서버 실행 (별도 PowerShell 창)
python backend/server.py

# 별도 터미널에서 브라우저 흐름 확인
pnpm test:e2e

```

시연 영상은 아직 촬영하지 않았습니다. [`docs/DEMO.md`](docs/DEMO.md)는 추후 시연 흐름을 위한 스토리보드입니다.

## 저장 및 데이터

- 세션과 참가 정보는 `backend/data/moagil.db`에 저장됩니다. 이 폴더는 Git에서 제외됩니다.
- 세션 TTL은 2시간이며 세션 종료 후 참가 링크가 닫힙니다. 서버 재시작 뒤에도 SQLite 세션은 남습니다.
- `MOAGIL_DB` 환경 변수를 지정하면 데이터베이스 위치를 바꿀 수 있습니다.
- 초대 및 운전자 토큰은 서버 DB에 SHA-256 해시로만 저장하고 HTTP access log에는 URL을 기록하지 않습니다.
- 참가 초대 확인/등록 요청은 IP 기준 분당 30회로 제한하며, 초과 시 429와 `Retry-After`를 반환합니다. 단일 프로세스 시연 서버의 메모리 제한이라 배포용 방어 기능은 아닙니다.

## Android 앱과 APK

Android 차량 앱의 QR·서버 동기화·경로 편집·모의 내비와 Pleos NaviHelper 변형은 [`vehicle-app/`](vehicle-app/README.md)에 있습니다. 빌드된 APK 위치와 설치 검증은 [`apk/README.md`](apk/README.md)를 참고하세요. Pleos 실차 환경과 실제 주행은 검증되지 않았습니다.

## 자료

- [PRD 사본](docs/PRD.md)
- [구성 및 상태 모델](docs/architecture.md)
- [검증 기록](docs/validation.md)
- [시연 스토리보드](docs/DEMO.md)
- 시연 영상은 요청에 따라 아직 촬영하지 않았습니다.
