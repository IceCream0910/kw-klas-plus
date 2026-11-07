# ADR-016: 심사용 데모 모드 (#113)

상태: 구현 및 자동 검증 완료. 사용자 동작 확인 완료; 확인 기기·OS 상세는 별도 기록하지 않음.

## 배경과 결정

해외·데이터센터 IP에서 요구되는 학교의 추가 인증을 우회하지 않고 App Store 심사자가 앱을 탐색할 수 있게 한다. 기존 로그인 양식에서 예약 학번 `0000000000`과 지정된 데모 비밀번호가 일치하면 Native가 로컬 데모 세션을 시작한다. 별도 진입 버튼은 없다. 잘못된 데모 비밀번호와 공통 자격증명 준비 경로의 예약 학번은 실제 암호화·인증 API로 보내지 않는다. 데모 계정은 공개된 가상 데이터의 진입 조건이며 실제 계정의 인증 수단이 아니다.

## 세션과 저장소

`DemoSession`의 토큰·세대·fixture 상태는 메모리에 둔다. 데모 로그인 여부는 Android `klasplus_demo` preferences와 iOS `com.icecream.kwklasplus.demo` UserDefaults suite의 `demo_session_active`에 기록해 명시적 로그아웃 전까지 복원한다. 비밀번호와 가상 SESSION을 영속 저장하지 않는다. 프로세스 재시작 시 로그인 상태는 복원하지만 가상 과제·캘린더·재생 위치는 초기화한다.

일반 계정 자격증명·SESSION·학교 cookie·기존 키는 보존한다. 공통 `SessionCoordinator`와 세션 유지 정책은 데모에서 가상 세션만 사용한다. Android 화면 설정은 별도 preferences를 사용한다. iOS는 데모 defaults·메모리 SecureStore·no-op cookie store가 있는 dependencies로 교체하고 로그아웃 시 원래 dependencies를 복원한다. 로그아웃은 세션 세대를 증가시켜 늦은 브리지 응답을 무효화하고 fixture·데모 로그인 기록·테스트 알림·데모 위젯 표시를 정리한다.

## Native 요청과 WebView 경계

- 공통 Ktor client의 `DemoHttpEngine`과 Native KLAS API 브리지는 데모에서 `DemoFixtures`로 응답한다. 미지원 endpoint는 501이며 실제 네트워크로 fallback하지 않는다. 종료된 가상 SESSION의 실제 서버 전송도 차단한다. 일반 세션은 기존 engine으로 위임한다.
- 기존 endpoint·payload·origin·top-level frame·메서드·chunk 제한을 유지한다. 공지·학식도 기존 `requestKlasApi`의 가상 endpoint로 Native가 반환한다.
- Android WebView는 학교 도메인 navigation/resource를 차단한다. iOS는 로그인 완료 전 학교 도메인 content rule을 준비하고 nonpersistent WebView store를 사용한다.
- 신뢰된 KLAS+ origin의 최상위 document-start에만 `__klasDemoSession`을 설치한다. 웹 저장소는 데모 세대별 sessionStorage로 분리하며 실제 localStorage를 읽거나 덮어쓰지 않는다. JS 마커로 Native 데모를 활성화할 수 없다.
- 데모의 웹 adapter는 정상 Native 브리지로 호출을 전달한다. Native `DemoBridgeCommandHandler`가 학교 링크 차단 안내와 과제·설정 경로를 결정한다. 표시용 컴포넌트와 데모 AI 예시 응답은 웹에 남는다. 이전 웹의 차단 안내 `alert`도 Native가 완료 콜백을 즉시 반환해 WebView 클릭이 멈추지 않게 한다.

캘린더 CRUD·과제 제출/수정·강의 진행률은 가상 상태에 반영한다. 데모 할일은 요청 시점의 `Clock` 값 하나로 학기와 날짜를 계산한다. 시작일은 전날이고 강의·개인과제·팀과제 마감은 각각 다음 날·이틀 뒤·사흘 뒤 23:59다. 같은 세션에서도 재조회 시 날짜를 갱신하며 실제 제출·완료 상태는 유지한다.

## 기기 기능과 영상 재생

학교 데이터만 모의하고 Native 화면과 기기 기능은 재사용한다.

- 온라인 강의는 기존 Native 플레이어에서 KLAS+ 서버의 120초 MP4를 재생한다. `DemoPlayer.installController()`가 `<video>` 제어 어댑터와 200ms 상태 모니터를 주입하고 기존 `receivePlayerStates` 브리지로 UI·데모 진행률을 갱신한다. Android도 기존 단일 재생 소유권을 획득하며 데모 버튼/PiP 액션은 키보드 이벤트 대신 공통 명령을 실행한다.
- 실제 배포 서버의 `.html` → 확장자 없는 경로 리디렉션에 맞춰 시작 주소는 `/demo/native-player`를 사용한다. 같은 HTTPS 호스트·443 포트의 해당 경로와 기존 `.html` 경로만 인정한다. 10초 이동 명령은 IIFE로 격리해 배포 페이지의 전역 `const player`와 충돌하지 않게 한다.
- 앱 잠금·생체인식은 실제 기기 설정이다. 기존 보안 저장소를 사용하고 데모 로그아웃으로 해제하지 않는다.
- 마감 알림은 기존 Native 동의 UI와 별도 메모리 ledger를 사용한다. 허용 뒤 5초 후 OS 로컬 테스트 알림을 보내며 일반 계정 알림을 취소하지 않는다. Native 동의 attempt는 음수로 구분해 세션 종료 뒤 일반 동의 요청을 완료하지 못하게 한다.
- QR 스캐너·출석 repository를 재사용하고 `KLASPLUS-DEMO-ATTENDANCE`만 수락한다. 웹의 `/demo/attendance-qr.svg`를 다른 화면에 표시해 시연한다. 도서관 출입증은 실사용 불가능한 샘플이며 실제 도서관 자격증명을 읽거나 변경하지 않는다.
- 위젯은 기존 렌더러를 재사용한다. iOS는 별도 App Group 데모 표시 파일/marker, Android는 메모리 snapshot을 사용해 일반 캐시를 보존한다. iOS 확장 새로고침도 데모 중 실제 계정 세션에 접근하지 않는다.

학교 영상·출석 인정·실제 파일 제출은 수행하지 않는다. 웹 UI와 샘플 MP4를 받으려면 KLAS+ 배포 서버 접속이 필요하므로 비행기 모드 지원은 아니다.

## 호환 배포와 롤백

기준 Native: `5533dffc4b6b906641ef41c5de4b36a0779a606b`.

호환 WebView: `IceCream0910/kw-klas-plus-webview@ef68c0f51b00d118449060f1331e6f54b9b8d652`. Native API 최소 지원 Android 34 / iOS 2와 기존 브리지 메서드·콜백을 유지한다. 데모 전체 기능은 해당 웹 버전과 이번 Native 변경을 함께 사용한다. 최종 플레이어 수정 자체는 웹 추가 배포 없이 Native 재빌드로 적용한다. JS 마커가 없는 일반 세션은 기존 동작을 사용한다.

롤백은 Native·웹 데모 변경을 함께 되돌린다. 웹만 되돌리면 데모 일부 화면이 열리지 않을 수 있으나 Native의 학교 요청 차단은 유지한다. 일반 사용자 데이터 마이그레이션은 없다.

위협과 회귀 대상은 예약 계정의 실제 서버 전송, 미지원 경로 fallback, 실제 캐시·자격증명 노출, 로그아웃 후 늦은 응답, 일반 계정 알림·위젯 데이터의 변경이다. 검증 증거와 한계는 [검증 기록](../issue-113-demo-validation.md)에 남긴다.
