# Android APK

**현재 파일은 공모전 제출용 release APK가 아닙니다.** 공모전은 실행 가능한 release 버전과 `팀명_앱이름_release.apk` 파일명을 요구합니다. 아래 APK는 2026-11-05 갱신본이며, 이후 Android 소스 변경은 포함하지 않습니다. 최신 소스로 release 빌드·서명·설치 검증을 마친 파일을 이 폴더에 추가해야 합니다. [제출 점검](../docs/submission.md)을 참고하세요.

아래 파일은 디버그 서명 APK입니다. x86_64는 Android Emulator용, arm64-v8a는 대부분의 Android 기기용입니다.

| 앱 변형 | APK | 크기 | SHA-256 |
|---|---|---:|---|
| Mock · x86_64 | [다운로드](MOAGIL-mock-debug-x86_64.apk) | 27,745,255 bytes | `7A8ED4ED83366FE44FEF136FCCEBFD03D35C22AE2404B41079670AD564AFA45C` |
| Mock · arm64-v8a | [다운로드](MOAGIL-mock-debug-arm64-v8a.apk) | 27,745,255 bytes | `7A8ED4ED83366FE44FEF136FCCEBFD03D35C22AE2404B41079670AD564AFA45C` |
| Pleos NaviHelper · x86_64 | [다운로드](MOAGIL-pleos-debug-x86_64.apk) | 31,298,987 bytes | `10FC85953206EEBB6B0B37AB0A0C499BF4231DFD33D219BC2D27DF417626F105` |
| Pleos NaviHelper · arm64-v8a | [다운로드](MOAGIL-pleos-debug-arm64-v8a.apk) | 31,298,987 bytes | `10FC85953206EEBB6B0B37AB0A0C499BF4231DFD33D219BC2D27DF417626F105` |

Mock과 Pleos는 별도 앱으로 설치되므로 둘 다 설치하면 아이콘도 두 개 표시됩니다. Pleos 에뮬레이터 테스트에는 Pleos x86_64 APK 하나만 설치하세요. 런처 이름은 **모아길 Mock**, **모아길 Pleos**로 구분됩니다.

에뮬레이터에 Pleos 변형을 설치하려면 `adb`가 연결된 상태에서 저장소 루트에서 실행하세요.

```powershell
adb install -r apk/MOAGIL-pleos-debug-x86_64.apk
```

일반 Android 기기에서 시연하려면 기기 CPU에 맞는 Mock 변형을 사용합니다. Pleos 변형은 Pleos/AAOS 서비스와 권한이 있는 환경이 필요하며, ARM64 파일이라는 이유만으로 일반 스마트폰에서 동작하는 것은 아닙니다. Pleos 변형은 선택된 하차 목적지를 NaviHelper에 보내고, Pleos Navigation의 목적지 정보 콜백이 같은 목적지를 반환하는지 확인합니다. Pleos 완료 이벤트는 이전 테스트에서 오지 않았지만 `onDrivingInfo`가 목적지·거리·시간을 전달해 내비 적용을 확인했습니다. Pleos의 보조 POI 상세 요청은 빈 POI ID 때문에 HTTP 400을 반환합니다. 이 응답은 경로 화면과 목적지 확인에 영향을 주지 않았습니다. 실차 주행에 사용하지 마세요. Mock 및 Pleos 앱의 상세 빌드 정보는 [`../vehicle-app/README.md`](../vehicle-app/README.md), 테스트 기록은 [`../docs/validation.md`](../docs/validation.md)를 참고하세요.

Gradle ABI별 분할 빌드는 `vehicle-app/README.md`의 명령을 사용합니다. APK는 Gradle 기본 debug 서명키로 서명되며 release 배포용이 아닙니다.
