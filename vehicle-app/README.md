# 네이티브 차량 앱

Android 네이티브 차량 앱은 아직 구현되지 않았습니다. 현재 차량 흐름은 저장소 루트의 브라우저 화면에서 실행합니다. 구현되지 않은 SDK 코드나 APK가 있는 것처럼 표시하지 않습니다.

다음 단계에서 SDK/API 34, JDK 17, Gradle을 준비한 뒤 PRD의 `NavigationPort`, `PositionPort`, `DrivingStatePort` 인터페이스와 mock 구현, Compose 화면, 안전 상태 테스트를 추가하고 `mockDebug` APK를 확인합니다. 현재 환경 제한과 재현 절차는 [`../docs/validation.md`](../docs/validation.md)에 있습니다.
