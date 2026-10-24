# ADR-016: 학사 위젯 Clear/Tinted 렌더링

- 상태: 구현; 실제 홈 화면·실기기 검증 대기
- 이슈: [#81](https://github.com/IceCream0910/kw-klas-plus/issues/81)
- Native 기준: `9046ddd193d01ad230834891e33a659f94b3c74f` (`main`)
- WebView 호환 기준: `20a614509ce610d669b63bed54d559cf55f8043a` (로컬 확인 버전); 웹 변경·동시 배포 불필요

## 결정

시간표와 캘린더는 `widgetRenderingMode == .accented`를 확인한다. Clear/Tinted는 별도의 OS 버전 분기가 아니라 WidgetKit이 전달하는 렌더링 모드로 대응한다. 따라서 iOS 18의 Tinted에도 같은 정책을 적용하며 iOS 16 최소 지원은 유지한다. [Apple 렌더링 안내](https://developer.apple.com/documentation/widgetkit/optimizing-your-widget-for-accented-rendering-mode-and-liquid-glass)를 따른다.

- iOS 17 이상은 Light/Dark 배경을 `containerBackground(for: .widget)`에만 그린다. 시스템이 컨테이너를 제거·대체할 수 있도록 콘텐츠의 불투명 배경을 제거한다. iOS 16은 기존 콘텐츠 배경을 유지한다.
- full-color는 기존 팔레트·글자 크기·배치·카드 모서리를 유지한다.
- accented의 글자는 흰색 원본을 시스템이 처리하도록 한다. 제목·시간·일정·수업 상태는 기본 그룹, 일정 마커·카드 윤곽선·오늘 날짜 윤곽선·새로고침 아이콘은 `widgetAccentable()` 그룹이다. 부모 전체에는 강조를 적용하지 않아 내부 글자까지 강조 그룹에 편입되지 않게 한다.
- accented의 일정 bar·수업 카드는 색 채움 대신 윤곽선을 사용한다. 같은 색으로 변환된 채움과 글자가 겹치지 않게 하며, 오늘은 굵은 숫자·윤곽선·접근성의 “오늘” 설명으로 구분한다. 기존 “예정/수업 중/종료” 문구도 유지한다.
- 자체 glass 효과·추가 반투명 카드·이미지 full-color 우회는 추가하지 않는다. 도서관 QR 위젯은 기존 렌더링을 유지한다.

Android·인증·Keychain·App Group 파일·데이터 DTO·타임라인·새로고침 Intent·딥링크·브리지 계약은 변경하지 않는다. 보안 경계와 기존 위젯의 동작은 ADR-005/006을 따른다.

## 검증 (2026-10-24)

Xcode 27.1 (27A9275), iOS 27.0 시뮬레이터에서 확인했다.

- `xcodebuild -project iosApp/iosApp.xcodeproj -target widgetExtension -configuration Debug -sdk iphonesimulator ... build`: 성공. Release도 동일한 명령의 configuration을 바꿔 성공했다. 최소 지원 버전은 iOS 16이다.
- 로컬 임시 SwiftUI fixture 앱에서 small/medium/large/extraLarge × Light/Dark × fullColor/accented × 8개 데이터 상태를 렌더링했다. 정상 캘린더·시간표, 빈 일정·수업, 데이터 누락·손상, 로그인 필요·조회 재시도 총 128개가 생성됐다.
- fixture는 저장소의 실제 View·지원 코드·DTO·Store를 복사해 사용하고 읽기 전용 `widgetFamily`만 주입 가능한 fixture 환경 키로 치환했다. `widgetRenderingMode`는 실제 환경 값을 주입했다. 갱신 Intent는 네트워크 호출 없는 stub이다. 실 계정·App Group 데이터에는 접근하지 않았다.
- 기준 main의 동일한 fixture와 full-color 64개를 비교했다. 56개는 픽셀이 같고, 8개는 카드 경계의 작은 렌더링 차이가 있었다. 전체 32,189,312 픽셀 중 2,524개(0.00785%), 최대 채널 차이 12/255였다. 배치·색상 정책 변경은 없으며 대표 이미지로 확인했다.
- accented의 배경 모서리 alpha=0, 카드 내부 투명·윤곽선, 오늘 표시와 수업 상태 문구, 정상·오류 placeholder를 대표 이미지에서 확인했다. 이는 콘텐츠 분기 검증이며 시스템의 실제 색 변환·Liquid Glass 검증은 아니다.

임시 증거 경로는 `/private/tmp/klas-widget-81-fixture/`의 `Fixture.swift`, `build.sh`, `compare.py`, `baseline-renders/`, `current-renders/`다. 빌드 로그는 `/private/tmp/klas-widget-81-{build,release-build}.log`에 있다. Shared 코드는 기준 커밋과 동일하여 기존 로컬 simulator framework를 재사용하고 `OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED=YES`로 Gradle 호출을 생략했다.

## 출시 전 남은 검증

실제 WidgetKit 호스트에서 iOS 26+ Clear Light/Dark와 여러 Tinted 색·밝은/어두운 배경화면, iOS 18 Tinted, iOS 16~18 일반 Light/Dark, iPhone/iPad의 각 family, 접근성 대비 증가·투명도 줄이기·Dynamic Type를 확인한다. 새로고침 버튼의 실제 Intent 실행, 위젯 터치 후 앱의 잠금·로그인 gate 및 원래 탭 이동은 이번 fixture에서 실행하지 않았다. 계정이 있는 시뮬레이터에 무서명 앱을 설치하지 않았으며, 실기기 확인 전에는 기능 완료·출시 검증 통과로 간주하지 않는다.

## 롤백

이 변경 커밋을 되돌리면 기존 콘텐츠 배경·채움 카드로 돌아간다. 저장 데이터 이전이나 웹 배포는 필요하지 않다. 기존 full-color 사용자 데이터·브리지와 호환된다.
