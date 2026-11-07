# 공모전 제출 점검

점검일: 2026-11-07 (Asia/Seoul)

검사한 앱 소스: `f9e76a5` (`main`)

기준: [현대자동차그룹 차량용 앱 개발 공모전 지원·제출 방법](https://appdevchallenge.auto-hmg.io/guide/submission)

**판정: 공개 저장소와 소스 코드 항목은 준비되어 있지만, APK를 포함한 제출은 아직 준비되지 않았습니다.** `apk/`에는 이전 debug APK만 있으며, 최신 소스의 release 빌드는 성공하되 서명되지 않은 APK를 생성합니다. 공식 접수양식(기획서)은 필수이고 GitHub·APK·영상은 선택 제출입니다. 접수양식의 작성·서명 여부와 실제 이메일 접수 여부는 이 저장소만으로 확인할 수 없습니다.

## 공식 안내와 비교

| 항목 | 현재 상태 | 제출 전 할 일 |
|---|---|---|
| 저장소 주소와 접근 | 충족 · [kimps005/MOAGIL](https://github.com/kimps005/MOAGIL), Public | 공개 상태를 유지하면 심사용 Collaborator 초대 불필요 |
| 프로젝트 소스 코드 | 충족 · Python API, 웹, Android/Pleos 코드와 Gradle wrapper 포함 | 최종 APK를 만든 소스 버전을 기록 |
| `apk/` 폴더 | 있음 · debug APK 4개 | 서명·설치 검증을 마친 release APK 추가 |
| 실행 가능한 release APK | 미충족 · 현재 APK는 Android Debug 인증서로 서명되고 `debuggable` 활성화 | 소유자가 관리하는 release 키로 최신 빌드를 서명하고 대상 기기에 설치·실행 확인 |
| `팀명_앱이름_release.apk` 파일명 | 미충족 | 실제 참가 팀명 또는 개인 참가 정보를 확정하고 안내 형식으로 저장 |
| 앱 이름과 한 줄 소개 | 충족 · 루트 README 첫 부분 | 제출용 명칭과 일치하는지 확인 |
| 실행 방법 | 있음 · 공개 웹 체험, 서버 실행, Android debug 빌드 | 최종 release APK의 설치 방법과 대상 환경 추가 |
| 테스트 환경과 필요한 설정 | 있음 · JDK 17, API 34, Build Tools 36.0.0, Kakao·Supabase 설정 | 최종 APK를 설치한 Pleos 환경과 허용 권한 기록 |
| APK 위치 또는 Release 링크 | 위치 안내 있음 · 현재 링크는 debug 파일 | 루트 README와 `apk/README.md`에 최종 release 파일 직접 링크와 SHA-256 추가 |
| 필수 접수양식·전원 동의서 | 저장소에서 확인 불가 · 공식 작성·서명본 없음 | 공식 DOCX를 작성하고 참가자 전원 동의·서명·날짜 확인 후 이메일에 첨부 |
| 시연 영상 | 저장소에 완성 영상 없음 · 촬영 스크립트와 스토리보드만 있음 | 선택 제출. 영상으로 제출할 경우 실제 재생 가능한 파일·링크 준비 |
| 숏폼 링크 | 확인 불가 | 선택 제출. 본상 가산점 없이 별도 특별상으로 운영 |

## 이번에 직접 확인한 결과

| 검사 | 결과와 범위 |
|---|---|
| 원격 동기화 | 점검 시작 시 `main`과 `origin/main`이 `f9e76a5`로 일치 |
| 배포 상태 | 해당 커밋의 Vercel 상태 `success` |
| 공개 웹과 DB | `/`, `/app` HTTP 200; `/api/health`가 `status=ok`, `database=ok`, `mode=kakao` 반환 |
| 실제 웹 지도 | 공개 origin에서 Kakao SDK와 지도 타일 HTTP 200; 타일 이미지와 지명 표시 직접 확인. Android WebView에서의 확인은 아님 |
| Python 회귀 검사 | 39개 통과 |
| 웹 UI 검사 | 기존 `demo/test-ui-flow.cjs` 통과. 외부 지도/API를 대체한 화면 흐름 검사 |
| 공개 서비스 E2E | 기존 `demo/verify-e2e.cjs`를 공개 배포 주소에서 실행해 통과. 참가·검색·목적지 핀 변경·순서 계산·수동 순서·인증·정원·동시 등록·취소·하차·종료 확인. 이 검사의 지도 SDK는 대체 구현이므로 실제 지도는 별도 검사 |
| 실제 Kakao 도로 경로 | 4명 24개 순서와 첫 하차 고정 6개 순서 확인. 24개 순서 계산은 이번 1회 15.54초였으며 보장 시간은 아님 |
| Android 단위 검사 | `testMockDebugUnitTest`, `testPleosDebugUnitTest` 각각 1개 통과 |
| Pleos release 빌드 | `assemblePleosRelease` 통과. ARM64와 x86_64 각각 23,545,480 bytes의 unsigned APK 생성 |
| 현재 GitHub APK | 2026-11-05 갱신본. 문서에 적힌 크기·SHA-256과 일치하지만, 이후 Android 변경 5개 커밋이 포함되지 않음 |
| 로컬 파일 제외 | 현재 추적 파일에 `.env`, `local.properties`, `.tools/`, 빌드 결과, 서명 키 파일이 없는지 확인. 전체 Git 이력의 비밀정보 감사를 의미하지 않음 |

이번 검사의 임시 세션은 완료하거나 취소했습니다. 생성된 unsigned APK와 캡처는 빌드·점검용 로컬 파일로 두며 제출 파일로 업로드하지 않습니다.

현재 release 서명 설정이 없으므로 `assemblePleosRelease`의 성공만으로 설치 가능한 APK가 준비된 것은 아닙니다. Android는 설치 전 APK 서명을 요구합니다. [Android 공식 서명 안내](https://developer.android.com/studio/publish/app-signing)를 참고하세요.

## 제출 전 남은 작업

1. 실제 팀명·앱 이름과 심사 환경의 CPU(ARM64 또는 x86_64)를 확정합니다. Pleos APK는 Pleos/AAOS 서비스가 있는 환경용이고 일반 Android 스마트폰 시험에는 Mock 변형을 사용합니다.
2. 최신 Android 소스로 release APK를 만들고 소유자가 관리하는 release 키로 서명합니다. 키와 비밀번호는 Git에 올리지 않습니다. 기존 debug APK의 파일명만 바꾸지 않습니다.
3. 서명된 APK를 대상 Pleos 환경에 설치하고 시작·QR 참가·지도·목적지 변경·경로 계산·NaviHelper 전달을 확인합니다. 현재 최신 소스는 차량 속도·기어 권한과 정차/P 신호가 없으면 운전자 조작이 잠기므로, 신호 수신을 먼저 확인해야 합니다. 이번에는 연결된 Android 시험 기기가 없어 설치·차량 신호·내비 실기 검증을 하지 않았습니다.
4. 검증한 파일을 `apk/팀명_앱이름_release.apk`로 저장하고 루트 README·APK 안내에 링크, 앱 버전, 빌드 소스, 시험 환경, SHA-256을 기록합니다. 용량 때문에 저장소 업로드가 어려우면 공식 안내에 따라 GitHub Releases 페이지를 제출합니다.
5. 공식 접수양식(기획서)에 저장소 주소와 최종 APK 파일 경로·링크를 기재합니다. 작성·서명한 접수양식과 동의서는 이메일 첨부로 준비하며 공개 GitHub에 올릴 필요가 없습니다.
6. 2026-11-15 마감 전 `appdevchallenge@hyundai-partners.com`으로 제출합니다. 제목은 `[차량용 앱 개발 공모전] 팀명(개인은 성명)_대표자명`이며 팀원 전원을 CC합니다. 수정 제출은 처음 제출한 주소에서 제목 앞에 `[수정제출]`을 붙이고 전체 자료·서명 동의서를 다시 첨부합니다.

## 재현 명령

저장소 루트:

```powershell
python -m unittest discover -s backend/tests -v
node demo/test-ui-flow.cjs
$env:MOAGIL_BASE_URL = 'https://moagil-eta.vercel.app'
node demo/verify-e2e.cjs
```

Android (`vehicle-app` 폴더, JDK 17·API 34·Build Tools 36.0.0 필요):

```powershell
.\gradlew.bat testMockDebugUnitTest testPleosDebugUnitTest assemblePleosRelease
```

이 Android 명령은 현재 설정에서 unsigned APK를 생성합니다. 서명·설치·실행 검증은 별도 단계입니다.
