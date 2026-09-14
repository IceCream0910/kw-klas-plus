# Native 릴리즈 회귀 검증

각 파트 PR에는 Native 기준 커밋과 별도 WebView 저장소의 호환 커밋, 실행한 명령과 결과, 실기기 검증 및 미검증 범위를 기록한다. 이 문서는 검증 순서이며 빌드 성공만으로 완료 처리하지 않는다.

## 자동 검증

| 환경 | 명령 또는 CI 단계 | 확인할 결과 |
|---|---|---|
| Android JVM | `:shared:testAndroidHostTest :androidApp:testDebugUnitTest` | 공통 세션·인증·위젯 정책과 Android 단위 테스트 통과 |
| Android 계측 | CI의 `:androidApp:connectedDebugAndroidTest` | WebView·보안 저장소·Compose 계측 테스트 실제 실행, 실패 시 Android 필수 check 실패 |
| Android release | `:androidApp:assembleRelease` | R8 축소 빌드 성공, 매핑에서 앱 내부 비공개 타입 난독화 확인 |
| iOS | CI의 `:shared:iosSimulatorArm64Test` 및 `iosAppUnitTests` | 공통 iOS 테스트와 앱 프로세스 Keychain 테스트 통과 |

## 실기기·계정 검증

| 플랫폼 | 순서 | 확인할 결과 |
|---|---|---|
| Android | 새 설치 → 로그인 → Home → 앱 잠금 설정·해제 → 학기 변경 → 시간표·캘린더 위젯 추가 → 재시작 → 세션 갱신 → 로그아웃 | 화면·세션·위젯 상태가 기준 앱과 같고 로그아웃 후 SESSION과 위젯 캐시가 복원되지 않음 |
| Android | 위젯 재인증을 시작한 직후 로그아웃 | 늦게 끝난 인증이 보안 저장소 또는 WebView cookie에 SESSION을 다시 쓰지 않음 |
| Android | 기존 배포 앱에서 데이터 유지 업그레이드 | 저장 자격증명·설정·위젯 ID가 유지되고 기존 `kwSESSION` 이전 후 평문 사본이 남지 않음 |
| Android | VPN 연결, 오프라인, 검증되지 않은 네트워크 | VPN에서 정상 진입하고 실제 연결 실패는 재시도 경로로 표시됨 |
| Android | minified release로 로그인·Home·강의·QR·앱 잠금·위젯 실행, 각 WebView 화면 반복 진입·종료 | reflection/리소스 누락 없이 동작하고 반복 종료 후 WebView 메모리 증가가 지속되지 않음 |
| Android | PIN 설정·변경·해제와 위젯 최초 로드·갱신·로그아웃을 실기기에서 반복하며 Main thread trace와 debug StrictMode disk violation 확인 | PIN hash/Keystore 작업과 위젯 `AtomicFile` 읽기·쓰기·삭제가 Main thread에서 실행되지 않고, 로그아웃 후 이전 위젯 데이터가 재등장하지 않음 |
| iOS | 로그인·재시작·세션 만료·로그아웃, Keychain read/write/delete 반복 | 인증 동작을 유지하고 Instruments Leaks/Allocations에서 CF 객체 증가가 지속되지 않음 |

CAPTCHA, 임시 비밀번호, 잘못된 자격증명, 사용자 취소, 일시적 네트워크 실패도 각각 확인한다. 실기기에서만 확인 가능한 항목은 PR에 기기·OS·앱 버전과 결과를 따로 남긴다.
