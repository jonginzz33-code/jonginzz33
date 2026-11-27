---
name: play-release
description: 사용자의 안드로이드 앱(광고알림 차단 등)을 Google Play 내부 테스트로 올리는 절차와 사용자 선호. "올려줘", "업데이트 올려", "내부테스트", "Play에 올려", 새 앱 Play 등록, 테스터 추가 요청 시 사용.
---

# Play 내부 테스트 업로드

## 사용자 선호 (꼭 지킬 것)
- **테스터: 내부 테스트는 이메일 목록 `테스터`(12명), 비공개 테스트는 구글 그룹 `mytoilet-testers@googlegroups.com`.** 새 목록을 만들라고 하지 말 것. 비공개 테스트 그룹은 `play-upload/listing.json`의 `closedTesting.googleGroups`로 API가 자동 등록.
  새 앱/새 트랙이면: 내부 테스트 → **테스터** 탭 → `테스터` 체크 → 변경사항 저장, 이것만 안내.
- 설명은 **이 대화창에서 바로**, 단계마다 번호 + 버튼 이름 굵게 + 복사할 주소/글자는 코드블록(회색 상자)으로.
- 가능한 건 전부 Claude가 직접(터미널/GitHub Actions) 하고, 사용자에게는 구글이 막아둔 것만 요청.
- 사용자는 폰/PC 크롬 둘 다 씀. Play Console 링크는 개발자 ID를 넣은 직접 링크로 줄 것.

## 고정 정보
| 항목 | 값 |
|---|---|
| 개발자 계정 ID | `7087559415015330997` (개인 계정) |
| 앱 목록 | https://play.google.com/console/developers/7087559415015330997/app-list |
| 사용자 및 권한 | https://play.google.com/console/developers/7087559415015330997/users-and-permissions |
| 업로드 서비스 계정 | `play-upload@adblock-upload.iam.gserviceaccount.com` (GCP 프로젝트 adblock-upload) |
| GitHub 비밀값 | `PLAY_SA_JSON` (저장소 jonginzz33-code/jonginzz33) |
| 광고알림 차단 패키지 | `com.jonginzz.adblock` (소스: `ad-blocker-app/`) |
| 광고알림 차단 테스터 참여 링크 | https://play.google.com/apps/internaltest/4699003768243359180 |
| 비공개 테스트 테스터 구글 그룹 | `mytoilet-testers@googlegroups.com` (조과일지 등 다른 앱과 공유) |

서비스 계정 권한은 앱별로 줘야 한다. **새 앱은 "관리자(모든 권한)"를 그 앱에만** 주게 안내.
(초안 앱은 '앱 초안 수정 및 삭제'가 없으면 commit이 403 PERMISSION_DENIED — 2026-11-27 실제로 겪음)

## 업로드 방법 (자동)
1. `ad-blocker-app/app/build.gradle.kts`의 `versionCode`를 +1, `versionName` 올림.
2. 서명 키(`ad-blocker-app/upload-key.jks` + `keystore.properties`)로 `gradle bundleRelease`.
   - 키는 저장소에 없다(공개 저장소). 클라우드 세션이 바뀌면 사라지므로 사용자에게 받은
     `upload-key.jks`/`keystore.properties.txt`가 필요. 잃어버렸으면 Play Console에서 업로드 키 재설정 요청.
   - Android SDK는 cmdline-tools를 scratchpad에 받아 `ANDROID_HOME`으로 지정(compileSdk 36).
3. 새 AAB를 `play-upload/`에 넣고(이전 aab는 삭제, 스크립트는 이름순 마지막 aab 사용),
   `play-upload/listing.json`의 `releaseNotes` 수정 → 커밋·푸시.
4. 푸시하면 `.github/workflows/play-upload.yml`이 `tools/play_upload.py`를 실행:
   AAB 업로드 → 스토어 글/아이콘/그래픽/스크린샷(`play-upload/screenshots/*.png`) → internal 트랙 completed.
5. 결과는 GitHub Actions 로그로 확인(“✓ 완료”). 재실행은 actions rerun.

## 구글이 API로 못 하게 막은 것 (사용자 몫)
- 개발자 가입·결제·신분 확인, 앱 만들기(패키지 이름 입력)
- 앱 콘텐츠 설문(콘텐츠 등급, 데이터 보안, 타겟층 등)
- 테스터 목록 선택(`테스터` 체크), 참여 링크 복사(링크는 Console 테스터 탭에만 있음)

## 참고
- 집 PC(DESKTOP-F33UIDP)는 클라우드 세션에서 Remote Control 폴더가 not_available로 연결 안 됨(원인 미상, 앱은 최신).
- 등록 안내 문서: https://claude.ai/artifact/8Kgh2hQFLvwbSkNty5Tkwy
- 심사 전(초안) 앱: 비공개 테스트(alpha)는 API로 **draft**만 가능 → 사용자가 Console 비공개 테스트에서 '검토 → 출시'(앱 콘텐츠 설문 완료 필요).
  내부 테스트는 completed 유지. 트랙 PUT은 기존 출시를 덮어쓰므로 내부 트랙을 draft로 올리지 말 것(2026-11-27 실수 후 수정).
- 비공개 테스트 참여 링크(구글 그룹 회원용): https://play.google.com/apps/testing/com.jonginzz.adblock
