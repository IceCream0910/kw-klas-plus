# ADR-013: DEADLINE WebView 토글 / Native 권한 시트 계약

2026-10-03. ADR-011의 수정 설계를 따른다. Native 기준 7faa2f80490125e8bf46ed70b4511d696904b69d, Web 기준 bc5a173b9a55532e6d782b4e5c058a62e019cdd6. 캘린더 알림은 없다.

웹 설정에 마감 임박 알림 ON/OFF 스위치를 제공한다. OFF는 Native 저장 성공 후 반영한다. ON은 네이티브 설명 bottom sheet를 시작하며 허용/저장 완료 전에는 실제 enabled를 true로 표시하지 않는다. pending 동안 조작을 막고 시트 취소/거부/실패 시 실제 상태로 복귀한다. 웹에는 권한 CTA/OS prompt나 별도 상태 저장이 없다.

| Bridge v1 메서드 | 인자 | 응답 |
|---|---|---|
| getNotificationCapabilities | 없음 | feature=deadlineReminders, schemaVersion=1, consentFlow=nativePermissionSheet, supportedMethods |
| getDeadlineNotificationState | 없음 | enabled, pending, permission, status, ready |
| setDeadlineNotificationsEnabled | Boolean 하나 | false는 해제 저장, true는 시트 시작; 최신 상태 응답 |
| openDeadlineNotificationSettings | 없음 | 구 opener 호환 별칭; 같은 시트 시작 후 opened 반환 |

Home/Settings의 KLAS+ origin·main frame만 허용한다. 모든 새 알림 메서드는 window.Android fallback 금지다. 새 consentFlow·state/set 메서드가 없는 구 앱은 토글을 숨긴다. 오류 시 이전 상태를 유지하고 재조회/재시도를 제공한다. 응답은 화면 수락/저장 상태이며 OS 실제 표시 성공을 의미하지 않는다.

시트는 ‘권한 허용하기’ CTA에서만 OS 허용 요청을 실행한다. 거부/재요청 불가 시 안내와 시스템 설정 이동 버튼을 표시한다. 허용 및 저장 성공 후 ‘알림 설정 완료’와 알림 카드 예시를 표시하며 별도 닫기 버튼으로 닫는다. 이미 authorized/provisional인 기기는 시트 진입 시 권한 요구 단계를 생략하고 설정 저장 성공 후 완료 안내를 표시한다. 시트 attempt·계정 generation·revision이 바뀌면 늦은 결과를 버린다.

웹은 mount·focus·visibility 복귀와 pending 동안 1초 간격으로 상태를 확인한다. 동시에 여러 조회를 만들지 않고 unmount에서 정리한다. 2분 이후 조회를 멈추고 재확인 버튼을 제공한다. OS 설정 복귀 시 다시 확인한다. 오래 열린 시트는 자동 성공으로 간주하지 않는다.

최신 feed DTO·callback과 DEADLINE 하루 항목별 중복 방지 및 새 항목 추가 안내는 유지한다. 홈 feed/권한 허용 완료는 발송이나 claim을 유발하지 않으며 Native 백그라운드 조회에서만 게시한다. Web 메서드/응답 변경은 없다. 실제 권한/저장 성공, 거부, 닫기, 시스템 설정 복귀, 저장 실패, 늦은 결과, 구 앱 및 legacy fallback을 Native/Web 테스트로 검증한다.

### 홈 할 일 조회 결과 (#120)

Native는 신뢰된 KLAS+ origin의 최상위 프레임에서 선택적 `window.receiveDeadlineFeed(status, json)`을 호출한다. 앱의 계정·학기·탭·탐색 세대·요청 번호가 바뀌면 늦은 응답을 버린다. JSON은 기존 `receiveDeadlineData`의 문자열 배열 형식을 사용한다. 새 브리지 메서드는 없다.

| 상태 | 동작 |
|---|---|
| `READY` | 배열을 표시한다. 전체 성공에서만 빈 배열을 완료 상태로 표시한다. |
| `PARTIAL` | 성공한 과목의 할 일과 재시도 안내를 표시한다. 표시할 할 일이 없는 부분 성공은 빈 문자열로 전달하고 완료 문구를 숨긴다. |
| `FAILED` | 빈 문자열과 함께 실패·재시도를 표시한다. 이미 표시한 목록은 유지한다. |
| 세션 만료 | Native의 기존 재인증 흐름으로 이동한다. |

구 앱은 기존 한 인자 콜백을 계속 사용한다. 새 앱과 구 WebView 조합은 전체 성공과 표시할 할 일이 있는 부분 성공만 기존 콜백으로 보낸다. 부분 성공·실패 안내는 Native 메시지로도 제공한다. 웹을 먼저 배포할 수 있으며 별도 브리지 버전 변경은 없다. 롤백은 두 저장소의 변경을 되돌리면 된다. 새 저장 키는 없다.

검토 기준: Native `022a29f718e308894dd5a84fec5f7082d3045427`, WebView `8f33e1241f142868d509ac52ed4ea5ccf654f627` 및 해당 콜백을 추가한 WebView 변경.
