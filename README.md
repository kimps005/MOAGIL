# MOAGIL | 모아길

**동승자들의 목적지를 모아 실제 도로 경로를 비교하고, 하차 순서를 추천하는 Pleos 차량 앱 프로토타입입니다.**

MOAGIL은 함께 이동하는 사람들이 QR로 각자 목적지를 등록하면, 운전자가 추천 경로와 하차 순서를 확인하고 Pleos 내비게이션에 다음 목적지를 전달할 수 있도록 돕습니다.

## 시연 및 자료

- 시연 영상: [YouTube](https://youtu.be/u-7vw_0MuKI)
- 웹 데모: [소개 화면](https://moagil-eta.vercel.app/) · [운전자 화면](https://moagil-eta.vercel.app/app)
- 소스 코드: [GitHub 저장소](https://github.com/kimps005/MOAGIL)
- 차량 앱: [Pleos ARM64 APK](apk/MOAGIL_Moagil_release.apk)

**웹 데모는 공모전 시연용 프로토타입이며 운영 중인 상용 차량 서비스가 아닙니다.** 브라우저 화면은 데모 서버와 연결되지만, 실제 차량 화면이나 차량 신호·Pleos 내비게이션 연동을 보여 주지는 않습니다.

## 주요 기능

- QR로 동승자를 초대하고 이름과 목적지를 등록합니다.
- Kakao Mobility의 도로 경로를 비교해 하차 순서를 추천합니다.
- 운전자는 추천 순서를 조정할 수 있고, Pleos 앱은 선택된 순서대로 다음 목적지를 차량 내비게이션에 전달합니다.

## Pleos 앱 실행

제출 APK는 **Pleos/AAOS ARM64 차량 환경용**입니다. 일반 스마트폰이나 x86_64 에뮬레이터용 APK가 아닙니다. Pleos 앱 서비스와 필요한 차량 권한이 제공되는 환경에서 실행해야 합니다.

저장소 루트에서 호환 차량 기기를 연결한 뒤 설치합니다.

```powershell
adb install apk/MOAGIL_Moagil_release.apk
```

패키지 ID: `kr.moagil.vehicle` · 최소 Android API: 28 · 앱 버전: `1.0.0-pleos`

APK SHA-256: `AE063FCADCEE0AB7C6259020A65B7D3720CB109782B74A75843F1EF16B93697B`

## 실행 환경과 설정

| 실행 대상 | 환경 및 설정 |
|---|---|
| 공개 웹 데모 | 인터넷 연결이 되는 PC·모바일 브라우저. 별도 Kakao 키나 로컬 서버 실행 없이 열 수 있습니다. |
| Pleos 차량 앱 | Android API 28 이상, Pleos/AAOS ARM64, Pleos NaviHelper와 허용된 차량 권한·신호가 필요합니다. |
| 로컬 개발 | Windows PowerShell, Python 3.12 이상. 전체 지도·경로 기능에는 Kakao REST 및 JavaScript 키를 로컬 `.env`에 설정합니다. `.env.example`을 참고하세요. |
| Android 빌드·검증 | JDK 17, Android SDK API 34, Build Tools 36.0.0, Pleos SDK 3.0.5. |

## 검증 범위

공개 웹 데모의 참가·경로·하차 흐름을 확인했습니다. Pleos x86_64 에뮬레이터에서는 별도 변형의 설치와 실행만 확인했으며, 전체 기능을 검증한 것은 아닙니다. 제출용 ARM64 APK의 실차 설치, 차량 위치·속도·기어 신호와 실제 주행은 확인하지 않았습니다.
