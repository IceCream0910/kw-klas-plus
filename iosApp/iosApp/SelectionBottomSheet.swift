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
        .scrollDisabled(contentHeight + grabberAllowance <= maxDetent)
        .background(KlasTheme.surface)
        .presentationDetents([.height(detentHeight)])
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
            Text(option.title)
        }
        .buttonStyle(KlasSelectionRowButtonStyle())
        .accessibilityAddTraits(option.isSelected ? .isSelected : [])
        .accessibilityIdentifier("selection_option_\(index)")

        if index == 0 {
            button.accessibilityFocused($focusedElement, equals: .firstOption)
        } else {
            button
        }
    }

    private var grabberAllowance: CGFloat { 20 }

    private var maxDetent: CGFloat {
        min(UIScreen.main.bounds.width, UIScreen.main.bounds.height) * 0.9
    }

    private var detentHeight: CGFloat {
        min(max(contentHeight + grabberAllowance, 1), maxDetent)
    }

}

private enum SelectionFocus: Hashable {
    case heading
    case firstOption
}

private struct SelectionSheetSurfaceBackground: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 16.4, *) {
            content.presentationBackground(KlasTheme.surface)
        } else {
            content
        }
    }
}

private struct SheetContentHeightKey: PreferenceKey {
    static var defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = max(value, nextValue())
    }
}
