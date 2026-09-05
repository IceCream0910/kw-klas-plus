# Android 온라인 강의 인증·화면 전환 후속 검증

- 작업: M5-003 후속, 2026-09-04
- 기준 커밋: `TASKS.md` M5-003 참조. 기존 로컬 커밋을 유지한 작업 트리 변경이며 PR #25를 병합하지 않는다.

## 동작

- 선택 강의의 JSON-safe viewer 스크립트를 보관하고, `klas.kw.ac.kr`의 명시적 인증 성공 alert를 확인한 경우에만 한 번 실행한다. KLAS alert는 성공/실패/안내 모두 Material 3 확인 창으로 표시하고, 확인 버튼으로 JS를 재개한다.
- alert 확인 후 250ms 동안 KLAS 자체 이동을 우선한다. 페이지 이동, 새 강의 요청, 뒤로가기, 영상 URL 수신, 종료 시 자동 실행이 무효화된다. 학교 인증 절차와 SESSION 저장 정책은 바꾸지 않는다.
- inline → PiP → inline, fullscreen → PiP → fullscreen을 보존한다. PiP 진입을 위해 fullscreen 키를 누르거나 복귀 시 portrait를 강제하지 않는다. 사용자가 fullscreen을 종료하면 진입 전 방향 정책으로 돌아간다.
- 영상 영역을 PiP sourceRectHint로 전달하고 Android 15 전환 시작 시 controls를 감춘다. Compose 배치 변경에는 동일한 media composition/WebView를 이동시킨다. fullscreen에는 WindowInsetsController를 사용한다.
- 목록 Activity는 별도로 열 수 있지만 활성 강의 소유자는 앱 프로세스당 하나다. 다른 영상 선택 시 전환 확인을 받고, 기존 VOD 페이지가 `about:blank`로 내려간 뒤 새 강의를 시작한다. 기존 Activity가 다르면 그 Activity의 KLAS/list/video WebView도 정리한다. 취소·바깥 터치·뒤로가기로 확인 창을 닫으면 기존 강의를 변경하지 않는다.
- 소유권 예약과 실제 플레이어 준비를 구분한다. 아직 재생 준비가 되지 않은 최초 요청에는 전환 확인을 띄우지 않으며, 준비 후 일시정지한 강의는 확인 대상이다. 같은 요청의 중복 선택 및 같은 KLAS 문서에서 재전달된 VOD URL은 무시한다. 동시 전환 요청은 직렬화하며, 정리 시간 초과 시 기존 WebView를 정리하고 새 재생은 시작하지 않은 채 재시도를 안내한다.
- VOD 자동 클릭/재시도/취소 로직은 사용자 요청으로 제거했다. 상태 모니터의 지연 설치는 유지한다. Seekbar 탐색 중 좌측 시간은 탐색 비율 × 전체 영상 길이로 표시하고, 탐색 종료 후 실제 플레이어 시간으로 복귀한다.
- Android 13+ PiP 닫기 액션은 즉시 모든 강의 WebView와 소유권을 정리한다. 이전 OS/제스처 닫기는 PiP 이후 onStop에서 정리하며 정상 복귀, 화면 잠금·꺼짐, 구성 변경은 제외한다. 미디어 receiver는 앱 외부 방송을 받지 않는다.

## 자동 검증

2026-09-05: 최신 `origin/kmp`(PR #26)와 통합 후 아래 JVM/common host 테스트 214개(Android 22 + shared 192)와 debug 앱·androidTest APK 빌드 통과. Instrumentation은 APK 컴파일만 확인했으며 기기 실행은 미수행.

실행 명령:

```powershell
.\gradlew.bat :androidApp:testDebugUnitTest :shared:testAndroidHostTest :androidApp:assembleDebug :androidApp:assembleDebugAndroidTest
```

- `LectureCertificationContinuationTest`: 성공 1회 소비, 실패·취소·안내 오인 방지, clear 및 새 강의 교체.
- `SingleLecturePlaybackTest`: 전환 완료 전 기존 소유자 유지, 취소 시 유지, 동시 요청 및 오래된 확인 차단, 이전 Activity 정리가 새 소유자에 미치는 영향 방지.
- `PipPlaybackLifecycleTest`: 닫기 콜백 순서, 정상 복귀, 잠금·화면 꺼짐·구성 변경 제외.
- `VideoPlayerTransitionTest.pipCloseImmediatelyReleasesPlaybackAndDetachesWebViews`: 즉시 소유권 해제/재생 상태 초기화/WebView 분리 검사 추가. 기기 실행 필요.
- `WebAutomationScriptsTest`: 기존 인증 함수 인자 계약과 직접 viewer 호출의 JSON-safe 인자/인증 플래그 차이.
- `VideoPlayerScreenTest.pipRoundTripKeepsMediaAttachedAndRestoresControls`: PiP controls 제거·복구 및 동일 media view 부착. 기기 실행 필요.
- `VideoPlayerTransitionTest`: fullscreen/PiP 콜백 왕복 시 방향 요청과 custom view 보존. 시스템 애니메이션/실제 미디어 재생 검증을 대체하지 않는다.

## 실기기 완료 조건 (미검증)

- Seekbar를 누른 채 이동하면 좌측 시간이 탐색 위치를 따라가고, 손을 놓으면 실제 재생 시간으로 갱신되는지 확인.

현재 adb 연결 기기 없음. iOS target 실행은 Windows에서 불가.

- 미완료 강의 선택 → 본인인증 성공 → 추가 클릭 없이 viewer 진입, 사용자 조작으로 영상 재생. 실제 학교 성공 문구가 matcher와 일치하는지 확인.
- 최초 선택 시 전환 확인 없음. KLAS의 성공/오류 alert가 Material 3로 표시되고, 확인 후 VOD 자동 클릭 없이 기존 재생 조작이 가능한지 확인.
- 인증 실패·취소·기간 제한·세션 만료에서는 재생을 자동 시작하지 않음. 기본 alert/학교 오류 안내 유지.
- 학교가 인증 후 자체 이동하는 경우 viewer가 두 번 로드되지 않음. 인증 후 뒤로가기/다른 강의 선택 시 이전 요청 미실행.
- Android 11 및 Android 12+, Android 15+에서 inline/PiP/복귀, fullscreen/PiP/복귀를 재생·일시정지 각각 확인. 폰 양쪽 가로, 태블릿, 시스템 회전 잠금 포함.
- 가로 fullscreen에서 PiP 진입·확대 시 세로 화면이 끼어들지 않고, 영상 위치·배속·음소거와 출석 진도가 유지됨.
- PiP X/스와이프 닫기 후 소리가 즉시 멈추고 다음 최초 선택에 전환 확인이 뜨지 않는지 Android 11/12와 13+ 각각 확인. 잠금/화면 꺼짐에서는 강의가 종료되지 않아야 한다. PiP 실패/미지원, 홈 제스처, remote play/pause/±10초, 회전 중 연속 조작도 확인.
- A 재생/PiP/일시정지 → 다른 과목 목록 열기만으로 A가 중단되지 않음. B 선택 후 취소·바깥 터치·뒤로가기는 A 유지, 승인하면 A 소리·PiP 종료 후 B 인증/재생. 빠른 연속 선택에도 동시 재생 없음. 같은 Activity 및 별도 Activity 경로 각각 확인.
- 인증 후와 PiP 왕복 후 서버 학습시간/진도 저장까지 실제 계정으로 확인. 화면 표시만으로 출석 저장을 판정하지 않음.

## 롤백

이번 변경의 `VideoPlayerActivity`, `VideoPlayerScreen`, `AndroidPictureInPicture`, 인증 continuation 및 공통 `openOnlineContentViewer` 추가를 함께 되돌린다. 기존 저장 데이터/브리지 메서드/인자에는 마이그레이션이 없으며, 기존 `openOnlineContent` 인증 경로는 유지된다.
