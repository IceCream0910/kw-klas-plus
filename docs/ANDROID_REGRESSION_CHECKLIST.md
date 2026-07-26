# Android 일괄 회귀 검증 체크리스트

이 문서는 Compose UI 재구현 및 iOS 확장 시작 전 현재 View 기반 Android 경로의 기준 동작을 한 번에 검증하기 위한 체크리스트다. 실제 통과 여부는 `TASKS.md`의 M5-007과 `FEATURE_PARITY_MATRIX.md`에 기록한다.

검증 결과: 2026-07-19 사용자 실기기 일괄 검증 후 QR scanner 무스캔 종료에서 `scanner_start_failed` 회귀가 확인됐다. 취소 결과 정규화 수정 빌드의 F-019 재검증이 필요하며 나머지 항목의 기존 통과 결과는 유지한다.

## 1. 기준과 증거

- Native 기준: `76be3b50ba6f3f28ab81c58918542203c6b5933c`
- Web 기준: `870f94d13f74bf0ffc1963d39d6640658cf32cba`
- applicationId: `com.icecream.kwklasplus`
- 버전: `1.2.0` / `32`
- 계정, 비밀번호, SESSION, QR 원문, 학번, 전화번호가 캡처·로그·파일명에 보이지 않게 마스킹한다.
- 각 실패는 기능 ID, 기기, OS, 설치 유형, 재현 단계, 기대/실제 결과와 함께 기록한다.

## 2. 기기 매트릭스

| ID | 필수 환경 | 주 검증 범위 | 결과 |
|---|---|---|---|
| D-01 | Android API 29 phone | 최소 OS, 저장소 이전, 다운로드, WebView | 통과 |
| D-02 | target API 37 수준 최신 Android phone | 권한, 생체인식, PIP, 백그라운드 | 통과 |
| D-03 | Android tablet | 회전, 멀티윈도우, fullscreen, WebView 상태 | 통과 |
| D-04 | 생체인식 미등록 또는 미지원 phone/emulator | 생체인식 실패·fallback | 통과 |

가능하면 D-01 또는 D-02에서 기존 1.1.x/1.2.0 설치 데이터 업그레이드와 신규 설치를 각각 수행한다.

## 3. 공통 실행 규칙

각 주요 화면에서 다음을 함께 확인한다.

- 뒤로가기, 새로고침, 화면 전환 후 복귀
- 백그라운드 1분 이내 복귀와 프로세스 종료 후 재시작
- phone 세로 고정과 tablet 회전 시 crash·중복 modal·중복 callback 없음
- 로딩 중 네트워크 단절, 재연결, 취소 경로
- 외부 URL에 `window.Android`/`KlasNativeBridgeNative`가 노출되지 않음
- SSL 경고를 우회하지 않고 cleartext URL을 앱 내부에서 로드하지 않음
- 화면·로그·Sentry 첨부에 credential/session/QR payload 없음

## 4. 인증·세션·업그레이드

| 시나리오 | 절차 | 기대 결과 | 기기 | 결과 |
|---|---|---|---|---|
| A-001/F-001~003 | 앱 데이터 삭제 후 오프라인/온라인으로 최초 실행 | 오프라인 오류와 재시도, 온라인에서 온보딩·로그인 입력·동의 정상 | D-01,D-02 | 통과 |
| A-002/F-004 | 올바른 ID/PW로 신규 로그인 | 암호화 API 후 자동 로그인, 평문 저장·로그 없음 | D-02 | 통과 |
| A-003/F-005 | 저장 credential, SESSION 제거 후 재시작 | Web 로그인 자동 입력·제출 | D-02 | 통과 |
| A-004/F-006 | 1시간 이내 유효 SESSION으로 재시작 | cookie 동기화 후 홈 한 번만 진입 | D-01,D-02 | 통과 |
| A-005 | 세션 만료 후 홈/API/Web 접근 | 저장소와 cookie 정리 후 로그인 경로 | D-02 | 통과 |
| A-006 | 자동 로그인 완료 직후 앱 종료·재시작 | SESSION/timestamp 복구, 중복 Home 없음 | D-02 | 통과 |
| A-007 | CAPTCHA 또는 임시 비밀번호 계정/fixture | 일반 오류와 구분된 안내·외부 조치 경로 | D-02 | 통과 |
| A-008~009 | 로그인 중 네트워크 차단 및 15초 timeout | credential 삭제 없이 재시도 가능한 오류 | D-01,D-02 | 통과 |
| A-010/F-031 | 1.1.x/1.2.0 데이터 위에 현재 APK 설치 | ID/PW/SESSION/PIN/도서관 설정 무손실 이전 또는 명시적 재로그인 | D-01,D-02 | 통과 |
| A-011 | 설정/홈에서 로그아웃 후 재시작 | cookie/session/localStorage 정책대로 정리, 자동 재진입 없음 | D-02 | 통과 |
| A-012 | 따옴표·역슬래시·Unicode 포함 테스트 payload | JS 문법 오류나 임의 코드 실행 없이 동일 값 전달 | D-02 | 통과 |

## 5. Web 화면과 브리지

| 기능 | 절차 | 기대 결과 | 기기 | 결과 |
|---|---|---|---|---|
| F-007 홈 피드 | feed 진입, 마감일 확인, 탭 전환, 새로고침 | 데이터·로딩·뒤로가기와 tab callback 정상 | D-01,D-02,D-03 | 통과 |
| F-008 시간표/학기 | 학기 변경, 시간표 진입, 앱 재시작 | 선택 유지, 시간표 JSON/교시 표시 정상 | D-02,D-03 | 통과 |
| F-009 캘린더 | 시작/종료 날짜·시간 선택 및 취소 | timezone/값 순서/취소 처리 정상 | D-02 | 통과 |
| F-010 프로필/학생증 | QR 요청, modal 열기·닫기, 밝기 복원 | 학생증·도서관 QR 표시, 민감 값 외부 노출 없음 | D-02 | 통과 |
| F-011 성적 등 | 성적/석차/장학/KLAS AI 진입 | SESSION callback, 학교/외부 링크 분기 정상 | D-02 | 통과 |
| F-012 강의 홈 | 과목 진입, 새로고침, KLAS 화면 전환·복귀 | subject/year/session 유지, back 정상 | D-01,D-02,D-03 | 통과 |
| F-013 강의계획서 | 검색/상세/외부 링크/닫기 | Web route와 외부 이동 정상 | D-02 | 통과 |
| F-014 게시판 | 목록→상세, 공지/자료실, 새 창, mailto | route payload와 back/fullscreen 정상 | D-01,D-02,D-03 | 통과 |
| F-015 과제/시험 | 과제·퀴즈·시험 링크, localStorage 재주입, 영상 전환 | 학기/과목 유지, 반복 reload 없음 | D-02 | 통과 |
| F-016 Link/modal | 내부 링크, 외부 링크, 중첩 modal, 닫기 | 외부는 시스템 앱, 외부 페이지에 bridge 미노출 | D-02,D-03 | 통과 |
| F-024 설정 | 테마·학기·잠금·생체 설정 후 재시작 | 설정 callback과 persistence 정상 | D-02,D-03 | 통과 |

## 6. 플랫폼 기능

| 기능 | 절차 | 기대 결과 | 기기 | 결과 |
|---|---|---|---|---|
| F-017 영상 | 미수강/수강완료 강의 재생, seek, 속도, 음소거, fullscreen, 종료 보고 | 진도·시간·상태 callback과 온라인 강의 payload 정상 | D-02,D-03 | 통과 |
| F-018 PIP | 재생 중 PIP, play/pause/앞뒤 이동, 앱 복귀 | remote action·상태·진도 보존, 중복 player 없음 | D-02 | 통과 |
| F-019 QR 출석 | 성공, 잘못된 QR, 무스캔 종료, 연속 탭, 권한 거부, 만료 세션 | 취소 무알림, 단일 scanner, typed 결과별 안내, 중복 체크인·잔류 loading 없음 | D-02,D-04 | 재검증 필요 |
| F-020 도서관 QR | 최초 설정, 성공, 잘못된 정보, 캐시 만료, 재설정 | repository 오류 구분, QR/밝기/캐시 정상 | D-01,D-02 | 통과 |
| F-021 위젯 | 위젯 추가, 설정 전/후 탭, 테마 변경, 잠금 상태 | 설정 안내 또는 QR modal, 앱 잠금 정책과 충돌 없음 | D-01,D-02 | 통과 |
| F-022 앱 잠금 | 활성화, PIN 설정·오류·해제, foreground/background 반복 | hash migration, lifecycle race, widget 예외 정상 | D-01,D-02 | 통과 |
| F-023 생체인식 | 등록 기기 성공/취소, 미등록·미지원 기기 활성화 | 결과별 UI와 PIN fallback 정상 | D-02,D-04 | 통과 |
| F-025 다운로드 | 게시판/강의/Link/Task에서 다양한 첨부 다운로드 | SESSION/User-Agent, 파일명/MIME, 실패·취소 정상 | D-01,D-02 | 통과 |
| F-026 업로드 | 단일·다중 파일, MIME 제한, picker 취소 | callback 1회, 올바른 URI, 취소 시 null 결과 | D-01,D-02 | 통과 |
| F-027 외부 이동 | https/mailto/tel 및 javascript/intent/file URL 시도 | 허용 scheme만 시스템 앱으로 열리고 위험 URL 거부 | D-02 | 통과 |
| F-028 햅틱 | 14개 legacy 이름을 사용하는 Web 동작 수행 | 기존 Android haptic 상수와 동일, unknown 안전 fallback | D-02 | 통과 |
| F-029 인앱 업데이트 | 업데이트 있음/없음/취소 가능한 내부 테스트 빌드 | flow 결과별 정상 UI, 앱 사용 지속 가능 | D-02 | 통과 |
| F-030 방향/태블릿 | phone 회전, tablet 회전·멀티윈도우, 영상 fullscreen/PIP | 정책대로 방향 유지, WebView/상태 불필요 재생성 없음 | D-02,D-03 | 통과 |
| F-032 오류 수집 | 네트워크/bridge/download 오류 유도 후 Sentry 확인 | 원인 분류 가능, secret·화면·view hierarchy 없음 | D-02 | 통과 |

## 7. 완료 기록

모든 필수 행 통과 후 아래를 채운다.

```text
Build/APK: Debug 및 R8 minify Release
Native baseline:
Web baseline:
Devices/OS:
New install result: 통과
Upgrade result: 통과
Failed or approved differences: 없음
Evidence location:
Rollback build/path:
Verifier/date: 사용자 / 2026-07-19
```
