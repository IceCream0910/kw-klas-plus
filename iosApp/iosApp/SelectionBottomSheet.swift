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

    @State private var contentHeight: CGFloat = 240

    var body: some View {
        ScrollView {
            sheetBody
                .background {
                    GeometryReader { proxy in
                        Color.clear.preference(key: SheetContentHeightKey.self, value: proxy.size.height)
                    }
                }
        }
        .scrollDisabled(contentHeight + grabberAllowance <= maxDetent)
        .background(KlasTheme.surface)
        .presentationDetents([.height(detentHeight)])
        .presentationDragIndicator(.visible)
        .modifier(SelectionSheetSurfaceBackground())
        .onPreferenceChange(SheetContentHeightKey.self) { contentHeight = $0 }
        .tint(KlasTheme.primary)
    }

    private var sheetBody: some View {
        VStack(alignment: .leading, spacing: 0) {
            if let title {
                Text(title)
                    .font(.system(size: 22, weight: .bold))
                    .foregroundStyle(KlasTheme.onSurfaceVariant)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .accessibilityAddTraits(.isHeader)
            }
            if let description {
                Text(description)
                    .font(.system(size: 14))
                    .foregroundStyle(KlasTheme.onSurfaceVariant)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.top, title == nil ? 0 : 4)
            }
            if title != nil || description != nil {
                Color.clear.frame(height: 16)
            }
            ForEach(Array(options.enumerated()), id: \.offset) { index, option in
                Button(action: option.action) {
                    Text(option.title)
                }
                .buttonStyle(KlasSelectionRowButtonStyle())
                .accessibilityAddTraits(option.isSelected ? .isSelected : [])
                .accessibilityIdentifier("selection_option_\(index)")
            }
        }
        .padding(20)
        .padding(.top, (title != nil || description != nil) ? 20 : 0)
        .frame(maxWidth: 640)
        .frame(maxWidth: .infinity)
        .accessibilityIdentifier("selection_bottom_sheet")
    }

    private var grabberAllowance: CGFloat { 20 }

    private var maxDetent: CGFloat {
        min(UIScreen.main.bounds.width, UIScreen.main.bounds.height) * 0.9
    }

    private var detentHeight: CGFloat {
        min(max(contentHeight + grabberAllowance, 1), maxDetent)
    }
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
