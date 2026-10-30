# 검증 기록

마지막 갱신: 2026-10-30 (Asia/Seoul)

## 완료한 검사

| 구분 | 결과 |
|---|---|
| Python 서버 테스트 | PASS · 14개 테스트 |
| 브라우저 JavaScript 구문 검사 | PASS · 차량 화면, 지도 모듈, Naver 지도 모듈 |
| 브라우저 E2E · mock | PASS · 참가·중복·정원 제한, 24→6 순열, 수동 순서, 안내 재시도, 단계별 하차 및 종료 |
| 브라우저 E2E · OSRM | PASS · 실제 OpenStreetMap 도로망으로 후보 순서 계산 및 선택 경로 조회 |
| OSRM 4명 순서 계산 속도 | PASS · 공개 API 측정 3.10초; 서울 시연 좌표 24개 순서와 선택 경로 포함 |
| Naver Directions 어댑터 | PASS · 모의 응답으로 요청 좌표·시간·거리·경로 형상 처리; 실제 Naver 인증은 미검증 |
| Android 단위 테스트 및 APK | PASS · JDK 17, API 34; `testMockDebugUnitTest`, `assembleMockDebug`, `assemblePleosDebug` |
| Pleos NaviHelper 설치 및 권한 | PASS · `emulator-5554`, NaviHelper 3.0.5, `NAVI_ROUTE_SEARCH` granted |
| Pleos DNS | PASS · AVD를 `-dns-server 172.20.10.1` 옵션으로 headless 실행해 OSRM과 Pleos 도메인 해석 |
| Pleos 목적지 콜백 | PASS · `onRouteStarted` 이후 일치하는 `onDrivingInfo` 수신. `시연 지점 B`, 20,000m / 4,260초; UI 확인에 약 10초 |
| Pleos 경로 완료 콜백 | 미수신 · 이번 테스트에서는 `onRoutingCompleted`가 오지 않음. 목적지 일치 콜백으로 실제 내비 목적지 전달은 확인 |
| Pleos POI 상세 조회 | 제한 · Pleos 앱의 보조 `/search/poi-detail` 요청이 빈 `pid`로 HTTP 400. 경로 화면과 목적지 콜백은 정상 |

## 외부 계정 또는 기기가 필요한 검사

| 기준 | 상태 | 다음 단계 |
|---|---|---|
| 네이버 Web/Mobile Dynamic Map 실제 인증 | 미검증 | Naver Cloud Client ID를 `.env`와 `vehicle-app/local.properties`에 설정하고 브라우저·Android 지도 확인 |
| 네이버 Directions 5 실제 응답 | 미검증 | Client ID/Secret 설정 후 실제 요청 확인. Secret은 채팅이나 GitHub에 올리지 않음 |
| 스마트폰 QR의 외부 네트워크 접속 | 미검증 | PC 서버를 HTTPS 주소 또는 터널로 공개하고 휴대폰에서 등록 확인 |
| 실제 차량 센서 및 주행 | 미검증 | 테스트 범위 밖. 현재 정차 토글은 시연 입력이고 앱은 실차용이 아님 |
| Pleos POI 상세 오류 제거 | 미완료 | 비어 있지 않은 Pleos POI ID가 필요. 현재 사용하는 NaviHelper 문서에는 검색 API가 없어 앱에서 ID를 자동 조회하는 흐름이 없음 |

## Pleos 실행 기록

- 기본 Android 에뮬레이터 DNS `10.0.2.3`이 이름을 해석하지 못했습니다. 앱 코드나 서버 라우팅 오류가 아니라 AVD DNS 전달 문제였습니다. 에뮬레이터를 종료하고 `emulator.exe -avd Pleos_Connect_v2.1.0 -dns-server 172.20.10.1 -no-snapshot-load -no-window -no-audio`로 실행해 해결했습니다. 노트북 UI는 점유하지 않고 ADB로 제어했습니다. ([Android 공식 DNS 설정](https://developer.android.com/studio/run/emulator-networking-dns))
- 최신 APK에서 OSRM이 승객 4명의 24개 순서를 계산했습니다. 운전자는 선택 순서를 Pleos NaviHelper에 보냈고, Pleos 화면에 `시연 지점 B`, `20km`, `1시간 11분`이 표시됐습니다. 로그에는 `onRouteStarted` 뒤 같은 목적지를 담은 `onDrivingInfo`가 10초 안에 수신됐습니다. 따라서 앱은 이 콜백으로 목적지를 확인하고 하차 버튼 잠금을 해제합니다.
- Pleos 문서는 `onRouteStarted`를 경로 탐색 시작, `onRoutingCompleted`를 완료로 설명합니다. 이번 AVD 실행에서는 후자가 수신되지 않았으므로 앱은 이를 완료 이벤트라고 부르지 않습니다. 선택 목적지와 같은 이름·거리·시간이 들어 있는 내비 주행 정보 콜백을 별도의 확인 근거로 사용합니다. ([Pleos 목적지 요청](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/NaviHelper/destinations), [Pleos 이벤트 목록](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/NaviHelper/event-listeners))
- Pleos Navigation 앱이 보조 `/navi42-api/v2/search/poi-detail` 요청을 빈 `pid`로 보내 400을 기록했습니다. 경로 요청에 전달하는 좌표와 목적지명은 적용되지만, 정식 POI 상세 정보는 조회되지 않았습니다. Pleos 오류 문서도 좌표와 POI ID를 함께 확인하라고 합니다. ([Pleos 오류 처리](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/NaviHelper/error-handling))
- Pleos NaviHelper로 24개 후보 경로를 계산하지 않습니다. NaviHelper는 선택된 하차 목적지를 차량 내비에 전달하고, 서버는 OSRM 경로 거리표로 순서를 평가합니다. 공개 OSRM의 3.10초 측정은 한 번의 시연 좌표 결과이며 네트워크별 성능을 보장하지 않습니다.

## 빌드 및 확인 명령

```powershell
python -m unittest discover -s backend/tests -v
node --check web/app.js
cd vehicle-app
.\gradlew.bat testMockDebugUnitTest assembleMockDebug assemblePleosDebug
```

시연 영상은 촬영하지 않았습니다.
