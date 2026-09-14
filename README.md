<div align="center">
<h1>
  <img src="https://github.com/user-attachments/assets/f9df5ff6-5d6d-405d-97be-55c58b6573e5" width="50" height="50" align="center" />
  &nbsp;KLAS+
</h1>

KLAS+는 기존 광운대학교 학사포털 KLAS의 모바일 앱을 개선하여, 자주 사용하는 학사 기능을 더 빠르게 확인하고 사용할 수 있도록 모바일에 최적화된 새로운 UI와 편의 기능을 제공합니다.


[주요 기능](#주요-기능) | [프로젝트 구성](#프로젝트-구성) | [프로젝트 빌드](#프로젝트-빌드)

</div>

> [!WARNING]
> KLAS+는 광운대학교의 공식 앱이 아니며, 스마트 인증 우회 및 푸시 알림 등 일부 기능을 지원하지 않으므로 보조 역할로 사용해주시기 바랍니다. 해당 앱을 불법적인 목적으로 사용 시 발생하는 불이익에 대해서 개발자는 어떠한 책임도 지지 않음을 밝힙니다.

[<img src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png?hl=ko" alt="" height="60">](https://play.google.com/store/apps/details?id=com.icecream.kwklasplus)

## 주요 기능

### 학사 정보를 한곳에서

- 오늘의 수업과 전체 시간표 확인
- 미수강 온라인 강의 및 미제출 과제 확인
- 학사 일정과 캘린더
- 학사 공지사항 및 오늘의 학식 메뉴
- 학기 선택 및 학기별 정보 조회
- 홈 화면 시간표·캘린더 위젯

### 강의와 과제

- 수강 과목별 강의 홈
- 공지사항 및 게시글 조회
- 과제·퀴즈·시험 바로가기
- 강의계획서 바로 조회
- 강의 자료 다운로드 및 파일 업로드
- KLAS 메뉴 검색 및 모바일 UI 개선

### 온라인 강의

- 앱 내 온라인 강의 재생
- 재생 위치 이동 및 배속 조절
- 학습 진도 연동
- Picture in Picture(PiP) 지원

### 출석과 학생증

- QR 출석 화면 빠른 진입
- 모바일에 최적화된 QR 스캔
- 학생증 및 중앙도서관 이용증 QR 조회
- 홈 화면 위젯과 딥링크를 통한 빠른 접근

### 성적과 학업 정보

- 학기별 성적 조회
- 성적 추이 시각화
- 석차 및 장학 관련 정보 확인
- KLAS 정보를 기반으로 답변하는 KLAS AI

### 보안과 사용성

- PIN 기반 앱 잠금
- 지문·Face ID 등 생체인증
- 시스템 테마와 연동되는 Light / Dark Mode
- 로그인 및 세션 정보의 안전한 저장
- 외부 링크, 파일 다운로드·업로드 등 네이티브 기능 연동

## 프로젝트 구성
KLAS+는 Android와 iOS를 지원하며, 공통 비즈니스 로직은 Kotlin Multiplatform으로 공유하고 각 플랫폼의 UI와 시스템 기능은 Android Compose와 iOS SwiftUI를 통해 제공합니다.
자세한 내용은 [ARCHITECTURE](docs/ARCHITECTURE.md) 문서를 참고해주세요.

| 경로 | 역할 |
|---|---|
| [`shared`](shared/src) | Kotlin Multiplatform 공통 인증·세션·API·모델·정책·브리지 계약 및 플랫폼별 소형 adapter |
| [`androidApp`](androidApp/src/main) | Jetpack Compose, Android WebView, QR·PIP·위젯 및 OS 연동 |
| [`iosApp`](iosApp/iosApp) | SwiftUI, WKWebView, QR·PIP·WidgetKit 및 iOS 연동 |
| [웹 앱](https://github.com/IceCream0910/kw-klas-plus-webview) | 별도 배포되는 WebView 화면과 `KlasNativeBridge` adapter |

## 프로젝트 빌드

JDK 21과 Android SDK가 필요합니다. Android는 Windows PowerShell에서 `./gradlew` 대신 `./gradlew.bat`를 사용할 수 있습니다.

```sh
./gradlew :androidApp:assembleDebug
./gradlew :shared:testAndroidHostTest :androidApp:testDebugUnitTest
```

iOS 빌드·테스트에는 macOS와 Xcode가 필요합니다. [`iosApp.xcodeproj`](iosApp/iosApp.xcodeproj)를 열고, 실기기 서명은 개인 `Config.local.xcconfig`에 설정하세요.

상세 툴체인, 설정, 플랫폼별 테스트와 PR 절차는 [기여 가이드](CONTRIBUTING.md)를 참고해주세요.

## 기여와 보안

[기여 가이드](CONTRIBUTING.md)에 따라 이슈 또는 PR을 보내주세요. 취약점이나 계정·세션 관련 문제는 공개 이슈에 비밀 값을 남기지 말고 [hey@yuntae.in](mailto:hey@yuntae.in)으로 비공개 제보해 주세요.
