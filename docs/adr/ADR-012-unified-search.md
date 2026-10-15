# ADR-012: 통합 검색과 Agent 컨텍스트 전달

상태: 구현, 실제 KLAS 및 실기기 검증 대기 (2026-10-09)

추적: https://github.com/IceCream0910/kw-klas-plus/issues/92

기준 Native: `dd6f4f5873e1c0fbe7dbde2cfa8836dbc7725958`.
WebView 기준: `ec5ee370cd96441a73064bd4e8a9c305e2bcbe39` 위의 검색 변경과 함께 배포한다.

## 데이터와 브리지

DeadlineRepository의 기존 응답을 마감 필터 전에 별도 DTO로 변환한다. 완료·마감 경과 항목을 보존하며 검색 변환 실패는 마감 계산을 막지 않는다. 과목·종류별 성공/부분/실패/로그인 필요 상태를 전달한다. accountScope는 홈 런타임의 임의 UUID로 앱 재시작 시 이전 실행의 캐시도 폐기한다.

HOME의 `requestSearchData()`는 schemaVersion 1의 `receiveSearchData`를 호출한다. 토큰은 검색 DTO에 넣지 않고 기존 `receiveToken`을 재사용한다. `setSearchOverlayOpen(Boolean)`은 시트와 별도로 내비게이션·뒤로가기를 제어한다. `openSearchBoard(kind, term, courseId, boardNo, masterNo)`는 현재 학기·조회 과목·식별자를 검사하고 기존 게시판 이동을 사용한다. 신규 메서드는 KLAS+ origin 최상위 프레임만 허용하며 구 Android fallback은 없다.

## AI 이동

검색 오버레이는 일반 검색과 Agent 모드를 제공하며 기존 `/agent` 페이지의 대화·기록·첨부 UI를 공유한다. 두 모드는 입력값 하나를 사용한다. 검색 결과의 허용된 메타데이터 최대 5개는 입력 문구에 붙이지 않고 별도 컨텍스트 카드로 표시한다. 사용자가 전송하면 기존 Agent Worker 요청의 `searchContext` 필드로 전달한다. 해당 Worker 변경은 별도 배포가 필요하며 배포 전에는 컨텍스트 활용을 보장하지 않는다.

호환 계약인 HOME의 `openSearchAgent(json)`은 질문과 최대 5개 검색 결과의 허용 필드만 검증해 메모리에 보관하고 기존 `/agent?searchContext=<임의 ID>`를 연다. LINK_VIEW의 `takeSearchContext(id)`는 60초 이내 한 번만 읽는다. 새 전달과 로그아웃은 이전 전달을 무효화한다. URL에 질문·과목·토큰을 넣지 않는다. Agent는 질문만 입력 초안으로 채우고 결과는 컨텍스트 카드로 표시하며 사용자가 전송하기 전에는 AI 요청을 시작하지 않는다. 별도 AI API는 만들지 않는다.

## 웹 검색과 캐시

메뉴·학습 목록은 로컬에서 검색한다. 명시적 제출 또는 1.5초 입력 대기 후 `/api/search`에 소스 목록을 한 번 전달한다. 서버는 기존 프록시와 같은 인증 경계에서 최대 3개를 병렬 조회하고, 완료된 소스부터 NDJSON으로 보내 웹이 개별 반영한다. KLAS 8초·학교 공지 20초 제한, 일시 오류 재시도 1회, 요청 공유·취소, 60초/20개 결과 캐시를 적용한다. 인증 실패·취소·timeout은 재시도하지 않는다. 학교 공지 crawler는 `tpage`와 다음 페이지 여부를 지원한다. 실제 로그인된 KLAS 병렬 조회의 과목별 결과 정확성은 수동 검증이 필요하다.

IndexedDB에는 정규화된 목록 메타데이터만 저장한다. payload 5MiB·3,000개, 조회 후 24시간 오래됨 표시·7일 만료를 적용한다. 토큰·본문·검색어·AI 답변은 검색 캐시에 저장하지 않는다. 저장 실패 시 메모리 검색을 유지한다. 계정·학기 변경과 로그아웃은 요청 및 캐시를 무효화한다.

## 검증과 남은 범위

초기 구현에서 공통/Android 테스트와 iOS 공통 simulator 테스트, 웹 검색·보안·알림 24개 테스트와 TypeScript 검사가 통과했다. 테스트는 완료·만료 항목 보존, 부분 응답, 브리지 계약, 일회성 전달, 허용 필드, 용량·만료·계정 전환, 요청 공유·취소를 다뤘다. 이후 사용자 요청에 따라 추가 테스트·브라우저 검증을 생략했다. 최종 스트리밍 검색, 공유 입력·IME, Agent 컨텍스트 및 과제 document-start 주입 변경에 이 결과를 적용하지 않는다.

실제 KLAS ALL 검색 범위와 제목 필드, 로그인된 원본 이동, 양 플랫폼 키보드·뒤로가기·복귀는 검증 대기다. 현재 게시판 캐시는 검색 응답에서 누적하며 일반 게시판 목록 조회 연동은 아직 없다. 학습 항목은 기존 종류별 목록으로 이동한다. 과제 목록 진입 시 iOS와 document-start script 지원 Android는 공식 KLAS 페이지 스크립트 실행 전에 localStorage의 `selectYearhakgi`, `selectSubj`를 설정한다. 주입은 KLAS origin의 최상위 프레임으로 제한한다. 미지원 Android WebView는 저장 완료 후 새로고침하는 기존 호환 경로를 유지한다. 키 이름과 기존 `evaluate` 브리지 인자는 유지한다. 이 순서 수정은 사용자 요청에 따라 추가 테스트·브라우저 검증을 생략했으며 실제 과목 전환은 수동 검증 대기다. 첨부 본문 검색과 Agent 검색 실행기 통합은 포함하지 않는다. 웹 전체 빌드는 기존 SkeletonLayouts 중복 export로 막혀 있다.

## 롤백

웹은 Android 33·iOS 1 이상 홈 헤더에 통합 검색을 표시하며 그 이전 Agent 지원 버전에는 기존 `/agent` 진입 버튼을 유지한다. 학습 데이터는 신규 Native 계약을 사용한다. 구 Native와 웹의 기존 계약은 유지한다. 변경을 되돌려도 검색 전용 IndexedDB만 남으며 기존 인증·설정 저장소는 바뀌지 않는다. 새 보안 저장 키나 마이그레이션은 없다.
