# ADR-001: shared 모듈과 legacy 비교 앱 유지

- 상태: 승인
- 일자: 2026-07-16

## 결정

`sharedLogic`과 `sharedUI`를 분리해 유지하고, 기존 Android 앱은 `legacyAndroidApp` 비교 모듈로 보존한다.

의존 방향은 다음으로 제한한다.

```text
androidApp / iosApp -> sharedUI -> sharedLogic
legacyAndroidApp -> sharedLogic
```

`sharedLogic`은 플랫폼 UI 타입을 참조하지 않는다. `sharedUI`는 공통 Compose UI와 플랫폼 Web surface 어댑터를 소유한다. `legacyAndroidApp`은 공통 코어를 기존 View UI에 먼저 연결해 동작을 검증하는 호환 경로로 사용한다.

## 이유

- 기존 앱을 덮어쓰지 않고 신규 구현과 비교할 수 있다.
- 인증·세션·API를 UI 전환 전에 검증할 수 있다.
- 공통 로직과 UI의 변경 주기 및 테스트 범위가 다르다.
- iOS에는 최종적으로 `sharedUI` framework 하나만 노출할 수 있다.

## 결과

- 일시적인 모듈 및 자원 중복을 허용한다.
- 기능별 패리티 완료 전 legacy Activity를 삭제하지 않는다.
- Android 패리티 완료 후 `legacyAndroidApp`의 제거 시점을 별도 ADR로 결정한다.

