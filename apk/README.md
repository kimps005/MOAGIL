# APK 상태

Android Compose 앱 소스는 `vehicle-app/`에 있습니다. mockDebug APK는 Gradle 빌드 후 Pleos Connect 에뮬레이터에서 설치·실행을 확인했습니다.

- 저장소에 포함된 설치 파일: [`MOAGIL-mock-debug.apk`](MOAGIL-mock-debug.apk)
- 로컬 Gradle 산출물: `vehicle-app/app/build/outputs/apk/mock/debug/app-mock-debug.apk`
- SHA-256: `726A1ECB01AFA1F94A89D4F920CE6396FB5734E5016BF6FF054D6E78666DE0D6`
- 테스트 및 에뮬레이터 결과: [`../docs/validation.md`](../docs/validation.md)

APK를 생성한 환경에서 저장소 루트 `vehicle-app` 폴더 기준으로 다음 명령을 실행합니다.

```powershell
.\gradlew.bat testMockDebugUnitTest assembleMockDebug
```

개발용 산출물 경로: `vehicle-app/app/build/outputs/apk/mock/debug/app-mock-debug.apk`. 이 APK는 Gradle 기본 debug 키로 서명된 개발/시연용 산출물이며 release용으로 표시하지 않습니다. 실제 SDK와 Android Emulator 설치에는 Google Android SDK의 이용 약관 및 패키지 구성이 필요합니다. 사용자가 소유한 release 서명키는 저장소에 추가하지 않습니다.

연결된 에뮬레이터에 저장소 APK를 설치하려면 저장소 루트에서 실행하세요.

```powershell
adb install -r apk/MOAGIL-mock-debug.apk
```

이 파일은 mock 앱입니다. 실제 Pleos SDK, 지도, 교통 및 차량 기능은 포함되지 않습니다.
