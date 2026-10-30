# 검증 기록

마지막 갱신: 2026-10-30 (Asia/Seoul)

## 완료한 검사

| 구분 | 결과 |
|---|---|
| Python 단위 테스트 | PASS · 번들 Python 3.12.14, 14개 테스트 |
| 웹 JavaScript 구문 검사 | PASS · 차량 앱, Leaflet 지도, NAVER 지도 모듈 |
| 브라우저 E2E · mock | PASS · 모바일 참가, 권한/중복/동시 정원, 24→6 순열, 순서 조정, 안내 실패/재시도, 단계별 하차, 세션 종료 |
| 브라우저 E2E · OSRM | PASS · 실제 OpenStreetMap 도로망 기반 거리표와 선택 경로 요청 확인 |
| NAVER Directions 어댑터 | PASS · 모의 응답으로 좌표 순서, 경유지, 시간·거리·형상 파싱 및 24개 후보 평가 확인 |
| OSRM 4명 순서 계산 속도 | PASS · 2026-10-30 라이브 API 측정: 24개 순서 거리표 평가와 선택 경로 형상 조회까지 3.10초 |
| Android 단위 테스트·APK 빌드 | PASS · JDK 17, Android SDK API 34; `testMockDebugUnitTest`, `assembleMockDebug`, `assemblePleosDebug` 성공 |
| Pleos Connect 에뮬레이터 SDK 설치 | PASS · `emulator-5554`, Pleos NaviHelper 3.0.5 빌드·설치, `NAVI_ROUTE_SEARCH` 권한이 granted 상태 |
| Pleos 경로 요청 콜백 | PARTIAL · 5초 제한을 제거한 APK에서 요청 후 15.5초에 `onError(CAUSE_SEARCH_ROUTE)` 콜백 수신. 성공 경로 콜백은 아직 미확인 |
| Pleos 에뮬레이터 DNS | FAIL · 사용자 승인으로 Private DNS를 `off`로 바꾸고 Android 재부팅·Wi-Fi 재연결 후에도 `router.project-osrm.org` 및 Pleos 게이트웨이 `connect-gw-coral.umos.ai`가 `unknown host`; `1.1.1.1` IP ping은 성공하며 노트북에서는 OSRM 도메인이 조회됨 |

## 아직 확인되지 않은 실제 연동

| 기준 | 상태 | 남은 단계 |
|---|---|---|
| 네이버 Web/Mobile Dynamic Map 실제 인증 | 미검증 | Naver Cloud Maps Application Client ID를 `.env`와 `vehicle-app/local.properties`에 넣은 뒤 지도 타일을 확인해야 함 |
| 네이버 Directions 5 실제 응답·속도 | 미검증 | 서버 `.env`에 Client ID와 Client Secret을 설정하고 인증된 요청을 실행해야 함. Secret은 채팅이나 GitHub에 올리지 않음 |
| 실제 스마트폰 QR 참가 및 동기화 | 미검증 | 휴대전화에서 외부/가정 네트워크 주소로 초대 QR을 스캔하고 위치를 등록해야 함 |
| Pleos 실제 경로 안내 성공 | 미검증 · 앱이 경로 요청 오류 콜백을 받음 | Private DNS를 끄고 에뮬레이터를 재부팅·Wi-Fi 재연결한 뒤에도 Pleos 게이트웨이 `connect-gw-coral.umos.ai`와 공개 도메인 DNS 조회가 실패했습니다. IP 연결은 되지만 에뮬레이터의 DNS 전달이 동작하지 않는 상태이며 노트북 DNS는 정상입니다. 에뮬레이터 DNS가 복구된 뒤 재시험해야 합니다. 앱 요청은 좌표와 함께 빈 `poiId`/`poiSubId`를 전달합니다. Pleos 공식 오류 안내는 좌표·POI ID 유효성을 확인하도록 하므로, 좌표 전용 요청 지원 여부와 실제 POI ID 필요 여부도 검증해야 합니다 |

## 범위와 제한

- 공모전 방향은 Pleos NaviHelper를 차량 내 경로 안내에 사용하고, 서버에서 OSRM 도로 거리표를 한 번 받아 24개 승객 순서를 계산하는 방식입니다. 현재 Pleos SDK 문서에서는 후보 경로 행렬·일괄 순위 API를 확인하지 못했습니다. NaviHelper에 후보 24개를 요청하면 내비 경로를 반복 변경하게 되므로 사용하지 않습니다.
- OSRM 속도 3.10초는 테스트 시점의 서울 샘플 좌표 한 번 측정한 값이며, 공개 데모 서버는 SLA나 실시간 교통을 보장하지 않습니다. 따라서 5초는 성능 목표이지 모든 네트워크에서 보장되는 상한이 아닙니다. NAVER Directions는 24개 경로를 요청하며 현재 코드에서 최대 2개씩 실행합니다. 실제 계정·네트워크 측정은 하지 않았습니다.
- Pleos 변형은 공식 `NaviHelper:3.0.5`와 `pleos.car.permission.NAVI_ROUTE_SEARCH`를 사용해 현재 하차 지점 하나씩 `requestRoute()`합니다. 요청 제한 시간은 두지 않고, 대기 중 경과 시간과 완료/오류 콜백의 소요 시간을 표시합니다. 오류가 반환되면 하차 확인을 막고 다시 요청할 수 있습니다. 에뮬레이터에서 실제 경로 완료는 확인하지 못했습니다.
- 에뮬레이터에서 `1.1.1.1` IP 연결은 되지만 `router.project-osrm.org`, `www.google.com`, `connectivitycheck.gstatic.com`, Pleos 게이트웨이 `connect-gw-coral.umos.ai` DNS 조회가 실패합니다. 사용자 승인으로 Private DNS를 `off`로 설정하고 에뮬레이터 재부팅 및 Wi-Fi 재연결을 해도 오류가 남았으며, 노트북에서는 OSRM 도메인이 조회됩니다. Pleos 공식 문서는 `CAUSE_SEARCH_ROUTE`를 경로 검색 서버 오류로 설명하며 좌표·POI ID와 Navigation 앱 상태를 확인하도록 안내합니다. 앱은 빈 `poiId`/`poiSubId`를 전송하고 있어 DNS 복구 뒤 이 payload도 추가 확인이 필요합니다 ([Pleos 오류 처리](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/NaviHelper/error-handling), [목적지 요청 예시](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/NaviHelper/destinations)).
- Android 에뮬레이터의 실제 기본 위치/정차 센서는 연결하지 않았습니다. 정차 스위치는 시연값입니다. 지도 Client ID가 없으면 Android 지도에 설정 안내를 표시합니다.
- 지도와 후보 경로는 키가 없을 때 OpenStreetMap과 OSRM을 사용합니다. OSRM은 공개 시연용이며 SLA와 실시간 교통 정보가 없습니다. NAVER Maps는 Client ID로 지도를 표시하며 Directions 5는 명시적으로 선택할 때만 후보 비용에 사용합니다.
- 앱은 실제 차량 주행용이 아닙니다. 회전별 음성 안내, 실제 차속·정차 판정, 스마트폰 QR 외부 네트워크 검증은 완료하지 않았습니다.
