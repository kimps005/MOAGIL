# Android APK

아래 파일은 디버그 서명 APK입니다. x86_64는 Android Emulator용, arm64-v8a는 대부분의 Android 기기용입니다.

| 앱 변형 | APK | 크기 | SHA-256 |
|---|---|---:|---|
| Mock · x86_64 | [다운로드](MOAGIL-mock-debug-x86_64.apk) | 54,287,340 bytes | `936314C62FB04413D464AA640E837B53832F1AA97608E6FB03CE0E161577F9D2` |
| Mock · arm64-v8a | [다운로드](MOAGIL-mock-debug-arm64-v8a.apk) | 52,912,183 bytes | `FBC050453F82EC5F4E03160610FD553F49B1040B6F96C36010C519ADD6CE5874` |
| Pleos NaviHelper · x86_64 | [다운로드](MOAGIL-pleos-debug-x86_64.apk) | 55,462,676 bytes | `0203A526236C9B362603BE493C119E618FFD83BCA2B81CD70641F3509AF2B1B1` |
| Pleos NaviHelper · arm64-v8a | [다운로드](MOAGIL-pleos-debug-arm64-v8a.apk) | 54,087,519 bytes | `D43BC3551E9E6EC1F08D601A04E5A0DD9B918A73256825CDECF739708CE5956C` |

에뮬레이터에 Pleos 변형을 설치하려면 `adb`가 연결된 상태에서 저장소 루트에서 실행하세요.

```powershell
adb install -r apk/MOAGIL-pleos-debug-x86_64.apk
```

실제 스마트폰에는 보통 arm64 APK를 설치합니다. Pleos 변형은 현재 선택한 하차 목적지 하나를 Pleos NaviHelper로 전달하고 5초 동안 완료/오류 콜백을 기다립니다. 에뮬레이터가 외부 인터넷에 연결되지 않은 상태에서는 내비 경로 완료를 검증할 수 없었습니다. 실제 주행에 사용하지 마세요. Mock 및 Pleos 앱의 상세 빌드 정보는 [`../vehicle-app/README.md`](../vehicle-app/README.md), 테스트 기록은 [`../docs/validation.md`](../docs/validation.md)를 참고하세요.

Gradle ABI별 분할 빌드는 `vehicle-app/README.md`의 명령을 사용합니다. APK는 Gradle 기본 debug 서명키로 서명되며 release 배포용이 아닙니다.
