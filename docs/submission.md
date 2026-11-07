# 공모전 제출 점검

점검일: 2026-11-07 (Asia/Seoul)

기준: [현대자동차그룹 차량용 앱 개발 공모전 지원·제출 방법](https://appdevchallenge.auto-hmg.io/guide/submission)

**판정: GitHub 소스와 제출용 서명 APK가 준비되었습니다.** 오래된 debug APK는 저장소에서 제거했고 `apk/`에는 Pleos ARM64 release APK만 남겼습니다. 동일한 키로 서명한 x86_64 APK를 격리된 Pleos 에뮬레이터에 설치해 실행했습니다. 실차 ARM64 설치·주행, 공식 접수양식과 전원 동의서 작성, 실제 이메일 접수는 아직 별도 단계입니다.

## 공식 요건별 상태

| 항목 | 현재 상태 | 제출 단계 |
|---|---|---|
| 저장소 및 소스 | 충족 · [kimps005/MOAGIL](https://github.com/kimps005/MOAGIL), 공개 저장소 | 공개 상태이면 심사용 Collaborator 초대 불필요 |
| 제출 APK | 충족 · 서명된 [MOAGIL_Moagil_release.apk](../apk/MOAGIL_Moagil_release.apk), Pleos ARM64 | 실제 참가 팀명이 MOAGIL과 다르면 파일명과 설명 변경 |
| 서명·실행 확인 | v2 서명 및 `debuggable` 비활성 확인; 동일 키의 x86_64 Pleos APK 실행 확인 | 실차 ARM64 설치와 차량 권한 신호는 별도 시험 |
| README | 앱 소개, 실행 방법, 환경 설정, APK 위치 기재 | 접수양식에 같은 저장소·APK 경로 기록 |
| 시연 영상 | 사용자가 영상 제작 완료; YouTube 업로드와 공개 링크는 사용자가 진행 예정 | 선택 제출; 영상 링크를 접수양식에 기재 |
| 필수 접수양식·동의서 | 저장소에서 작성·서명본 확인 불가 | [공식 양식 DOCX](https://appdevchallenge.auto-hmg.io/downloads/contest-application-consent.docx)에 기획 내용을 쓰고 참가자 전원(최대 5인)이 개별 동의·서명·날짜 작성 |
| 숏폼 챌린지 | 링크 미기재 | 선택 사항; 본상 가산점은 없고 별도 특별상 신청 |

## 최종 빌드 확인

| 검사 | 결과 |
|---|---|
| Android 단위 검사 | `testMockDebugUnitTest`, `testPleosDebugUnitTest` 통과 |
| Pleos release 빌드 | ARM64 및 x86_64 변형 빌드; 제출 파일은 ARM64 |
| 서명 | 3,072비트 RSA release 인증서; APK v2 검증 통과; `debuggable=false` |
| 설치·실행 | 동일 인증서의 x86_64 Pleos release 변형을 격리된 Pleos 에뮬레이터에 설치하고 앱 프로세스 실행 확인 |
| 실차 범위 | ARM64 Pleos 차량 미연결; 속도·기어 권한과 실주행 동작 미확인 |
| 제출 파일 | 23,553,672 bytes; SHA-256 `AE063FCADCEE0AB7C6259020A65B7D3720CB109782B74A75843F1EF16B93697B` |
| 공개 서비스 | `/`, `/app` HTTP 200; `/api/health` 정상; 이전 공개 배포의 브라우저 E2E도 통과 |

Keystore와 암호는 로컬 `.tools/signing/`에만 두며 Git에 올리지 않습니다. 현재 Windows 계정에서 `vehicle-app/build-submission-apk.ps1`을 실행하면 두 Android 단위 검사를 실행하고 signed ARM64 APK 및 SHA-256을 다시 생성합니다. 동일한 앱을 나중에 업데이트하려면 지금의 keystore를 유지해야 합니다. 다른 PC로 옮길 경우 키와 암호를 안전하게 함께 백업하세요.

## 이메일 접수 전 체크리스트

1. 접수양식의 팀명·앱명과 APK 파일명 `MOAGIL_Moagil_release.apk`가 일치하는지 확인합니다. 참가 팀명이 다르면 APK 파일명과 문서를 함께 맞춥니다.
2. 제작한 시연 영상을 YouTube에 올린 뒤 영상 링크를 접수양식에 적습니다. 일반 시연 영상의 별도 업로드 규격은 확인되지 않았습니다.
3. 참가자 모두 접수양식의 동의 여부·서명·날짜를 작성합니다. 대표자는 팀원 전원을 CC에 넣습니다.
4. 2026-11-15 마감 전 `appdevchallenge@hyundai-partners.com`으로 접수양식과 선택 제출물을 보냅니다. 제목: `[차량용 앱 개발 공모전] 팀명(개인은 성명)_대표자명`.
5. 수정 제출은 최초 제출에 사용한 주소에서 제목 앞에 `[수정제출]`을 붙이고 전체 자료와 서명한 동의서를 다시 첨부합니다.

## 재현 명령

저장소 루트:

```powershell
python -m unittest discover -s backend/tests -v
node demo/test-ui-flow.cjs
$env:MOAGIL_BASE_URL = 'https://moagil-eta.vercel.app'
node demo/verify-e2e.cjs
```

Android는 저장소 루트에서 실행합니다:

```powershell
cd vehicle-app
.\build-submission-apk.ps1
```
