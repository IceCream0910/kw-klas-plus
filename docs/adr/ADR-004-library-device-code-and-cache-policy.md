# ADR-004: 도서관 device code와 세션 캐시 정책

- 상태: Android 적용, iOS 서버 호환성 확인 대기
- 기준일: 2026-07-17

## 결정

도서관 QR workflow는 `LibraryRepository`에서 비밀키 요청, 로그인, QR 조회 순서를 공통으로 소유한다. Base64/AES와 HTTP 전송은 플랫폼 어댑터로 주입한다.

기존 Android 요청의 `device_gb=A`는 Android에서 그대로 유지한다. 값은 repository 생성 시 주입 가능하게 두되, iOS 구현에서 임의로 `I` 등의 값을 추정하지 않는다. iOS 작업 시작 전 서버 운영자 확인 또는 실서버 계약 검증으로 허용값을 확정한다.

secret 캐시는 저장 후 30일, authKey 캐시는 12시간 동안 사용한다. timestamp가 없는 기존 Android 캐시는 최초 접근을 허용하고 그 시점의 timestamp를 기록한다. 만료된 secret은 secret/authKey를 함께 삭제하며, 만료된 authKey는 authKey만 삭제해 재로그인을 유도한다. 네트워크 외 모든 workflow 실패에서도 기존 동작과 같이 두 캐시를 함께 삭제한다.

## 근거와 영향

- `device_gb` 값을 근거 없이 바꾸면 Android 회귀와 계정 잠금 위험이 있다.
- authKey를 비밀번호와 동일한 장기 캐시로 취급하지 않는다.
- 기존 timestamp 없는 설치 데이터를 즉시 폐기하지 않아 업그레이드 시 불필요한 재로그인을 줄인다.
- iOS 도서관 QR은 서버 허용값 확인 전까지 완료로 표시하지 않는다.
