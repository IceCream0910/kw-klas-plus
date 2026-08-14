import Shared
import SwiftUI
import UIKit

struct HomeOverlayModifier: ViewModifier {
    @ObservedObject var coordinator: HomeCoordinator

    func body(content: Content) -> some View {
        content
            .background {
                Color.clear
                    .sheet(isPresented: $coordinator.showYearHakgiPicker) {
                        yearHakgiSheet
                    }
            }
            .background {
                Color.clear
                    .sheet(isPresented: $coordinator.showOptionsMenu) {
                        optionsMenuSheet
                    }
            }
            .confirmationDialog("로그아웃", isPresented: $coordinator.showLogoutConfirm, titleVisibility: .visible) {
                Button("확인", role: .destructive) { coordinator.confirmLogout() }
                Button("취소", role: .cancel) {}
            } message: {
                Text("정말 로그아웃할까요?")
            }
            .sheet(isPresented: $coordinator.showDatePicker) {
                NavigationStack {
                    DatePicker(
                        "날짜/시간",
                        selection: $coordinator.datePickerDate,
                        displayedComponents: [.date, .hourAndMinute]
                    )
                    .datePickerStyle(.graphical)
                    .padding()
                    .navigationTitle("날짜 선택")
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) {
                            Button("취소") { coordinator.showDatePicker = false }
                        }
                        ToolbarItem(placement: .confirmationAction) {
                            Button("확인") { coordinator.confirmDatePicker() }
                        }
                    }
                }
                .presentationDetents([.medium, .large])
            }
            .overlay(alignment: .bottom) {
                if let toast = coordinator.toastMessage {
                    Text(toast)
                        .font(.subheadline)
                        .foregroundStyle(KlasTheme.onSurface)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(
                            Capsule(style: .continuous)
                                .fill(KlasTheme.surfaceContainerHigh)
                        )
                        .padding(.bottom, 24)
                        .accessibilityIdentifier("home_toast")
                }
            }
    }

    private var yearHakgiSheet: some View {
        let options = coordinator.yearHakgiList.map { value in
            SelectionBottomSheetOption(
                label: coordinator.homeRuntime.yearHakgiButtonText(value: value),
                action: { coordinator.selectYearHakgi(value) }
            )
        }
        return SelectionBottomSheet(
            title: coordinator.yearHakgiPickerIsUpdate ? "새로운 학기를 찾았어요!" : "학기 선택",
            description: "앱 실행 시 기본적으로 보여질 학기를 선택해주세요.",
            options: options
        )
        .klasSelectionSheetChrome(optionCount: options.count, hasHeader: true)
    }

    private var optionsMenuSheet: some View {
        let options = [
            SelectionBottomSheetOption(label: "광운대학교 공식 앱") {
                coordinator.openExternalAppSearch("광운대학교")
            },
            SelectionBottomSheetOption(label: "중앙도서관 앱") {
                coordinator.openExternalAppSearch("광운대학교 도서관")
            },
            SelectionBottomSheetOption(label: "앱 설정") {
                coordinator.showOptionsMenu = false
                coordinator.openSettings()
            },
            SelectionBottomSheetOption(label: "로그아웃") {
                coordinator.showOptionsMenu = false
                coordinator.showLogoutConfirm = true
            },
        ]
        return SelectionBottomSheet(options: options)
            .klasSelectionSheetChrome(optionCount: options.count, hasHeader: false)
    }
}

struct SelectionBottomSheetOption {
    var label: String
    var action: () -> Void
}

/// Android `SelectionBottomSheetContent` + Material3 Modal BottomSheet 토큰
enum KlasBottomSheetMetrics {
    static let padding: CGFloat = 20
    static let titleSize: CGFloat = 22
    static let titleLineHeight: CGFloat = 28
    static let descriptionSize: CGFloat = 14
    static let descriptionLineHeight: CGFloat = 40
    static let titleToDescription: CGFloat = 4
    static let headerToOptions: CGFloat = 16
    static let optionHeight: CGFloat = 50
    static let cornerRadius: CGFloat = 28
    static let maxWidth: CGFloat = 640

    static func contentHeight(optionCount: Int, hasHeader: Bool) -> CGFloat {
        var height = padding * 2 + CGFloat(optionCount) * optionHeight
        if hasHeader {
            height += titleLineHeight + titleToDescription + descriptionLineHeight + headerToOptions
        }
        return height
    }

    static func detentHeight(optionCount: Int, hasHeader: Bool) -> CGFloat {
        let screenHeight = UIScreen.main.bounds.height
        return min(
            contentHeight(optionCount: optionCount, hasHeader: hasHeader),
            screenHeight * 0.9
        )
    }
}

struct SelectionBottomSheet: View {
    var title: String? = nil
    var description: String? = nil
    var options: [SelectionBottomSheetOption]

    var body: some View {
        let hasHeader = title != nil || description != nil
        let idealHeight = KlasBottomSheetMetrics.contentHeight(
            optionCount: options.count,
            hasHeader: hasHeader
        )
        let maxHeight = UIScreen.main.bounds.height * 0.9
        Group {
            if idealHeight > maxHeight {
                ScrollView {
                    sheetContent
                }
            } else {
                sheetContent
            }
        }
        .background(KlasTheme.surfaceContainerLow)
        .ignoresSafeArea(edges: .bottom)
        .accessibilityIdentifier("selection_bottom_sheet")
    }

    private var sheetContent: some View {
        VStack(alignment: .leading, spacing: 0) {
            if let title {
                Text(title)
                    .font(.system(size: KlasBottomSheetMetrics.titleSize, weight: .bold))
                    .foregroundStyle(KlasTheme.onSurfaceVariant)
            }
            if let description {
                Text(description)
                    .font(.system(size: KlasBottomSheetMetrics.descriptionSize))
                    .foregroundStyle(KlasTheme.onSurfaceVariant)
                    .padding(.top, title == nil ? 0 : KlasBottomSheetMetrics.titleToDescription)
            }
            if title != nil || description != nil {
                Spacer().frame(height: KlasBottomSheetMetrics.headerToOptions)
            }
            ForEach(Array(options.enumerated()), id: \.offset) { index, option in
                Button(action: option.action) {
                    Text(option.label)
                        .font(.system(size: 16))
                        .foregroundStyle(KlasTheme.onSurfaceVariant)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .frame(height: KlasBottomSheetMetrics.optionHeight)
                        .contentShape(Rectangle())
                }
                .buttonStyle(KlasPressHighlightButtonStyle())
                .accessibilityIdentifier("selection_option_\(index)")
            }
        }
        .frame(maxWidth: KlasBottomSheetMetrics.maxWidth, alignment: .leading)
        .padding(KlasBottomSheetMetrics.padding)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private extension View {
    func klasSelectionSheetChrome(optionCount: Int, hasHeader: Bool) -> some View {
        let height = KlasBottomSheetMetrics.detentHeight(optionCount: optionCount, hasHeader: hasHeader)
        return modifier(KlasSelectionSheetChromeModifier(height: height))
    }
}

private struct KlasSelectionSheetChromeModifier: ViewModifier {
    var height: CGFloat

    func body(content: Content) -> some View {
        if #available(iOS 16.4, *) {
            content
                .presentationDetents([.height(height)])
                .presentationDragIndicator(.hidden)
                .presentationCornerRadius(KlasBottomSheetMetrics.cornerRadius)
                .presentationBackground(KlasTheme.surfaceContainerLow)
        } else {
            content
                .presentationDetents([.height(height)])
                .presentationDragIndicator(.hidden)
        }
    }
}
