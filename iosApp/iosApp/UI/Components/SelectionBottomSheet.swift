import SwiftUI

struct SelectionOptionRow {
    let title: String
    var isSelected: Bool = false
    let action: () -> Void
}

/// Android `SelectionBottomSheetContent` 패리티.
/// 공식 `.sheet`의 내용 뷰이며, 시트 크롬은 커스텀하지 않는다.
struct SelectionBottomSheet: View {
    var title: String? = nil
    var description: String? = nil
    var options: [SelectionOptionRow]

    @AccessibilityFocusState private var focusedElement: SelectionFocus?
    @State private var contentHeight: CGFloat = 240
    @State private var windowSize: CGSize?

    var body: some View {
        ScrollView {
            sheetBody
                .background {
                    GeometryReader { proxy in
                        Color.clear.preference(
                            key: SheetContentHeightKey.self,
                            value: proxy.size.height
                        )
                    }
                }
        }
        .scrollDisabled(
            contentHeight + SelectionSheetMetrics.grabberAllowance
                <= SelectionSheetMetrics.maxDetent(windowSize: windowSize)
        )
        .background(WindowSizeReader { windowSize = $0 })
        .presentationDetents([
            .height(SelectionSheetMetrics.detentHeight(contentHeight: contentHeight, windowSize: windowSize))
        ])
        .presentationDragIndicator(.visible)
        .modifier(SelectionSheetSurfaceBackground())
        .tint(KlasTheme.primary)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("selection_bottom_sheet")
        .onPreferenceChange(SheetContentHeightKey.self) { contentHeight = $0 }
        .onAppear {
            DispatchQueue.main.async {
                focusedElement = title == nil ? .firstOption : .heading
            }
        }
    }

    private var sheetBody: some View {
        VStack(alignment: .leading, spacing: 0) {
            if let title {
                Text(title)
                    .font(.title2.weight(.bold))
                    .foregroundStyle(KlasTheme.onSurfaceVariant)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .accessibilityAddTraits(.isHeader)
                    .accessibilityFocused($focusedElement, equals: .heading)
            }
            if let description {
                Text(description)
                    .font(.subheadline)
                    .foregroundStyle(KlasTheme.onSurfaceVariant)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.top, title == nil ? 0 : 4)
            }
            if title != nil || description != nil {
                Color.clear.frame(height: 16)
            }
            ForEach(Array(options.enumerated()), id: \.offset) { index, option in
                optionButton(index: index, option: option)
            }
        }
        .padding(20)
        .padding(.top, (title != nil || description != nil) ? 20 : 0)
        .frame(maxWidth: 640)
        .frame(maxWidth: .infinity)
    }

    @ViewBuilder
    private func optionButton(index: Int, option: SelectionOptionRow) -> some View {
        let button = Button(action: option.action) {
            HStack(spacing: 0) {
                Text(option.title)
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .contentShape(Rectangle())
        }
        .buttonStyle(KlasSelectionRowButtonStyle(isSelected: option.isSelected))
        .accessibilityAddTraits(option.isSelected ? .isSelected : [])
        .accessibilityIdentifier("selection_option_\(index)")

        if index == 0 {
            button.accessibilityFocused($focusedElement, equals: .firstOption)
        } else {
            button
        }
    }

}

enum SelectionSheetMetrics {
    static let grabberAllowance: CGFloat = 20

    static func maxDetent(windowSize: CGSize?) -> CGFloat {
        guard let windowSize, windowSize.width > 0, windowSize.height > 0 else {
            return .infinity
        }
        return min(windowSize.width, windowSize.height) * 0.9
    }

    static func detentHeight(contentHeight: CGFloat, windowSize: CGSize?) -> CGFloat {
        min(max(contentHeight + grabberAllowance, 1), maxDetent(windowSize: windowSize))
    }
}

private enum SelectionFocus: Hashable {
    case heading
    case firstOption
}

private struct SelectionSheetSurfaceBackground: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            content.background(KlasTheme.surface)
        } else if #available(iOS 16.4, *) {
            content
                .background(KlasTheme.surface)
                .presentationBackground(KlasTheme.surface)
        } else {
            content.background(KlasTheme.surface)
        }
    }
}

private struct WindowSizeReader: UIViewRepresentable {
    let onChange: (CGSize) -> Void

    func makeUIView(context: Context) -> ReaderView {
        let view = ReaderView()
        view.isUserInteractionEnabled = false
        view.onChange = onChange
        return view
    }

    func updateUIView(_ uiView: ReaderView, context: Context) {
        uiView.onChange = onChange
    }

    final class ReaderView: UIView {
        var onChange: ((CGSize) -> Void)?
        private var reportedSize: CGSize?

        override func didMoveToWindow() {
            super.didMoveToWindow()
            reportWindowSize()
        }

        override func layoutSubviews() {
            super.layoutSubviews()
            reportWindowSize()
        }

        private func reportWindowSize() {
            guard let size = window?.bounds.size, size != reportedSize else { return }
            reportedSize = size
            DispatchQueue.main.async { [onChange] in
                onChange?(size)
            }
        }
    }
}

private struct SheetContentHeightKey: PreferenceKey {
    static var defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = max(value, nextValue())
    }
}
