# Android APK

아래 파일은 디버그 서명 APK입니다. x86_64는 Android Emulator용, arm64-v8a는 대부분의 Android 기기용입니다.

| 앱 변형 | APK | 크기 | SHA-256 |
|---|---|---:|---|
| Mock · x86_64 | [다운로드](MOAGIL-mock-debug-x86_64.apk) | 62,453,892 bytes | `50A60C13EB5577EAA2206FDE500A1C020E8751BB61A4F32BE8BE5A9BA008512F` |
| Mock · arm64-v8a | [다운로드](MOAGIL-mock-debug-arm64-v8a.apk) | 61,078,735 bytes | `27CE5237841210F1A2C7256F265D2F17231BAE8CE004AB83A0AEC257AEECA8FB` |
| Pleos NaviHelper · x86_64 | [다운로드](MOAGIL-pleos-debug-x86_64.apk) | 64,562,660 bytes | `AF597B5F8BAB4EBC5AC1AB04409A9989DE5F5D79A0620BBD2F0800723CC44B65` |
| Pleos NaviHelper · arm64-v8a | [다운로드](MOAGIL-pleos-debug-arm64-v8a.apk) | 63,187,503 bytes | `67DB4582860D52680148353A83E7E3C0F06E21030196DA8C4082CE118A2D5C4F` |

Mock과 Pleos는 별도 앱으로 설치되므로 둘 다 설치하면 아이콘도 두 개 표시됩니다. Pleos 에뮬레이터 테스트에는 Pleos x86_64 APK 하나만 설치하세요. 런처 이름은 **모아길 Mock**, **모아길 Pleos**로 구분됩니다.

에뮬레이터에 Pleos 변형을 설치하려면 `adb`가 연결된 상태에서 저장소 루트에서 실행하세요.

```powershell
adb install -r apk/MOAGIL-pleos-debug-x86_64.apk
```

실제 스마트폰에는 보통 arm64 APK를 설치합니다. Pleos 변형은 선택된 하차 목적지를 NaviHelper에 보내고, Pleos Navigation의 목적지 정보 콜백이 같은 목적지를 반환하는지 확인합니다. Pleos 완료 이벤트는 테스트에서 오지 않았지만 `onDrivingInfo`가 목적지·거리·시간을 전달해 내비 적용을 확인했습니다. Pleos의 보조 POI 상세 요청은 빈 POI ID 때문에 HTTP 400을 반환합니다. 이 응답은 경로 화면과 목적지 확인에 영향을 주지 않았습니다. 실차 주행에 사용하지 마세요. Mock 및 Pleos 앱의 상세 빌드 정보는 [`../vehicle-app/README.md`](../vehicle-app/README.md), 테스트 기록은 [`../docs/validation.md`](../docs/validation.md)를 참고하세요.

Gradle ABI별 분할 빌드는 `vehicle-app/README.md`의 명령을 사용합니다. APK는 Gradle 기본 debug 서명키로 서명되며 release 배포용이 아닙니다.
