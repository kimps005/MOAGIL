# 검증 기록

마지막 갱신: 2026-11-07 (Asia/Seoul)

## 2026-11-07 제출 준비 재점검

- 실행할 수 없는 OSRM·Naver 경로 분기와 관련된 의존 코드를 제거했습니다. 운영 서버의 실제 길찾기는 Kakao Mobility로 고정하고, 사용 중인 Kakao 처리와 Mock 테스트 흐름은 유지합니다.
- 제출용으로 서명된 Pleos ARM64 release APK를 만들었습니다. 크기 23,553,672 bytes, SHA-256 `AE063FCADCEE0AB7C6259020A65B7D3720CB109782B74A75843F1EF16B93697B`; APK v2 서명 확인, `debuggable=false`.
- 같은 인증서로 서명한 Pleos x86_64 release 변형을 별도 격리 에뮬레이터에 설치하고 앱 실행까지 확인했습니다. 실제 ARM64 차량 설치, 센서 권한 수신과 주행은 미검증입니다.
- 오래된 debug APK 4개를 삭제하고 `apk/`에는 제출용 ARM64 release 파일만 유지합니다. keystore와 DPAPI 암호 파일은 `.tools/signing/`에 보관하며 Git에는 추가하지 않습니다.
- 최종 정리 후 Python 서버 검사 39개와 `node demo/test-ui-flow.cjs`를 다시 실행해 모두 통과했습니다. signed release 빌드 스크립트도 Android Mock/Pleos 단위 검사 태스크와 Pleos release 빌드를 완료했습니다.
- YouTube 시연 영상 링크를 확보해 README와 제출 점검표에 추가했습니다. 접수양식·전원 동의서 작성 및 이메일 제출은 아직 완료된 것으로 확인되지 않았습니다.

공식 요건별 판정과 남은 단계는 [공모전 제출 점검](submission.md)에 정리했습니다. 이하 항목은 2026-11-05 및 그 이전 검증 이력입니다.

## 무료 도로 경로

- Kakao Mobility 공식 명세의 `avoid`는 다중 출발지와 다중 경유지 API 모두 문자열 배열입니다. 다중 경유지 요청만 단일 문자열로 보내던 오류를 수정했습니다. 같은 샘플 경로에서 기존 문자열 요청은 HTTP 500, 수정된 배열 요청은 성공과 통행료 0원을 반환했습니다. Kakao가 경로 요금 `0원`을 반환하지 않으면 무료 경로를 저장하지 않도록 했습니다.
- Kakao 실 API로 통행료 회피 행렬과 경유지 1곳을 포함한 경로를 조회했습니다. 행렬 구간은 모두 반환됐고, 상세 경로는 2,157m·통행료 0원이었습니다.
- Pleos 앱은 각 목적지 요청마다 `RouteOption.FREE`를 보내고 일반 시간 우선 선택은 `RouteOption.SHORT_TIME`을 보냅니다. Pleos 문서가 `FREE`를 무료 도로 우선으로 설명하므로, Pleos 내비가 실제 재탐색하는 모든 경우까지 완전한 무통행료를 보장한다고 단정할 수는 없습니다. 실제 차량 주행은 검증하지 않았습니다.
- Pleos에는 경유지 없이 목적지 하나씩 요청합니다. 각 하차 확인 후 다음 목적지를 현재 위치 기준으로 새로 요청하고, 최종 목적지도 별도 요청합니다.
- Python 회귀 검사 39개, Mock/Pleos Android 단위 검사, 양쪽 APK 빌드가 통과했습니다. 무료 우선·시간 우선 표현을 반영해 차량 앱 debug APK 4종을 갱신했습니다.

## Kakao-only 지도·경로 전환

- 웹과 Pleos 화면에서 OSM/Naver 지도 fallback을 제거했습니다. 서버 경로 제공자도 Kakao Mobility로 고정했으며, 설정값에 `osrm`, `naver`, `mock`이 들어 있어도 전환하지 않는 회귀 검사를 추가했습니다.
- Kakao 다중 출발지 응답에서 10km 검색 반경 밖의 구간이 빠질 때 전체 Kakao 길찾기 API로 누락 구간을 보완합니다. 서울–오이도 실제 경로와 4명 경로 24개 순서 비교를 Kakao API로 확인했습니다.
- 실제 Kakao Maps JavaScript SDK 요청은 HTTP 401 `domain mismatched`로 거절됩니다. 코드와 DNS는 정상이며, JavaScript 키의 Kakao Developers Web 도메인 허용 목록에 실행 origin을 추가해야 실제 지도 타일이 표시됩니다. 허용 도메인 변경은 계정 보안 설정이므로 사용자 승인 대기입니다.
- Pleos 앱 내 지도는 Kakao Maps이며, 선택된 차량 길안내 목적지는 현재 구조대로 Pleos NaviHelper에 전달됩니다.

## 차량 상태 연동 변경

- Pleos 변형에 FusedLocation 차량 위치와 AAOS `CarPropertyManager` 차량 속도·기어 조회를 연결하는 코드를 추가했습니다. 위치를 서버 세션 출발지로 반영하고, 속도 0.3m/s 미만 및 P 기어일 때만 운전자 조작을 허용합니다. Mock 변형의 시연용 정차 스위치는 유지합니다.
- Pleos APK를 빌드·설치했고 실행도 확인했습니다. 에뮬레이터에서 실제 위치·속도·기어 신호를 내는 테스트는 아직 하지 않았습니다.
- Pleos 권한 `FUSED_LOCATION`, `CAR_SPEED`, `CAR_POWERTRAIN`을 manifest에 추가했습니다. Playground에서 해당 권한 사용이 허용되지 않으면 위치·상태가 제공되지 않으며 Pleos 앱은 운전자 조작을 잠급니다.

## 완료한 검사

| 구분 | 결과 |
|---|---|
| Python 서버 테스트 | PASS · 28개 테스트. 로컬 SQLite 경로와 Supabase 연결 어댑터를 포함 |
| 브라우저 JavaScript 구문 검사 | PASS · Kakao 전용 화면·지도 모듈 |
| 차량 화면 UI 흐름 | PASS · 첫 화면 QR, 참가 후 지도·세션, 1명·2명 자동 재계산, 장소 검색·핀 이동·등록 |
| UI 마감 · 브라우저 | PASS · 데스크톱·모바일 화면 렌더링; 헤더 정렬, 중복 상태 문구, 참가자 좌표 표기 정리 |
| UI 마감 · Pleos | PASS · Mock 단위 검사, Mock/Pleos APK 빌드, Pleos x86_64 설치 및 앱 실행. 실제 Kakao SDK 도메인 거부로 지도 표시는 미검증 |
| 참가자 이름 및 수동 순서 | PASS · QR에서 이름 전송; Pleos 에뮬레이터에 수연·민수 등록, 지도 마커와 장소명 확인; 정차 상태에서 아래 화살표를 눌러 순서와 경로 변경 확인 |
| 브라우저 E2E · mock | PASS · 참가·중복·정원 제한, 24→6 순열, 수동 순서, 안내 재시도, 단계별 하차 및 종료 |
| 브라우저 E2E · Kakao Mobility | PASS · QR 참가, 장소 검색, 핀 이동, Kakao 실시간 경로, 24→6 순서, 수동 순서, 안내 재시도, 하차·종료, 동시 등록 정원 제한 |
| Kakao 4명 순서 계산 속도 | PASS · 이번 실 API 측정 18.87초; 24개 순서와 선택 경로 포함. 1회 측정이며 보장 시간은 아님 |
| Vercel 설정·Python 진입점 | PASS · 설정 JSON과 함수 import 확인. 실제 배포 URL 요청은 미검증 |
| Supabase 드라이버 연동 | PASS · 연결 옵션·트랜잭션 SQL 변환을 가짜 드라이버로 검사. 실제 Supabase 접속은 미검증 |
| Android 단위 테스트 및 APK | PASS · JDK 17, API 34; Mock/Pleos 단위 테스트 및 양쪽 APK 빌드 |
| Pleos 앱 QR 크기 | PASS · 초대 QR을 220dp로 키운 APK 설치 후 에뮬레이터 화면에서 확인 |
| Pleos 지도 조작 | PASS · 에뮬레이터 드래그와 확대·축소 버튼에서 중심·타일 변경 확인. 두 손가락 핀치 줌은 코드에 적용; 직접 멀티터치 입력은 미검증 |
| Pleos NaviHelper 설치 및 권한 | PASS · `emulator-5554`, NaviHelper 3.0.5, `NAVI_ROUTE_SEARCH` granted |
| Pleos DNS | PASS · AVD에 호스트 네트워크 DNS를 전달해 Kakao 도메인 해석 |
| Pleos 목적지 콜백 | PASS · QR로 등록한 `오이도역`이 지도·친구 A에 반영되고, `onRouteStarted` 뒤 일치하는 `onDrivingInfo` 수신. 43,000m / 2,520초; 약 1초 |
| Pleos 경로 완료 콜백 | 미수신 · 이번 테스트에서는 `onRoutingCompleted`가 오지 않음. 목적지 일치 콜백으로 실제 내비 목적지 전달은 확인 |
| Pleos POI 상세 조회 | 제한 · Pleos 앱의 보조 `/search/poi-detail` 요청이 빈 `pid`로 HTTP 400. 경로 화면과 목적지 콜백은 정상 |

## 외부 계정 또는 기기가 필요한 검사

| 기준 | 상태 | 다음 단계 |
|---|---|---|
| Kakao Maps JavaScript SDK 실제 지도 표시 | 대기 | Kakao Developers Web 허용 도메인에 `http://192.168.0.43:8080`, `http://10.0.2.2:8080`, PC 로컬 origin과 QR 공개 origin을 등록한 뒤 Android·브라우저에서 재확인 |
| 스마트폰 QR의 외부 네트워크 접속 | 부분 검증 | 임시 HTTPS QR 페이지가 200으로 열리고 등록 요청이 성공. 실제 휴대폰에서 카메라로 여는 과정은 미검증 |
| Vercel 고정 URL·실제 Supabase 저장 | 미배포 | Vercel 및 Supabase 프로젝트와 서버 환경 변수를 연결한 뒤 실제 URL에서 세션 생성·다른 휴대폰 참가를 확인 |
| Pleos 차량 위치·속도·기어 수신 | 미검증 | 이번 코드 변경의 Pleos APK 빌드와 AVD 신호 수신을 확인해야 함. 위치·속도 권한이 Playground에서 허용되어야 함 |
| 실제 차량 주행 | 미검증 | 실차 주행 시험은 하지 않음 |
| Pleos POI 상세 오류 제거 | 미완료 | 비어 있지 않은 Pleos POI ID가 필요. 현재 사용하는 NaviHelper 문서에는 검색 API가 없어 앱에서 ID를 자동 조회하는 흐름이 없음 |

## Pleos 실행 기록

- 기본 Android 에뮬레이터 DNS `10.0.2.3`이 이름을 해석하지 못했습니다. AVD에 현재 호스트 네트워크 DNS를 전달해 Kakao 도메인 해석은 해결했습니다. 이 설정과 별도로, Kakao SDK는 키의 허용 origin이 등록되지 않아 HTTP 401을 반환합니다. ([Android 공식 DNS 설정](https://developer.android.com/studio/run/emulator-networking-dns))
- 최신 APK는 실행되며 서버는 Kakao Mobility 경로로 4명 24개 순서를 계산했습니다. Pleos 실제 주행과 NaviHelper 콜백은 별도 시험 범위입니다.
- Pleos 문서는 `onRouteStarted`를 경로 탐색 시작, `onRoutingCompleted`를 완료로 설명합니다. 이번 AVD 실행에서는 후자가 수신되지 않았으므로 앱은 이를 완료 이벤트라고 부르지 않습니다. 선택 목적지와 같은 이름·거리·시간이 들어 있는 내비 주행 정보 콜백을 별도의 확인 근거로 사용합니다. ([Pleos 목적지 요청](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/NaviHelper/destinations), [Pleos 이벤트 목록](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/NaviHelper/event-listeners))
- Pleos Navigation 앱이 보조 `/navi42-api/v2/search/poi-detail` 요청을 빈 `pid`로 보내 400을 기록했습니다. 경로 요청에 전달하는 좌표와 목적지명은 적용되지만, 정식 POI 상세 정보는 조회되지 않았습니다. Pleos 오류 문서도 좌표와 POI ID를 함께 확인하라고 합니다. ([Pleos 오류 처리](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/NaviHelper/error-handling))
- Pleos NaviHelper로 후보 경로를 계산하지 않습니다. NaviHelper는 선택된 하차 목적지를 차량 내비에 전달하고, 서버는 Kakao Mobility 실시간 시간·거리 행렬로 순서를 평가합니다.

## 빌드 및 확인 명령

```powershell
python -m unittest discover -s backend/tests -v
node demo/test-ui-flow.cjs
node demo/verify-e2e.cjs
cd vehicle-app
.\gradlew.bat testMockDebugUnitTest assembleMockDebug testPleosDebugUnitTest assemblePleosDebug
```

시연 영상은 촬영하지 않았습니다.
