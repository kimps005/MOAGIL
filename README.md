# MOAGIL | 모아길

**모아길(MOAGIL)은 차량 QR코드로 앱 설치 없이 접속한 동승자가 지도에서 하차 장소를 직접 고르면, 여러 목적지의 하차 순서를 실시간 교통상황에 맞춰 자동으로 계산해 하나의 경로로 안내하고 목적지 설명·조율과 내비게이션 입력·재설정 수고를 줄여 운전자가 주행에 더 집중하도록 돕는 서비스입니다.**

## 시연 및 자료

- 시연 영상: [YouTube](https://youtu.be/u-7vw_0MuKI)
- 에뮬레이터 앱 화면 시연 영상: [YouTube](https://youtu.be/aYubrDH5uk0)
- 웹 데모: [소개 화면](https://moagil-eta.vercel.app/) · [운전자 화면](https://moagil-eta.vercel.app/app)
- 소스 코드: [GitHub 저장소](https://github.com/kimps005/MOAGIL)
- 차량 앱: [Pleos ARM64 APK](apk/최민수_모아길_release.apk)

**권장 시연 방식:** 차량용 앱의 화면과 동작은 웹 데모보다 Pleos x86_64 에뮬레이터에서 모아길 앱을 직접 실행해 보여주는 방식을 권장합니다. 웹 데모는 동승자 참여와 목적지 등록 흐름을 확인하는 보조 자료입니다.

**웹 데모는 공모전 시연용 프로토타입이며 운영 중인 상용 차량 서비스가 아닙니다.** 브라우저 화면은 데모 서버와 연결되지만, 실제 차량 화면이나 차량 신호·Pleos 내비게이션 연동을 보여 주지는 않습니다.

## 주요 기능

- QR로 동승자를 초대하고 이름과 목적지를 등록합니다.
- Kakao Mobility의 도로 경로를 비교해 하차 순서를 추천합니다.
- 운전자는 추천 순서를 조정할 수 있고, Pleos 앱은 선택된 순서대로 다음 목적지를 차량 내비게이션에 전달합니다.

## Pleos 앱 실행

제출 APK는 **Pleos/AAOS ARM64 차량 환경용**입니다. 일반 스마트폰이나 x86_64 에뮬레이터용 APK가 아닙니다. Pleos 앱 서비스와 필요한 차량 권한이 제공되는 환경에서 실행해야 합니다.

저장소 루트에서 호환 차량 기기를 연결한 뒤 설치합니다.

```powershell
adb install apk/최민수_모아길_release.apk
```

패키지 ID: `kr.moagil.vehicle` · 최소 Android API: 28 · 앱 버전: `1.0.0-pleos`

APK SHA-256: `AE063FCADCEE0AB7C6259020A65B7D3720CB109782B74A75843F1EF16B93697B`

## 실행 환경과 설정

| 실행 대상 | 환경 및 설정 |
|---|---|
| 공개 웹 데모 | 인터넷 연결이 되는 PC·모바일 브라우저. 별도 Kakao 키나 로컬 서버 실행 없이 열 수 있습니다. |
| Pleos 차량 앱 | Android API 28 이상, Pleos/AAOS ARM64, Pleos NaviHelper와 허용된 차량 권한·신호가 필요합니다. |
| 로컬 개발 | Windows PowerShell, Python 3.12 이상. 공개 배포본은 배포 환경변수를 사용하므로 별도 키 입력이 필요하지 않습니다. 로컬에서 지도·경로 기능을 실행할 때만 `.env.example`을 참고해 서버용 Kakao REST 키(경로·장소 검색)와 지도용 JavaScript 키를 `.env`에 설정합니다. 키 값은 GitHub에 올리지 마세요. |
| Android 빌드·검증 | JDK 17, Android SDK API 34, Build Tools 36.0.0, Pleos SDK 3.0.5. |

## 검증 범위

공개 웹 데모의 참가·경로·하차 흐름을 확인했습니다. Pleos x86_64 에뮬레이터에서는 별도 변형의 설치와 실행만 확인했으며, 전체 기능을 검증한 것은 아닙니다. 제출용 ARM64 APK의 실차 설치, 차량 위치·속도·기어 신호와 실제 주행은 확인하지 않았습니다.
