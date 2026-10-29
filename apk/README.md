# APK 상태

Android Compose 앱 소스는 `vehicle-app/`에 있습니다. APK는 실제 Gradle 빌드가 성공한 뒤에만 이 폴더에 추가합니다. 현재 산출물과 설치 여부는 [`../docs/validation.md`](../docs/validation.md)를 확인하세요.

APK를 생성한 환경에서 저장소 루트 `vehicle-app` 폴더 기준으로 다음 명령을 실행합니다.

```powershell
.\gradlew.bat testMockDebugUnitTest assembleMockDebug
```

개발용 산출물 경로: `vehicle-app/app/build/outputs/apk/mock/debug/app-mock-debug.apk`. 서명되지 않은 debug APK는 개발/시연용이며 release로 표시하지 않습니다. 실제 SDK와 Android Emulator 설치에는 Google Android SDK의 이용 약관 및 패키지 구성이 필요합니다. 사용자가 소유한 서명키는 저장소에 추가하지 않습니다.
