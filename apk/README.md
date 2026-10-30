# Android APK

아래 파일은 디버그 서명 APK입니다. x86_64는 Android Emulator용, arm64-v8a는 대부분의 Android 기기용입니다.

| 앱 변형 | APK | 크기 | SHA-256 |
|---|---|---:|---|
| Mock · x86_64 | [다운로드](MOAGIL-mock-debug-x86_64.apk) | 54,288,524 bytes | `0388A4FFF786806FCE9FCB81D04E87B7CE86DB13664063F9F57C1BDE9490AA84` |
| Mock · arm64-v8a | [다운로드](MOAGIL-mock-debug-arm64-v8a.apk) | 52,913,367 bytes | `B6913351C60E12D2DA261495321A691F319D8A42FEC3BA3AE392356E505EE22F` |
| Pleos NaviHelper · x86_64 | [다운로드](MOAGIL-pleos-debug-x86_64.apk) | 55,462,676 bytes | `7AC31F060AF9FF9F7F65EE98A35419E160E3FCC65F0F38690CCB09D345479A30` |
| Pleos NaviHelper · arm64-v8a | [다운로드](MOAGIL-pleos-debug-arm64-v8a.apk) | 54,087,519 bytes | `2C5C7803005D73E648286CAD4B6D8FE2E74C86D97069CCBF7B766B7062D8B106` |

에뮬레이터에 Pleos 변형을 설치하려면 `adb`가 연결된 상태에서 저장소 루트에서 실행하세요.

```powershell
adb install -r apk/MOAGIL-pleos-debug-x86_64.apk
```

실제 스마트폰에는 보통 arm64 APK를 설치합니다. Pleos 변형은 현재 선택한 하차 목적지 하나를 Pleos NaviHelper로 전달하고 콜백까지 기다립니다. 시간 제한은 두지 않으며 진행 중 경과 시간과 콜백 시 소요 시간을 표시합니다. 에뮬레이터에서는 `CAUSE_SEARCH_ROUTE` 오류가 15.5초 후 돌아왔고 경로 성공은 아직 확인하지 못했습니다. 실제 주행에 사용하지 마세요. Mock 및 Pleos 앱의 상세 빌드 정보는 [`../vehicle-app/README.md`](../vehicle-app/README.md), 테스트 기록은 [`../docs/validation.md`](../docs/validation.md)를 참고하세요.

Gradle ABI별 분할 빌드는 `vehicle-app/README.md`의 명령을 사용합니다. APK는 Gradle 기본 debug 서명키로 서명되며 release 배포용이 아닙니다.
