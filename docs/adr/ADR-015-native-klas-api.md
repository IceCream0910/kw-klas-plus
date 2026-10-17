# ADR-015: Native KLAS API 전송과 구 빌드 호환

상태: 구현 및 관련 자동 테스트 통과; 실제 KLAS 및 실기기 검증은 출시 조건이다.

추적: [Native #94](https://github.com/IceCream0910/kw-klas-plus/issues/94).

## 기준과 범위

- Native 기준: `182ac5825022a4cb12046817af4fc7f373bd22c8` (`origin/main`).
- WebView 기준: `0e626adec5963ebe9ecffc87d2c05c9c186bb282` (`origin/main`).
- 양 저장소의 `feat/native-klas-api`를 함께 검토한다. Android 보조 WebView의 API 버전은 Native의 문서 시작 빌드 정보로 보완하고 기존 UI의 UA 판정은 유지한다. 원격 push와 PR 생성은 보류한다.
- Android versionCode 34, iOS CFBundleVersion 2부터 웹 `lib/core/nativeApp.js`의 `klasApi` 분기를 사용한다. 기존 버전 및 식별 불가 환경은 기존 Next.js API router를 사용한다.
- KLAS+의 API·공지 집계·강의계획서·게시판 검색·Agent 도구를 이관한다. 공식 KLAS 페이지 자체의 XHR, 강의 플레이어·파일 다운로드는 기존 Native/WebView 기능 경로를 유지한다. Agent가 공식 페이지를 읽는 기능은 등록된 KLAS 페이지의 GET을 Native로 실행하고 웹에서 Markdown으로 변환한다.

## 결정

웹은 기존 `KLAS(url, token, body)` Promise와 응답 JSON 형태를 유지한다. 지원 빌드는 URL을 등록된 경로로 변환하고 SESSION 없이 Native에 요청한다. Native `KlasApiCore`가 SecureStore의 현재 SESSION과 학기 문맥을 읽고 Ktor로 실행한다. Activity·WKWebView 수명주기는 플랫폼 앱 adapter가 소유한다.

웹 API router도 서버용 KLAS core를 사용한다. 강의계획서·공지 집계는 순수 lib로 분리하여 서버에서는 serverKLAS를 주입하고, 지원 빌드에서는 브라우저 lib가 KLAS core와 bridge를 직접 호출한다. 검색의 캐시·중복 병합·점진적 결과 형식은 유지하며 KLAS source transport만 교체한다. 학교 공개 공지·학식 등의 비-KLAS 요청은 기존 서버 경로를 유지한다.

Native 오류·timeout·지원 메서드 불일치는 서버 proxy로 fallback하지 않는다. Android 33 이하와 iOS 1은 기존 proxy 및 집계 API를 유지한다. 새 Native와 구 웹의 한 릴리스 호환을 위해 기존 메서드·callback은 삭제하지 않는다.

## Bridge v1 추가 계약

모든 surface에 다음 비동기 메서드를 추가한다. 정확한 KLAS+ origin과 top-level frame에서만 허용하며 학교 origin·video wildcard에는 새 API 권한을 주지 않는다.

| 메서드 | 문자열 인자 | 응답 |
|---|---|---|
| requestKlasApi | operationId, endpoint, payloadJson | `{ responseId, chunkCount }` 또는 `{ error, status }` |
| readKlasApiChunk | responseId, index | JSON 원문 문자열의 청크 또는 `{ error, status }` |
| cancelKlasApi | operationId | 작업 취소·버퍼 제거 |

`endpoint`는 등록된 POST 경로 또는 `page:` 접두사의 등록된 공식 GET 페이지다. URL·HTTP method·헤더·SESSION을 임의로 정할 수 없다. Web과 Kotlin의 등록 목록을 함께 갱신한다. 내부 JSON은 객체 또는 null이며 파싱 전에 길이와 16단계 중첩을 제한한다. 기존 외부 envelope의 64 KiB 제한도 유지한다.

HTTP 응답은 스트리밍 중 4 MiB에서 제한한다. 반환 JSON을 6,144 UTF-16 code unit 이하로 나누되 surrogate pair를 끊지 않는다. 한 WebView의 작업·응답 수는 합계 16개, 응답 문자 버퍼 예산은 16 MiB, 미소비 응답은 30초다. 실제 HTTP 동시 실행은 플랫폼 공통 core당 최대 4개다. 새 응답 때문에 다른 정상 응답을 퇴출하지 않는다. API 응답의 디스크 캐시는 추가하지 않는다.

라우트 이동·학기 초기화·pagehide에서 웹이 Native 호출을 취소한다. Native 문서 이동·dispose는 작업과 청크 버퍼를 정리한다. 큐 진입 전 캡처한 SESSION 및 학기가 실행·응답 시 달라졌으면 STALE_SESSION 또는 STALE_CONTEXT로 폐기한다. 청크를 읽을 때도 현재 문맥을 재검사한다.

## 인증·오류·쓰기

- SESSION은 Native 보안 저장소에서 읽으며 일반 preferences나 bridge 인자로 새로 저장하지 않는다. 기존 로그인·CookieStore·세션 복구 정책과 키 이름은 유지한다.
- 401은 SESSION_EXPIRED, 403은 ACCESS_DENIED, redirect/비 JSON 인증 페이지는 AUTH_ACTION_REQUIRED다. 서버 오류·네트워크 오류·timeout으로 저장 세션을 지우지 않는다. 이 core는 자동 재인증을 시작하지 않고 기존 앱 인증 흐름에 사용자 조치가 필요한 결과를 전달한다.
- 해외 IP 추가 인증의 실제 HTTP·세션 연계 계약은 학교 확인 사항이다. Native 전환이 추가 인증을 완료하거나 우회한다고 간주하지 않는다.
- 캘린더 쓰기는 자동 재시도하지 않는다. timeout·응답 전송 실패는 OUTCOME_UNKNOWN이며 웹은 목록을 재조회하고 사용자에게 확인을 안내한다. 취소는 이미 전송한 서버 변경을 되돌리지 않는다.
- 같은 operationId의 쓰기는 문서 수명 내 최대 120초·128개 범위에서 재실행을 억제한다. 서버 멱등성이나 프로세스 재시작을 넘는 exactly-once는 보장하지 않는다.

지원 웹은 `window.__klasNativeApi` 문맥을 표시하고 토큰을 기다리는 기존 훅의 gate를 공통 hasKlasSession으로 바꾼다. Native는 토큰 callback과 klasSessionToken 저장 스크립트를 실행하기 전에 해당 문서를 확인하여 지원 웹에는 null 문맥/키 삭제 스크립트만 전달한다. 구 웹에는 기존 callback을 유지한다. 이 웹 표시는 UI 호환 선택이며 보안 권한 판정으로 쓰지 않는다. 호환 기간에는 구 웹의 SESSION 접근 경로가 남으므로 전체 앱에서 SESSION 노출이 제거되었다고 주장하지 않는다.

## 위협과 검증

Native와 서버 core는 HTTPS의 정확한 klas.kw.ac.kr 및 등록 endpoint만 사용한다. 사용자정보·임의 포트·외부 URL과 redirect 자동 추적을 차단한다. 웹의 임의 Cookie·헤더는 Native에 전달하지 않는다. 요청/응답·SESSION·학생 데이터는 로그나 Sentry에 넣지 않는다. 브리지 origin 허용만으로 신뢰 도메인의 XSS가 해결되지는 않으며 기존 게시글 정화와 operation 제한을 함께 유지한다.

검증 대상은 호스트·endpoint 우회, redirect, origin/frame, 구/신 빌드 경계, 큰 응답과 분할, 로그아웃/학기 변경 및 큐 대기 중 계정 변경, callback 토큰 제외와 구 웹 호환, 취소, 쓰기 결과 불명, 집계 부분 성공 및 페이지 정렬이다. 공통 Android 320개·iOS 345개·Android 앱 JVM 45개가 통과했고 Debug 및 instrumentation APK를 컴파일했다. iOS 전체 XCTest 224개를 실행했으며, 이후 변경 관련 42개와 실제 WKWebView 외부 endpoint 거부 1개도 별도로 통과했다. Android 기기 연결이 없어 instrumentation 실행은 미수행이다. 실행 명령과 결과는 WebView의 `docs/native-klas-api.md` 및 로컬 작업 보고에 기록한다. 실제 로그인·추가 인증·게시판 첨부·캘린더 서버 반영·강의 재생·위젯·실기기 네트워크 확인은 자동 테스트로 대체하지 않는다.

## 롤백

새 Native와 기존 웹은 기존 callback으로 동작한다. 웹 배포만 되돌려 기존 서버 경로로 복원할 수 있다. Native 변경을 되돌릴 때는 versionCode/CFBundleVersion 연속성을 유지하고 웹의 적용 기준도 함께 조정해야 한다. 지원 빌드의 실패를 동적으로 proxy로 우회시키지 않는다. 새 비밀 저장 키나 사용자 데이터 마이그레이션은 없으며 사용자 설정과 기존 비밀 저장 키를 유지한다.
