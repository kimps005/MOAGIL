# MOAGIL 제출 APK

| 변형 | 대상 | 파일 | 크기 | SHA-256 |
|---|---|---|---:|---|
| Pleos release · v1.0.0 | ARM64 | [최민수_모아길_release.apk](최민수_모아길_release.apk) | 23,553,672 bytes | `AE063FCADCEE0AB7C6259020A65B7D3720CB109782B74A75843F1EF16B93697B` |

패키지 ID는 `kr.moagil.vehicle`이며 Android API 28 이상이 필요합니다. Pleos/AAOS 차량 환경용 APK입니다. release 빌드는 `debuggable`이 꺼져 있고 MOAGIL release 키로 서명했습니다. 인증서 SHA-256: `A60D1E5C19E4C107708463922D1AD32248D0A7CC97B4529DD747C87FC312D6B6`.

동일한 release 키로 서명한 x86_64 APK를 격리된 Pleos 에뮬레이터에 설치해 앱 실행까지 확인했습니다. 이 저장소에는 제출 대상 ARM64 APK만 둡니다. 실차 ARM64 설치와 주행 시험은 별도 확인이 필요합니다.

release 키와 Windows 사용자 전용 DPAPI 암호 파일은 Git에 올리지 않고 로컬 `.tools/signing/`에 보관합니다. 같은 PC·Windows 계정에서 다시 빌드하려면 `vehicle-app/build-submission-apk.ps1`을 실행하세요. 이후 업데이트를 만들 때도 이 키가 필요합니다. [전체 제출 점검](../docs/submission.md)을 참고하세요.
