# Native → Web 스크립트 인벤토리

기준 Native 커밋은 `76be3b50ba6f3f28ab81c58918542203c6b5933c`이다. 이 문서는 `evaluateJavascript` 호출을 공통 메시지, Web 자동화, 플레이어 제어로 분류한다.

## 공통 메시지로 이전된 경로

아래 값은 `shared`의 `LegacyWebScripts`와 `JavaScriptArgument`를 통해 JSON 문자열 또는 primitive로 인코딩한다. Android는 `executeWebScript`로 완성된 `WebScript`를 실행한다.

| 계약 | 주요 값 | Android 사용처 |
|---|---|---|
| `appLogin.setInitial` | 학번, 서버 암호화 비밀번호 | `MainActivity` |
| `window.receiveToken` | SESSION | `HomeActivity`, `LinkViewActivity` |
| `window.receivedData` | SESSION, 과목, 학기, 경로 | Board/Lecture/LctPlan/Video |
| `window.receiveDeadlineData` | 마감 JSON 문자열 | `HomeActivity` |
| `window.receiveTimetableData` | 시간표 JSON 문자열 | `HomeActivity` |
| `window.receiveIdCardQRValue` | 도서관/학생증 QR | `HomeActivity` |
| `window.setDateTime` | 날짜 문자열, 시작 여부 | `HomeActivity` |
| `window.receiveTheme` | 테마 | `SettingsActivity` |
| `window.receiveYearHakgi` | 학기 | `SettingsActivity` |
| `window.receiveVersion` | 앱 버전 | `SettingsActivity` |
| `window.onAppLockSettingChanged` | typed 설정 object | `SettingsActivity` |
| `window.onBiometricSettingChanged` | boolean | `SettingsActivity` |
| `localStorage.setItem` | SESSION, 학기, 과목 | Home/Lecture/Task/Video |

## 별도 포트 경계

### WebAutomationPort

- KLAS 페이지의 DOM 숨김·스타일 변경
- `appModule.goLctrum`
- 온라인 강의 진입 `appModule.goViewCntnts`, `lrnCerti.checkCerti` — `PlayerWebScripts.OnlineContentRequest`로 이전 완료
- 로그인/과제/비디오 페이지의 DOM 탐색과 click 대체
- 레거시 공개 메서드 `evaluteKLASScript`

`evaluteKLASScript` 철자는 호환을 위해 유지하되 입력 origin, top-level frame, 명령 allowlist가 적용되기 전에는 신규 호출을 추가하지 않는다.

### PlayerControlPort

- play/pause/seek/forward/backward
- playback rate 변경
- fullscreen 열기/닫기
- controller 표시/숨김
- 재생 상태와 진도 보고

재생률, seek, play/pause/fullscreen 명령은 `PlayerWebScripts`의 typed command로 이전했다. DOM 기반 상태 관찰과 forward/backward/controller 스타일은 Android legacy player에 남겨 두고 Compose/iOS player host 전환 시 플랫폼 adapter가 실행한다.

## 금지 사항

- 외부 입력을 Kotlin 문자열 보간으로 JavaScript에 연결하지 않는다.
- SESSION, 암호화 비밀번호, QR 값이 포함된 `WebScript`를 로그에 출력하지 않는다.
- DOM 자동화 스크립트를 KLAS HTTP repository로 위장하지 않는다.
- `RawJson` 또는 임의 `RawScript` 공개 API를 공통 callback 계층에 추가하지 않는다.
