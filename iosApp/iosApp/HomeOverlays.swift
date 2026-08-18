import Shared
import SwiftUI

struct HomeOverlayModifier: ViewModifier {
    @ObservedObject var coordinator: HomeCoordinator
    @State private var datePickerDetent: PresentationDetent = .large

    func body(content: Content) -> some View {
        content
            .background {
                Color.clear
                    .sheet(isPresented: $coordinator.showYearHakgiPicker) {
                        yearHakgiSheet
                            .preferredColorScheme(coordinator.colorScheme)
                    }
            }
            .background {
                Color.clear
                    .sheet(isPresented: $coordinator.showOptionsMenu) {
                        optionsSheet
                            .preferredColorScheme(coordinator.colorScheme)
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
                .presentationDetents(
                    [.medium, .large],
                    selection: $datePickerDetent
                )
                .onAppear { datePickerDetent = .large }
                .preferredColorScheme(coordinator.colorScheme)
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

    private var optionsSheet: some View {
        SelectionBottomSheet(options: [
            SelectionOptionRow(title: "광운대학교 공식 앱") {
                coordinator.openExternalAppSearch("광운대학교")
            },
            SelectionOptionRow(title: "중앙도서관 앱") {
                coordinator.openExternalAppSearch("광운대학교 도서관")
            },
            SelectionOptionRow(title: "앱 설정") {
                coordinator.openSettings()
            },
            SelectionOptionRow(title: "로그아웃") {
                coordinator.presentLogoutConfirm()
            },
        ])
    }

    private var yearHakgiSheet: some View {
        SelectionBottomSheet(
            title: coordinator.yearHakgiPickerIsUpdate ? "새로운 학기를 찾았어요!" : "학기 선택",
            description: "앱 실행 시 기본적으로 보여질 학기를 선택해주세요.",
            options: coordinator.yearHakgiList.map { value in
                SelectionOptionRow(
                    title: coordinator.homeRuntime.yearHakgiButtonText(value: value),
                    isSelected: value == coordinator.yearHakgi
                ) {
                    coordinator.selectYearHakgi(value)
                }
            }
        )
    }
}
