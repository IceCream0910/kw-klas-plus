# KLAS+ KMP 마이그레이션 작업 현황

- 기준일: 2026-08-05
- 현재 단계: **Android 마이그레이션 완료, iOS 기본 제품 경로(M6) 착수 단계**
- Android 상태: KMP 공통 코어, Compose UI, WebView 브리지, 네이티브 기능의 P0/P1 패리티 완료
- iOS 상태: 프로젝트 골격과 최소 지원 정책만 확정. 실제 제품 경로와 플랫폼 기능은 미구현


## 개요

| Milestone | 상태 | 결과 / 다음 게이트 |
|---|---|---|
| M1 계약 고정 | **완료(6/6)** | Android 인증·브리지·저장소·플랫폼 계약 고정 완료 |
| M2 KMP/Android 기반 | **완료(6/6)** | 공통 모듈과 Android source set 경계 정렬 완료 |
| M3 공통 코어 | **완료(9/9)** | 인증·세션·API·보안 저장소·앱 잠금 공통화 및 Android 연결 완료 |
| M4 Android Web/Compose | 진행 중(7/8) | Android 화면 전환 완료. legacy 자산 정리만 남음 |
| M5 Android 기능 패리티 | **완료(7/7)** | QR·잠금·PIP·위젯·파일·테마 및 전체 Android 회귀 통과 |
| M6 iOS 기본 경로 | 미착수(0/11) | **현재 주력 마일스톤**. Web 저장소 대응 포함 |
| M7 iOS 플랫폼 기능 | 미착수(0/7) | M6 기본 경로 이후 진행 |


### M1 — 기존 계약 고정

- [x] **M1-001 (P0, L)** 인증 A-001~A-012 fixture와 상태 전이 테스트
- [x] **M1-002 (P0, L)** Web → Native legacy 브리지 8개 surface/64개 메서드 고정
- [x] **M1-003 (P0, L)** Native → Web callback과 JSON-safe 주입 fixture 고정
- [x] **M1-005 (P0, L)** Android 화면별 golden flow 기록 및 검증
- [x] **M1-006 (P0, M)** 기존 저장 데이터 migration fixture와 업그레이드 검증
- [x] **M1-007 (P1, M)** QR·PIP·Widget·생체·파일 기능 특성화

### M2 — KMP/Android 실행 기반 · 완료

- [x] **M2-001 (P0, M)** 단일 `shared` 모듈 전략 확정
- [x] **M2-005 (P0, M)** Ktor·serialization·coroutine 공통 의존성 도입
- [x] **M2-006 (P0, L)** 공통 platform port와 테스트 fake 정의
- [x] **M2-007 (P1, M)** typed navigation과 capability 모델 정의
- [x] **M2-008 (P0, S)** Android 최소 OS·기기·반응형 정책 확정
- [x] **M2-009 (P0, L)** Android 구현을 KMP source set 경계로 재배치

### M3 — 공통 코어 추출 · 완료

- [x] **M3-001 (P0, M)** URL·preference key·route typed model
- [x] **M3-002 (P0, L)** AuthStateMachine과 LoginUseCase
- [x] **M3-003 (P0, L)** SessionCoordinator와 cookie/store 동기화
- [x] **M3-004 (P0, L)** KLAS API repository
- [x] **M3-005 (P1, L)** LibraryRepository와 Android 호환 crypto/cache
- [x] **M3-006 (P0, L)** Android Keystore SecureStore migration
- [x] **M3-007 (P0, M)** 앱 잠금 도메인·lifecycle 정책
- [x] **M3-008 (P0, L)** 기존 Android UI의 신규 core 연결
- [x] **M3-009 (P0, L)** Android 앱 계층 공통화 감사

### M4 — Android Web/Compose 전환

- [x] **M4-001 (P0, L)** Bridge v1 schema와 router
- [x] **M4-002 (P0, L)** Android legacy façade와 Bridge v1 병행 연결
- [x] **M4-003 (P0, M)** Native → JS JSON-safe dispatcher
- [x] **M4-004 (P0, L)** WebSurface와 Android WebView holder
- [x] **M4-005 (P0, L)** Compose startup/auth shell
- [x] **M4-006 (P0, XL)** Home Web surface Compose 전환
- [x] **M4-007 (P0, XL)** Lecture/Board/Task/Link/Plan Compose 전환
- [ ] **M4-009 (P1, M)** 사용하지 않는 XML/View 자산을 기능별 정리
  - Depends on: M5-007
  - 완료 기준: rollback 자산 목록 확인 후 삭제, debug와 R8 release build 통과

### M5 — Android 네이티브 기능 패리티 · 완료

- [x] **M5-001 (P0, L)** QR 출석과 공통 AttendanceRepository
- [x] **M5-002 (P0, L)** Compose 앱 잠금과 Android 생체인식
- [x] **M5-003 (P0, XL)** Compose 비디오 플레이어와 Android PIP
- [x] **M5-004 (P1, L)** 도서관 QR과 AppWidget
- [x] **M5-005 (P1, L)** 다운로드·파일 선택·외부 이동
- [x] **M5-006 (P1, M)** 테마·방향·태블릿·햅틱·인앱 업데이트 패리티
- [x] **M5-007 (P0, L)** Android P0/P1 업그레이드·회귀·release 검증

### M6 — iOS 기본 제품 경로 · 현재 주력

- [ ] **M6-001 (P0, M)** iOS 툴체인과 최소 지원 환경 고정
  - 완료 기준: macOS에서 Xcode·Kotlin·Gradle 조합, iOS/iPadOS 최소 버전, device/simulator 빌드 경로 확인
- [ ] **M6-002 (P0, M)** iOS device/simulator framework 연결
  - Depends on: M6-001
  - 완료 기준: `iosArm64`·`iosSimulatorArm64` 빌드와 Xcode 링크
- [ ] **M6-003 (P0, M)** SwiftUI entry와 공통 상태/ViewModel 연결
  - Depends on: M6-002
  - 완료 기준: `Shared.framework` API 호출과 lifecycle smoke
- [ ] **M6-004 (P0, M)** Next.js bridge contract test 작성
  - 상태: **미착수**. 대상은 별도 `kw-klas-plus-webview` 저장소다.
  - 완료 기준: `window.Android`, `KlasNativeBridge`, 브라우저 fallback, 앱/Web 버전 조합
- [ ] **M6-005 (P0, M)** Next.js 플랫폼 중립 bridge adapter 추가
  - 상태: **미착수**
  - 필요성: 기존 페이지의 `Android.*` 직접 호출은 iOS `WKWebView`에서 동작하지 않는다.
  - 구현 방향: 페이지는 공통 adapter를 호출하고, adapter가 iOS `KlasNativeBridge`와 기존 Android `window.Android` fallback을 선택한다.
  - Depends on: M6-004, 앱/Web 동시 변경 승인
  - 완료 기준: 직접 `Android.*` 호출 제거, Promise API, version negotiation, Web CI
- [ ] **M6-006 (P0, L)** WKWebView holder와 navigation/cookie adapter
  - Depends on: M6-003
  - 완료 기준: persistent cookie, back/reload/modal/external URL, handler lifecycle
- [ ] **M6-007 (P0, L)** iOS `window.Android` shim과 Bridge v1 handler
  - Depends on: M6-005, M6-006
  - 주의: 네이티브 shim만으로 완료하지 않으며 M6-005의 Next.js adapter 전환을 필수 조건으로 한다.
  - 완료 기준: document-start 주입, trusted origin/main-frame, legacy 동기 설정 조회
- [ ] **M6-008 (P0, XL)** iOS 인증·세션·Keychain 경로
  - Depends on: M6-006, M6-007
  - 완료 기준: F-002~F-006, 재시작 복구, CAPTCHA·임시 비밀번호·네트워크·timeout
- [ ] **M6-009 (P0, XL)** iOS Home/Lecture/Board/Task 기본 경로
  - Depends on: M6-008
  - 완료 기준: F-007~F-016 P0 경로와 앱/Web 버전 조합
- [ ] **M6-010 (P1, L)** iOS 다운로드·파일 선택·외부 링크
  - Depends on: M6-009
- [ ] **M6-011 (P1, M)** iOS 테마·키보드·safe area·회전·접근성
  - Depends on: M6-009

### M7 — iOS 플랫폼 기능

- [ ] **M7-001 (P0, L)** 앱 잠금·LocalAuthentication·Keychain
  - Depends on: M6-008
- [ ] **M7-002 (P0, L)** QR 출석 스캐너
  - Depends on: M6-009
- [ ] **M7-003 (P0, M)** iOS PIP 기술 spike와 방식 결정
  - Depends on: M6-009
- [ ] **M7-004 (P1, XL)** iOS 비디오/PIP 구현
  - Depends on: M7-003
- [ ] **M7-005 (P1, M)** WidgetKit/App Group 기술 spike와 공유 정책
  - Depends on: M6-008
- [ ] **M7-006 (P1, XL)** iOS 도서관 QR·WidgetKit 구현
  - Depends on: M7-005
- [ ] **M7-007 (P1, M)** 플랫폼별 승인 차이와 사용자 안내 정리
  - Depends on: 출시 범위의 M7 작업