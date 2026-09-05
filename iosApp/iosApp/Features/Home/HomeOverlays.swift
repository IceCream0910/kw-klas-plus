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
            .fullScreenCover(
                isPresented: Binding(
                    get: { coordinator.isPresentingQrScanner && coordinator.usesOverlayQrScanner },
                    set: { if !$0 { coordinator.finishQrScan(QrScanResultCancelled()) } }
                )
            ) {
                QrDataScannerView { result in
                    coordinator.finishQrScan(result)
                }
                .ignoresSafeArea()
            }
            .overlay {
                switch coordinator.qrPhase {
                case .preparing:
                    KlasLoadingView(message: "불러오는 중")
                case .authenticating:
                    KlasLoadingView(message: "인증 중")
                        .accessibilityIdentifier("qr_check_in_loading")
                default:
                    EmptyView()
                }
            }
            .alert(
                coordinator.qrAlertTitle,
                isPresented: Binding(
                    get: { coordinator.isQrAlertPresented },
                    set: { if !$0 { coordinator.dismissQrAlert() } }
                )
            ) {
                Button("확인") { coordinator.dismissQrAlert() }
            } message: {
                Text(coordinator.qrAlertMessage)
            }
    }

    private var optionsSheet: some View {
        SelectionBottomSheet(options: [
            SelectionOptionRow(title: "광운대학교 공식 앱") {
                coordinator.openOfficialAppStore()
            },
            SelectionOptionRow(title: "중앙도서관 앱") {
                coordinator.openLibraryAppStore()
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

@MainActor
enum ToastBanner {
    private static var activeToastHosting: UIViewController?
    private static var activeDismissTask: Task<Void, Never>?

    static func show(_ message: String) {
        guard !message.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        guard let windowScene = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .first(where: { $0.activationState == .foregroundActive }) ??
            UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }).first,
            let window = windowScene.windows.first(where: { $0.isKeyWindow }) ?? windowScene.windows.first else {
            return
        }

        activeDismissTask?.cancel()
        activeToastHosting?.view.removeFromSuperview()
        activeToastHosting?.removeFromParent()

        let toastController = UIHostingController(
            rootView: HStack {
                Text(message)
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(KlasTheme.onSurface)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 20)
                    .padding(.vertical, 12)
                    .background(
                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                            .fill(KlasTheme.surfaceContainerHigh)
                            .shadow(color: Color.black.opacity(0.25), radius: 8, x: 0, y: 4)
                    )
            }
        )

        let toastView = toastController.view!
        toastView.backgroundColor = .clear
        toastView.translatesAutoresizingMaskIntoConstraints = false
        toastView.isUserInteractionEnabled = false
        toastView.alpha = 0
        toastView.transform = CGAffineTransform(translationX: 0, y: 20)
        toastView.accessibilityIdentifier = "home_toast"

        window.addSubview(toastView)
        NSLayoutConstraint.activate([
            toastView.centerXAnchor.constraint(equalTo: window.centerXAnchor),
            toastView.bottomAnchor.constraint(equalTo: window.safeAreaLayoutGuide.bottomAnchor, constant: -36),
            toastView.leadingAnchor.constraint(greaterThanOrEqualTo: window.leadingAnchor, constant: 24),
            toastView.trailingAnchor.constraint(lessThanOrEqualTo: window.trailingAnchor, constant: -24),
        ])

        activeToastHosting = toastController

        UIView.animate(withDuration: 0.25, delay: 0, options: [.curveEaseOut, .allowUserInteraction]) {
            toastView.alpha = 1
            toastView.transform = .identity
        }

        activeDismissTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 2_500_000_000)
            guard activeToastHosting === toastController else { return }
            UIView.animate(withDuration: 0.25, animations: {
                toastView.alpha = 0
                toastView.transform = CGAffineTransform(translationX: 0, y: 20)
            }) { _ in
                if activeToastHosting === toastController {
                    toastView.removeFromSuperview()
                    activeToastHosting = nil
                }
            }
        }
    }
}
