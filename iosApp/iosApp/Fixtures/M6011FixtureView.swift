import SwiftUI

enum M6011UITestConfiguration {
    static let launchArgument = "-m6011-ui-test"

    static var isEnabled: Bool {
        ProcessInfo.processInfo.arguments.contains(launchArgument)
    }

    static var dynamicTypeSize: DynamicTypeSize {
        switch ProcessInfo.processInfo.environment["M6011_DYNAMIC_TYPE"] {
        case "accessibility5":
            return .accessibility5
        case "accessibility3":
            return .accessibility3
        default:
            return .large
        }
    }
}

@MainActor
final class M6011FixtureModel: ObservableObject {
    let holder: WebViewHolder
    @Published var showSelection = false
    @Published var showDownload = false
    @Published var showAlert = false

    init() {
        holder = WebViewHolder()
        holder.loadHTML(Self.html, baseURL: Self.baseURL)
    }

    deinit {
        holder.dispose()
    }

    private static let baseURL = URL(string: "https://klasplus.yuntae.in/m6011-fixture")!

    private static let html = """
    <!doctype html>
    <html>
      <head>
        <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
        <style>
          :root { color-scheme: light dark; }
          * { box-sizing: border-box; }
          html, body { margin: 0; min-height: 100%; font: -apple-system-body; }
          body { padding: 24px 16px 120px; }
          main { min-height: 140vh; }
          h1 { font: -apple-system-title3; }
          input, button { font: -apple-system-body; min-height: 48px; padding: 8px 12px; }
          input { width: 100%; margin: 16px 0; }
          .fixed-nav {
            position: fixed; left: 0; right: 0; bottom: 0;
            min-height: 56px; padding: 8px 16px;
            padding-bottom: max(8px, env(safe-area-inset-bottom));
            background: Canvas; border-top: 1px solid GrayText;
          }
        </style>
      </head>
      <body>
        <main>
          <h1>Web content fixture</h1>
          <p>화면 회전과 키보드 표시 중에도 본문과 하단 내비게이션이 유지됩니다.</p>
          <label for="m6011-input">웹 입력</label>
          <input id="m6011-input" aria-label="웹 입력" type="text" placeholder="입력 후 키보드를 확인하세요">
          <p>스크롤 가능한 Web content</p>
        </main>
        <nav class="fixed-nav" aria-label="웹 하단 내비게이션">웹 하단 내비게이션</nav>
      </body>
    </html>
    """
}

struct M6011FixtureRootView: View {
    @StateObject private var model = M6011FixtureModel()
    @AccessibilityFocusState private var focusedControl: FixtureFocus?

    var body: some View {
        NavigationStack {
            ZStack(alignment: .bottom) {
                WebViewContainer(
                    webView: model.holder.webView,
                    accessibilityIdentifier: "m6011_web_surface"
                )
                .webSurfaceLayout()
                .accessibilityHidden(
                    model.showSelection || model.showAlert || model.showDownload
                )
                controls
                    .padding(.bottom, 8)
                    .zIndex(1)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
                .sheet(isPresented: $model.showSelection) {
                    SelectionBottomSheet(
                        title: "환경 확인",
                        description: "Dynamic Type에서도 모든 항목을 읽을 수 있어야 합니다.",
                        options: [
                            SelectionOptionRow(title: "첫 번째 항목") {
                                model.showSelection = false
                            },
                            SelectionOptionRow(title: "두 번째 항목") {
                                model.showSelection = false
                            },
                        ]
                    )
                }
                .onChange(of: model.showSelection) { isPresented in
                    if !isPresented {
                        DispatchQueue.main.async {
                            focusedControl = .sheetButton
                        }
                    }
                }
                .alert("환경 확인", isPresented: $model.showAlert) {
                    Button("확인", role: .cancel) {}
                } message: {
                    Text("Native overlay가 Web content 위에 한 번만 표시됩니다.")
                }
                .overlay {
                    if model.showDownload {
                        DownloadProgressOverlay(
                            fileName: "fixture.pdf",
                            fraction: 0.5,
                            onCancel: { model.showDownload = false }
                        )
                    }
                }
                .navigationTitle("M6-011 Fixture")
                .navigationBarTitleDisplayMode(.inline)
        }
        .accessibilityIdentifier("m6011_fixture_root")
    }

    private var controls: some View {
        HStack(spacing: 8) {
            Button("시트") { model.showSelection = true }
                .accessibilityIdentifier("m6011_sheet_button")
                .accessibilityFocused($focusedControl, equals: .sheetButton)
            Button("알림") { model.showAlert = true }
                .accessibilityIdentifier("m6011_alert_button")
            Button("다운로드") { model.showDownload = true }
                .accessibilityIdentifier("m6011_download_button")
        }
        .buttonStyle(.bordered)
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .frame(maxWidth: .infinity)
        .background(KlasTheme.surface)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("m6011_native_controls")
    }
}

private enum FixtureFocus: Hashable {
    case sheetButton
}
