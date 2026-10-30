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
| OSRM 4명 순서 계산 속도 | PASS · 공개 OSRM을 사용한 샘플 1회: 거리표 1.10초, 24개 순서 점수 계산 0.000초, 선택 경로 형상 2.24초, 총 3.34초 |
| Android 단위 테스트·APK 빌드 | PASS · JDK 17, Android SDK API 34; `testMockDebugUnitTest`, `assembleMockDebug`, `assemblePleosDebug` 성공 |
| Pleos Connect 에뮬레이터 SDK 설치 | PASS · `emulator-5554`, Pleos NaviHelper 3.0.5 빌드·설치, `NAVI_ROUTE_SEARCH` 권한이 granted 상태 |

## 아직 확인되지 않은 실제 연동

| 기준 | 상태 | 남은 단계 |
|---|---|---|
| 네이버 Web/Mobile Dynamic Map 실제 인증 | 미검증 | Naver Cloud Maps Application Client ID를 `.env`와 `vehicle-app/local.properties`에 넣은 뒤 지도 타일을 확인해야 함 |
| 네이버 Directions 5 실제 응답·속도 | 미검증 | 서버 `.env`에 Client ID와 Client Secret을 설정하고 인증된 요청을 실행해야 함. Secret은 채팅이나 GitHub에 올리지 않음 |
| 실제 스마트폰 QR 참가 및 동기화 | 미검증 | 휴대전화에서 외부/가정 네트워크 주소로 초대 QR을 스캔하고 위치를 등록해야 함 |
| Pleos 내비 경로 완료 콜백 | BLOCKED · 이번 에뮬레이터에서 외부 Wi-Fi가 `noInternet`으로 표시됐고, 내비 앱이 “경로 탐색 오류 · 일시적으로 서비스를 이용할 수 없습니다”를 표시함. `requestRoute()`로 내비 앱이 열렸지만 NaviHelper의 완료/오류 콜백은 돌아오지 않아 앱에서 5초 제한시간 초과로 처리함. 실제 경로 안내 성공은 확인하지 못함. | 인터넷 연결이 검증된 Pleos 에뮬레이터에서 재시도. 좌표 핀에 빈 POI ID를 전달하는 방식도 실기기/API로 확인해야 함 |

## 범위와 제한

- 공모전 방향은 Pleos NaviHelper를 차량 내 경로 안내에 사용하고, 서버에서 OSRM 도로 거리표를 한 번 받아 24개 승객 순서를 계산하는 방식입니다. 현재 Pleos SDK 문서에서는 후보 경로 행렬·일괄 순위 API를 확인하지 못했습니다. NaviHelper에 후보 24개를 요청하면 내비 경로를 반복 변경하게 되므로 사용하지 않습니다.
- OSRM 속도 3.34초는 테스트 시점의 서울 샘플 좌표 한 번 측정한 값이며, 공개 데모 서버는 SLA나 실시간 교통을 보장하지 않습니다. 따라서 5초는 성능 목표이지 모든 네트워크에서 보장되는 상한이 아닙니다. NAVER Directions는 24개 경로를 요청하며 현재 코드에서 최대 2개씩 실행합니다. 실제 계정·네트워크 측정은 하지 않았습니다.
- Pleos 변형은 공식 `NaviHelper:3.0.5`와 `pleos.car.permission.NAVI_ROUTE_SEARCH`를 사용해 현재 하차 지점 하나씩 `requestRoute()`합니다. 완료 콜백 확인 전에 하차 완료를 허용하지 않으며, 5초 내 콜백이 없으면 재시도를 안내합니다. 현재 에뮬레이터에서는 내비 서비스가 오프라인이어서 경로 안내 성공을 확인하지 못했습니다.
- Android 에뮬레이터의 실제 기본 위치/정차 센서는 연결하지 않았습니다. 정차 스위치는 시연값입니다. 지도 Client ID가 없으면 Android 지도에 설정 안내를 표시합니다.
- 지도와 후보 경로는 키가 없을 때 OpenStreetMap과 OSRM을 사용합니다. OSRM은 공개 시연용이며 SLA와 실시간 교통 정보가 없습니다. NAVER Maps는 Client ID로 지도를 표시하며 Directions 5는 명시적으로 선택할 때만 후보 비용에 사용합니다.
- 앱은 실제 차량 주행용이 아닙니다. 회전별 음성 안내, 실제 차속·정차 판정, 스마트폰 QR 외부 네트워크 검증은 완료하지 않았습니다.
