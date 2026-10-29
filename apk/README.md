# APK 상태

현재 저장소에는 APK가 없습니다. 개발 환경에서 Android SDK/API 34, JDK 17, Gradle wrapper/배포판 및 Android Emulator/ADB를 확인하지 못해 네이티브 앱을 빌드하거나 설치하지 않았습니다. Android Studio의 JBR은 OpenJDK 25이며 이것만으로 Android 플랫폼 SDK가 제공되지는 않습니다.

도구가 설치된 환경에서 Android 네이티브 구현을 추가하고 아래를 검증한 뒤 APK 경로를 갱신해야 합니다.

1. Android SDK API 34와 JDK 17의 경로 및 버전을 기록합니다.
2. `vehicle-app` Gradle wrapper를 만든 뒤 `mockDebug` 변형을 빌드합니다.
3. 일반 Android 에뮬레이터에 설치해 QR, 앱 재시작, 서버 연결, 정차 상태 차단을 확인합니다.
4. 서명키 없이 생성한 산출물은 개발/시연용 debug APK로만 표시합니다.

PRD 수용 기준 AC01/AC07/AC12는 네이티브 구현 및 APK가 추가되기 전에는 완료가 아닙니다.
