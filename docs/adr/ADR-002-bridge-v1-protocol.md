# ADR-002: Bridge v1 프로토콜과 호환 전략

- 상태: 승인
- 날짜: 2026-07-17
- 기준 Native: `76be3b50ba6f3f28ab81c58918542203c6b5933c`
- 기준 Web: `870f94d13f74bf0ffc1963d39d6640658cf32cba`

## 배경

Android 앱과 Web 앱은 `window.Android.*` 64개 메서드와 여러 전역 callback에 결합되어 있다. Web과 앱은 독립 배포되므로 신규 프로토콜을 한 번에 교체할 수 없고, iOS에서도 동일한 의미를 제공해야 한다.

## 결정

Bridge v1 request는 `version`, `id`, `method`, `arguments`를 가진 JSON envelope로 정의한다. response는 동일한 `version`, `id`, `ok`와 `result` 또는 안정된 `error.code`를 반환한다. Native→Web event는 `version`, `id`, typed `event`, `payload` envelope를 사용한다.

- 현재 version은 `1`이며 지원하지 않는 version은 `UNSUPPORTED_VERSION`으로 거부한다.
- request id는 비어 있지 않은 최대 128자 문자열이다.
- 실제 UTF-8 payload는 최대 64 KiB다.
- origin은 `https://klas.kw.ac.kr`, `https://klasplus.yuntae.in`과 정확히 일치해야 한다.
- top-level frame의 메시지만 처리한다.
- 64개 legacy 메서드는 `BridgeMethodId`와 catalog argument schema에 1:1 대응한다.
- handler 예외 메시지는 Web 응답에 포함하지 않고 `HANDLER_FAILURE`만 반환한다.
- 동기 처리는 catalog에서 동기 반환으로 선언된 메서드만 허용한다. 현재 대상은 `getAppLockSettings()`다.
- coroutine 취소는 handler 실패로 변환하지 않고 호출 수명주기로 전파한다.

## 호환 및 버전 협상

기존 `window.Android` façade와 전역 callback은 Android 패리티 및 Web 배포 전환이 끝날 때까지 유지한다. Web의 `KlasNativeBridge.call()`은 v1을 우선 사용하고 지원되지 않는 앱에서는 legacy façade로 fallback한다. 구 계약 삭제는 앱과 Web의 지원 버전 교집합 및 최소 한 릴리스의 병행 운영이 확인된 후 별도 결정한다.

`getAppLockSettings()`의 legacy 동기 반환은 유지한다. 신규 Promise API에서는 비동기 호출을 사용하고, iOS shim은 document start에 필요한 초기 설정 cache를 제공한다.

## 결과

공통 codec과 router는 Android/iOS에서 재사용할 수 있다. 플랫폼 adapter는 신뢰 가능한 origin과 main-frame 정보를 제공해야 하며, Android의 단순 `addJavascriptInterface`만으로 이 정보를 추정해서는 안 된다. Android 연결은 WebView message listener 또는 동등한 origin-aware API를 사용한다.
