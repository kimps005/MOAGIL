# Android APK

아래 파일은 디버그 서명 APK입니다. x86_64는 Android Emulator용, arm64-v8a는 대부분의 Android 기기용입니다.

| 앱 변형 | APK | 크기 | SHA-256 |
|---|---|---:|---|
| Mock · x86_64 | [다운로드](MOAGIL-mock-debug-x86_64.apk) | 54,289,128 bytes | `B506C2181CD7AEBEC0AB0BBD095C5D1BD04030970EA5183ABFD3A061B3F2AD6B` |
| Mock · arm64-v8a | [다운로드](MOAGIL-mock-debug-arm64-v8a.apk) | 52,913,971 bytes | `A46DA838D2B5C719FB9BAFD049DDD4CC6112129872263DBA00E0FDAE5966CBC5` |
| Pleos NaviHelper · x86_64 | [다운로드](MOAGIL-pleos-debug-x86_64.apk) | 55,462,676 bytes | `F59F37CF93E28655D0F10D4C6B8A69E8693342799E7AE633C9CFD84D12B43D85` |
| Pleos NaviHelper · arm64-v8a | [다운로드](MOAGIL-pleos-debug-arm64-v8a.apk) | 54,087,519 bytes | `5817E00270D09B7CB2966A5BE6616BCE571195ABCE05180DB59BC0FB8349A80D` |

에뮬레이터에 Pleos 변형을 설치하려면 `adb`가 연결된 상태에서 저장소 루트에서 실행하세요.

```powershell
adb install -r apk/MOAGIL-pleos-debug-x86_64.apk
```

실제 스마트폰에는 보통 arm64 APK를 설치합니다. Pleos 변형은 선택된 하차 목적지를 NaviHelper에 보내고, Pleos Navigation의 목적지 정보 콜백이 같은 목적지를 반환하는지 확인합니다. Pleos 완료 이벤트는 테스트에서 오지 않았지만 `onDrivingInfo`가 목적지·거리·시간을 전달해 내비 적용을 확인했습니다. Pleos의 보조 POI 상세 요청은 빈 POI ID 때문에 HTTP 400을 반환합니다. 이 응답은 경로 화면과 목적지 확인에 영향을 주지 않았습니다. 실차 주행에 사용하지 마세요. Mock 및 Pleos 앱의 상세 빌드 정보는 [`../vehicle-app/README.md`](../vehicle-app/README.md), 테스트 기록은 [`../docs/validation.md`](../docs/validation.md)를 참고하세요.

Gradle ABI별 분할 빌드는 `vehicle-app/README.md`의 명령을 사용합니다. APK는 Gradle 기본 debug 서명키로 서명되며 release 배포용이 아닙니다.
