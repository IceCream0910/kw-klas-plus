# ADR-010: iOS Liquid Glass 적용 범위

- 상태: Accepted; 실기기 검증 대기
- 기준 Native: `60725cc9084361813e998008d67a5e7e5bd560aa`
- 기준 WebView: 변경 없음 (브리지 계약 영향 없음)
- 이슈: [#80](https://github.com/IceCream0910/kw-klas-plus/issues/80)

## 배경과 결정

iOS 27 SDK로 빌드하면 `UIDesignRequiresCompatibility`가 무시되어 표준 탭 바·툴바·시트·alert·컨트롤은 Liquid Glass로 그려집니다. 배포 타깃 iOS 16.0은 유지하고, iOS 26 이상에서만 glass를 적용하며 iOS 16~18은 기존 화면을 그대로 둡니다. Android 화면은 바꾸지 않으며, iOS 26 이상의 머티리얼 차이는 승인된 플랫폼 차이로 둡니다.

버전 분기는 `iosApp/iosApp/UI/Theme/KlasGlass.swift`에 모읍니다.

- `klasFloatingSurface(cornerRadius:)`: iOS 26 이상은 `glassEffect(.regular, in:)`, 그 미만은 기존 `.regularMaterial`을 씁니다. 콘텐츠 위에 떠 있는 새로고침 오버레이(`HomeReloadOverlay`)와 다운로드 오버레이(`DownloadProgressOverlay`)에만 적용해 iOS 27 Liquid Glass 투명도 설정을 따르게 합니다. 패널 뒤의 scrim은 glass 위에 덧칠하는 색이 아니므로 유지합니다.
- `ToolbarItemPlacement.klasPinnedTrailing`: iOS 27 이상은 `topBarPinnedTrailing`, 그 미만은 `topBarTrailing`입니다. Xcode 26.x SDK에는 이 API가 없어 `#if compiler(>=6.4)`로 감쌉니다([ADR-003](ADR-003-min-platform-versions.md)).

`PushedWebStack`과 `LectureView`의 PDF 공유 버튼은 `ToolbarItem` 안의 `if`가 아니라 조건부 `ToolbarItem`으로 바꿔, iOS 26 이상에서 빈 glass 버튼이 남지 않게 합니다. `pdf_share_button` 식별자는 유지합니다.

`SelectionBottomSheet`는 iOS 26 이상에서 `presentationBackground(KlasTheme.surface)`만 제거해 시트 크롬의 Liquid Glass를 사용합니다. `.background(KlasTheme.surface)`는 시트 내부 콘텐츠 배경으로 유지합니다. iOS 16.4~18은 `presentationBackground`와 콘텐츠 배경을 모두 유지하고, 그 미만은 콘텐츠 배경만 둡니다. 시트 최대 높이는 `UIScreen.main` 대신 시트가 붙은 창의 크기로 계산해 iOS 27 창 크기 조절에서도 창보다 커지지 않게 합니다. 창 크기를 아직 모르면 콘텐츠 높이를 그대로 사용합니다. 계산은 `SelectionSheetMetrics`가 맡고 `SelectionSheetMetricsTests`가 고정합니다.

홈 탭 바는 [ADR-007](ADR-007-native-home-navigation.md)의 `homeSystemTabBarPlacement()`를 유지하되 `.frame(height: 60)`은 제거합니다. iOS 26 이상은 플로팅 탭 바 높이를 시스템이 정합니다. iOS 26 미만은 `UITabBar` 고유 높이를 쓰고, 배경만 `.bar`로 홈 인디케이터까지 이어집니다. 이 변경은 iPad sidebar adaptation을 제공하지 않습니다.

## 적용하지 않은 항목

- 홈 탭 바의 `TabView`/`UITabBarController` 전환: 시스템 탭 컨테이너는 각 탭의 콘텐츠를 소유해야 하고, `WKWebView`는 superview를 하나만 가질 수 있습니다. 단일 웹뷰를 탭 밖에 두면 스크롤 최소화와 iPad sidebar를 표준 경로로 얻기 어렵습니다. [ADR-007](ADR-007-native-home-navigation.md)대로 웹뷰는 한 `HomeView`에 고정하고, 탭 바 이전은 별도 작업으로 둡니다.
- 툴바 배경: `.toolbarBackground(.hidden)`과 `webSurfaceTopBackground()` 조합에서는 WebView가 툴바 아래에서 시작하고 툴바 영역은 단색이라 scroll edge effect와 겹치지 않습니다. 스크롤 주체가 `WKWebView` 내부 `UIScrollView`라 `toolbarMinimizationBehavior`도 쓰지 않습니다.
- confirmationDialog 위치: 로그아웃은 옵션 시트가 닫힌 뒤, 강의 전환은 PiP 중 웹 요청에서 열려 기준으로 삼을 네이티브 버튼이 없습니다. 강의 종료는 `VideoPlayerOverlay`의 Android 패리티 콜백 구조를 바꿔야 해 화면 루트 위치를 유지합니다.
- 버튼: 로그인 퍼널의 플로팅 뒤로가기는 `.buttonStyle(.glass)`를 유지합니다. 내비게이션 툴바의 뒤로가기·공유 버튼은 시스템 툴바 스타일을 사용합니다. 로그인·저장·확인 CTA는 `KlasInverseButtonStyle`을 유지합니다.
- `VideoPlayerOverlay`: 재생·이동·PIP·배속 버튼은 플레이어 컨트롤 패널의 surface 배경을 유지합니다. `Slider`는 시스템 컨트롤의 Liquid Glass와 대비만 확인합니다.
- 로그인 `Toggle`은 `KlasCheckboxToggleStyle`로 그린 커스텀 체크박스라 glass 적용 대상이 아니며 바꾸지 않습니다. `ProgressView`는 기존 tint와 주변 배경의 대비만 확인합니다.
- 도서관 QR 시트는 `presentationBackground` 없이 콘텐츠 `.background(KlasTheme.surface)`만 사용합니다. 배경 변경은 실기기에서 QR 대비와 quiet zone을 비교한 뒤 결정합니다.
- 학사 시간표·캘린더 위젯과 도서관 QR 위젯의 Clear/Tinted 렌더링은 별도 이슈로 두고, 이번 변경은 위젯 코드를 바꾸지 않습니다.
- 앱 아이콘 Icon Composer 전환은 디자인 리소스가 필요해 후속 작업으로 둡니다.

## 롤백

이 변경의 커밋을 되돌리면 기존 `.regularMaterial`·`topBarTrailing`·고정 탭 바 높이·`presentationBackground` 시트 배경·`UIScreen` 기준 시트 높이로 돌아갑니다. iOS 26 미만 탭 바 배경 연장은 ADR-007을 따릅니다. 부분 롤백이 필요하면 `KlasGlass.swift`와 `SelectionSheetSurfaceBackground`의 iOS 26 분기, `HomeSystemTabBarPlacement`의 높이 제거만 기존 경로로 바꿉니다. 저장 키·세션·브리지는 바뀌지 않습니다.

## 검증

로컬 Xcode 27.0(27A266a)에서 `:shared:iosSimulatorArm64Test`와 iOS 27.0 시뮬레이터의 `iosAppTests` 189개가 통과했습니다. iOS 27.0·26.5 시뮬레이터에서 Light/Dark로 툴바 glass 버튼·다운로드 오버레이·스크롤 중 툴바를 UI 테스트 fixture로 확인했습니다. 탭 바 높이 제거와 시트 콘텐츠 배경 유지 후의 iPhone·iPad·Dynamic Type·분할 화면 레이아웃, Xcode 26.5 빌드, 로그인한 실제 화면, iOS 27 Liquid Glass 투명도 슬라이더, 투명도 줄이기·대비 증가·동작 줄이기, iOS 16~18 회귀는 수동 검증 대상입니다. 위젯 Clear/Tinted와 도서관 QR 시트 배경은 이번 범위가 아닙니다.
