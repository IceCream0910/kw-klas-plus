# ADR-006: iOS 17 캘린더 위젯 AppIntent 새로고침과 제한적 Keychain 공유

- 상태: Accepted
- 날짜: 2026-09-16
- 기준: [ADR-005](ADR-005-ios-academic-widgets.md), [ADR-004](ADR-004-android-academic-widgets.md), iOS/iPadOS 16.0 최소 버전 [ADR-003](ADR-003-min-platform-versions.md)

## 맥락

ADR-005는 WidgetKit 확장이 SESSION·저장 자격증명을 읽지 않고, 메인 앱이 App Group 표시 JSON만 기록한다고 못 박았다. iOS 16에는 인위젯 버튼이 없어 새로고침이 앱 열기로만 가능했기 때문이다.

Android 캘린더 위젯 새로고침은 앱과 같은 UID에서 `CalendarSyncUseCase`를 호출한다. SESSION을 위젯 캐시 파일에 복제하지 않고 앱의 Keystore canonical 저장소를 그대로 쓴다. iOS WidgetKit은 별도 프로세스라 같은 패턴을 쓰려면 확장에 인증 접근을 열어 줘야 한다.

이 ADR은 ADR-005를 덮어쓰지 않고, 캘린더 위젯 iOS 17+ 새로고침에 한해 보안 경계를 확장한다.

## 결정

1. iOS 17+ 캘린더 위젯 새로고침 버튼은 `AppIntent`로 앱을 전면 실행하지 않고 KLAS 월간 일정을 조회한다. iOS 16은 `kwklasplus://widget/calendar`로 앱을 연 뒤 기존 foreground 동기화를 유지한다.
2. 시간표 위젯과 도서관 QR 위젯은 이 경계를 쓰지 않는다.
3. SESSION 원문은 App Group 파일·UserDefaults에 두지 않는다. 앱과 캘린더 새로고침만 `Keychain Access Group` `$(AppIdentifierPrefix)com.icecream.kwklasplus.academic-session`에서 `SESSION_TOKEN`과 서버 암호화 KLAS 비밀번호를 읽는다. 재인증용 학번(`kwID`)·학기·세션 관찰 timestamp는 비밀이 아니므로 App Group UserDefaults에 미러링한다.
4. 앱·위젯 `keychain-access-groups`의 첫 항목은 각 타깃의 `$(AppIdentifierPrefix)$(PRODUCT_BUNDLE_IDENTIFIER)`다. `kSecAttrAccessGroup`을 생략하면 Apple이 이 첫 그룹을 쓰므로, 공유 그룹만 넣으면 앱 잠금·도서관 비밀이 위젯이 읽는 그룹에 들어간다. 앱 잠금 hash/salt와 도서관 비밀번호·세션 키는 타깃 App ID 그룹을 `kSecAttrAccessGroup`에 명시해 저장한다. 위젯 entitlement에는 앱 번들 ID 그룹을 넣지 않는다.
5. 확장에는 `WKWebsiteDataStore`를 쓰지 않는다. 재인증으로 SESSION이 바뀌면 App Group에 `cookie 동기화 필요` 표시만 남기고, 메인 앱이 `SessionCoordinator.restore()`로 WebView cookie를 맞춘 뒤 표시를 지운다.
6. SESSION과 암호화 비밀번호는 처음부터 공유 Keychain Access Group에만 둔다. 공유 그룹 probe가 실패하면 기본(타깃 App ID) 그룹에 넣지 않고 해당 키의 저장·삭제를 실패한다. iOS 앱이 미출시이므로 앱 전용 Keychain에서 공유 그룹으로의 이전을 두지 않는다. 학번·학기·timestamp는 로그인·세션 저장 때 App Group에 기록하고 로그아웃 때 지운다.
7. 구앱으로 롤백하면 공유 Keychain SESSION을 읽지 못하므로 재로그인이 필요할 수 있다.

## 세션 동기화 계약

| 단계 | 확장 | 메인 앱 |
|---|---|---|
| 조회 | 공유 Keychain SESSION으로 캘린더 API 호출 | 동일 canonical SESSION |
| 만료 | 저장 암호화 비밀번호로 `CalendarSyncUseCase` 재인증 | 동일 유스케이스 |
| 성공 | SESSION+timestamp 기록, revision 증가, cookie 동기화 필요, 스냅샷 저장, calendar timeline reload | `observe()`가 SESSION+timestamp+WK cookie를 한 경로로 기록 |
| 중간 실패 | 이전 SESSION/timestamp 유지, 일정은 마지막 성공 데이터+`RETRY`/`NEEDS_LOGIN` | `SessionCoordinator` 보상 복원 |
| 동시 갱신 | 요청 시작 시 owner/term을 캡처하고 완료 시 불일치면 스냅샷을 쓰지 않음 | 로그아웃·계정 변경이 `clear()`로 공유 비밀과 스냅샷을 함께 지움 |
| CAPTCHA·임시 비밀번호·자격증명 오류 | `NEEDS_LOGIN`, 앱을 열지 않음 | 로그인 화면 |
| 네트워크 오류 | `RETRY`, SESSION을 지우지 않음 | 동일 |

## 위협 모델

- 확장 프로세스가 SESSION과 암호화 비밀번호를 읽을 수 있다. 평문 비밀번호는 재인증 API 호출 순간에만 메모리에 둔다.
- 앱 잠금·도서관 비밀은 앱 App ID 그룹에 명시 저장한다. 위젯 entitlement에는 그 그룹이 없어 확장이 읽지 못한다.
- 홈 화면 위젯은 학사 일정(개인정보)을 계속 표시한다. 인증 비밀은 표시 JSON에 넣지 않는다.
- 기기 잠금 전(`AfterFirstUnlockThisDeviceOnly`) Keychain을 못 읽거나 공유 그룹에 쓰지 못하면 새로고침은 `RETRY`이고 크래시하지 않는다. SESSION·암호화 비밀번호는 기본 Keychain 그룹으로 내리지 않는다.
- 로그아웃은 공유 Keychain 항목, App Group 미러, 표시 스냅샷, cookie 동기화 표시를 모두 지운다.

## 대안

- App Group 파일에 SESSION 저장: 기각. 평문 복제다.
- AppIntent가 timeline만 reload: 기각. 서버 데이터가 바뀌지 않는다.
- iOS 17에서도 앱만 열기: Android 새로고침 패리티를 포기한다. iOS 16 경로로만 남긴다.

## 결과

- 구현은 이 문서를 따른다. ADR-005의 표시 JSON·딥링크·시간표 축소 제외는 유지한다.
- 학사 WidgetKit 타깃은 캘린더 새로고침을 위해 Shared.framework를 링크한다. 도서관 QR 런처는 인증 API를 호출하지 않는다.
- 롤백은 AppIntent 버튼 제거와 공유 Keychain 미사용으로 되돌리며, 이 경우 재로그인이 필요할 수 있다.
