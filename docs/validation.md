# 검증 기록

마지막 갱신: 2026-10-31 (Asia/Seoul)

## 차량 상태 연동 변경 (검증 대기)

- Pleos 변형에 FusedLocation 차량 위치와 AAOS `CarPropertyManager` 차량 속도·기어 조회를 연결하는 코드를 추가했습니다. 위치를 서버 세션 출발지로 반영하고, 속도 0.3m/s 미만 및 P 기어일 때만 운전자 조작을 허용합니다. Mock 변형의 시연용 정차 스위치는 유지합니다.
- 이 변경 후 Pleos APK는 아직 빌드·설치하지 않았습니다. 빌드는 Gradle distribution을 받으려 했으나 `services.gradle.org` DNS 연결을 사용할 수 없었고, 오프라인 Gradle 실행은 Windows native-platform 라이브러리 초기화에서 중단됐습니다. 에뮬레이터의 실제 위치·속도·기어 신호도 확인 전입니다.
- Pleos 권한 `FUSED_LOCATION`, `CAR_SPEED`, `CAR_POWERTRAIN`을 manifest에 추가했습니다. Playground에서 해당 권한 사용이 허용되지 않으면 위치·상태가 제공되지 않으며 Pleos 앱은 운전자 조작을 잠급니다.

## 완료한 검사

| 구분 | 결과 |
|---|---|
| Python 서버 테스트 | PASS · 23개 테스트 |
| 브라우저 JavaScript 구문 검사 | PASS · 차량 화면, 지도 모듈, Naver 지도 모듈 |
| 차량 화면 UI 흐름 | PASS · 첫 화면 QR, 참가 후 지도·세션, 1명·2명 자동 재계산, 장소 검색·핀 이동·등록 |
| UI 마감 · 브라우저 | PASS · 데스크톱·모바일 화면 렌더링; 헤더 정렬, 중복 상태 문구, 참가자 좌표 표기 정리 |
| UI 마감 · Pleos | PASS · Mock/Pleos 단위 검사 및 APK 빌드; Pleos x86_64 에뮬레이터 설치 후 지도·QR 화면과 지도 타일 표시 확인 |
| 참가자 별명·이모지 및 수동 순서 | PASS · QR에서 이모지 선택과 별명 전송; Pleos 에뮬레이터에 🐱 수연·🐼 민수 등록, 지도 마커와 장소명 확인; 정차 상태에서 아래 화살표를 눌러 순서와 경로 변경 확인 |
| 브라우저 E2E · mock | PASS · 참가·중복·정원 제한, 24→6 순열, 수동 순서, 안내 재시도, 단계별 하차 및 종료 |
| 브라우저 E2E · OSRM | PASS · 실제 도로 경로, 경로 타일 프록시, 24→6 후보 순서, 수동 순서, 단계별 하차 및 종료 |
| OSRM 4명 순서 계산 속도 | PASS · 공개 API 측정 3.11초; 24개 순서와 선택 경로 포함. 1회 측정이며 보장 시간은 아님 |
| Naver Directions 어댑터 | PASS · 모의 응답으로 요청 좌표·시간·거리·경로 형상 처리; 실제 Naver 인증은 미검증 |
| Android 단위 테스트 및 APK | PASS · JDK 17, API 34; Mock/Pleos 단위 테스트 및 양쪽 APK 빌드 |
| Pleos 앱 QR 크기 | PASS · 초대 QR을 220dp로 키운 APK 설치 후 에뮬레이터 화면에서 확인 |
| Pleos 지도 조작 | PASS · 에뮬레이터 드래그와 확대·축소 버튼에서 중심·타일 변경 확인. 두 손가락 핀치 줌은 코드에 적용; 직접 멀티터치 입력은 미검증 |
| Pleos NaviHelper 설치 및 권한 | PASS · `emulator-5554`, NaviHelper 3.0.5, `NAVI_ROUTE_SEARCH` granted |
| Pleos DNS | PASS · AVD를 `-dns-server 172.20.10.1` 옵션으로 headless 실행해 OSRM과 Pleos 도메인 해석 |
| Pleos 목적지 콜백 | PASS · QR로 등록한 `오이도역`이 지도·친구 A에 반영되고, `onRouteStarted` 뒤 일치하는 `onDrivingInfo` 수신. 43,000m / 2,520초; 약 1초 |
| Pleos 경로 완료 콜백 | 미수신 · 이번 테스트에서는 `onRoutingCompleted`가 오지 않음. 목적지 일치 콜백으로 실제 내비 목적지 전달은 확인 |
| Pleos POI 상세 조회 | 제한 · Pleos 앱의 보조 `/search/poi-detail` 요청이 빈 `pid`로 HTTP 400. 경로 화면과 목적지 콜백은 정상 |

## 외부 계정 또는 기기가 필요한 검사

| 기준 | 상태 | 다음 단계 |
|---|---|---|
| 네이버 Web/Mobile Dynamic Map 실제 인증 | 미검증 | Naver Cloud Client ID를 `.env`와 `vehicle-app/local.properties`에 설정하고 브라우저·Android 지도 확인 |
| 네이버 Directions 5 실제 응답 | 미검증 | Client ID/Secret 설정 후 실제 요청 확인. Secret은 채팅이나 GitHub에 올리지 않음 |
| 스마트폰 QR의 외부 네트워크 접속 | 부분 검증 | 임시 HTTPS QR 페이지가 200으로 열리고 등록 요청이 성공. 실제 휴대폰에서 카메라로 여는 과정은 미검증 |
| Pleos 차량 위치·속도·기어 수신 | 미검증 | 이번 코드 변경의 Pleos APK 빌드와 AVD 신호 수신을 확인해야 함. 위치·속도 권한이 Playground에서 허용되어야 함 |
| 실제 차량 주행 | 미검증 | 실차 주행 시험은 하지 않음 |
| Pleos POI 상세 오류 제거 | 미완료 | 비어 있지 않은 Pleos POI ID가 필요. 현재 사용하는 NaviHelper 문서에는 검색 API가 없어 앱에서 ID를 자동 조회하는 흐름이 없음 |

## Pleos 실행 기록

- 기본 Android 에뮬레이터 DNS `10.0.2.3`이 이름을 해석하지 못했습니다. 앱 코드나 서버 라우팅 오류가 아니라 AVD DNS 전달 문제였습니다. 에뮬레이터를 종료하고 `emulator.exe -avd Pleos_Connect_v2.1.0 -dns-server 172.20.10.1 -no-snapshot-load -no-window -no-audio`로 실행해 해결했습니다. 노트북 UI는 점유하지 않고 ADB로 제어했습니다. ([Android 공식 DNS 설정](https://developer.android.com/studio/run/emulator-networking-dns))
- 최신 APK에서 OSRM이 승객 4명의 24개 순서를 계산했습니다. 별도 에뮬레이터 검증에서는 QR 페이지로 `오이도역`을 등록해 지도와 `친구 A` 목록의 즉시 갱신을 확인했습니다. 정차 시뮬레이션을 켜고 안내를 시작하자 Pleos 화면에 해당 목적지와 `43km · 42분`이 표시되고, `onRouteStarted` 후 약 1초에 같은 목적지를 담은 `onDrivingInfo`가 수신됐습니다. 실제 차량 주행은 시험하지 않았습니다.
- Pleos 문서는 `onRouteStarted`를 경로 탐색 시작, `onRoutingCompleted`를 완료로 설명합니다. 이번 AVD 실행에서는 후자가 수신되지 않았으므로 앱은 이를 완료 이벤트라고 부르지 않습니다. 선택 목적지와 같은 이름·거리·시간이 들어 있는 내비 주행 정보 콜백을 별도의 확인 근거로 사용합니다. ([Pleos 목적지 요청](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/NaviHelper/destinations), [Pleos 이벤트 목록](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/NaviHelper/event-listeners))
- Pleos Navigation 앱이 보조 `/navi42-api/v2/search/poi-detail` 요청을 빈 `pid`로 보내 400을 기록했습니다. 경로 요청에 전달하는 좌표와 목적지명은 적용되지만, 정식 POI 상세 정보는 조회되지 않았습니다. Pleos 오류 문서도 좌표와 POI ID를 함께 확인하라고 합니다. ([Pleos 오류 처리](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/NaviHelper/error-handling))
- Pleos NaviHelper로 24개 후보 경로를 계산하지 않습니다. NaviHelper는 선택된 하차 목적지를 차량 내비에 전달하고, 서버는 OSRM 경로 거리표로 순서를 평가합니다. 공개 OSRM의 3.10초 측정은 한 번의 시연 좌표 결과이며 네트워크별 성능을 보장하지 않습니다.

## 빌드 및 확인 명령

```powershell
python -m unittest discover -s backend/tests -v
node demo/test-ui-flow.cjs
node demo/verify-e2e.cjs
cd vehicle-app
.\gradlew.bat testMockDebugUnitTest assembleMockDebug testPleosDebugUnitTest assemblePleosDebug
```

시연 영상은 촬영하지 않았습니다.
