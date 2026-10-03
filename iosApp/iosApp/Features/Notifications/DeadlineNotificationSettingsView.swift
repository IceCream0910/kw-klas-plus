import Shared
import SwiftUI
import UIKit

struct DeadlineNotificationSettingsView: View {
    let attempt: Int64
    var onCompleted: (() -> Void)? = nil
    var onHeightChange: ((CGFloat) -> Void)? = nil
    @Environment(\.dismiss) private var dismiss
    @State private var busy = false
    @State private var denied = false
    @State private var completed = false
    @State private var error = ""
    @State private var awaitingSettings = false

    @State private var checking = true
    @State private var contentHeight: CGFloat = 200
    @State private var actionsHeight: CGFloat = 50
    private var sheetHeight: CGFloat {
        max(200, contentHeight + (checking ? 0 : actionsHeight + 16) + 56)
    }
    var body: some View {
        VStack(spacing: 16) {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text(completed ? "알림 설정 완료!" : "곧 마감되는 할 일 알림 켜기").font(.title2.bold())
                    if checking {
                        ProgressView()
                        Text("알림 설정을 확인하고 있어요.").font(.footnote)
                    } else {
                        Text(completed ? "앱을 사용하고 있지 않을 때, 주기적으로 24시간 이내에 마감되는 할 일이 있는지 확인해서 알림을 보내줄게요." : "24시간 이내에 마감되는 할 일을 모아 알림으로 받아보세요.").font(.subheadline)
                        if completed {
                            Text("동일한 항목은 하루에 한 번만 발송되며, 기기 상태에 따라 알림이 늦어질 수 있어요.").font(.caption).foregroundStyle(.secondary)
                        } else {
                            if denied { Text("기기 설정에서 KLAS+ 알림을 허용한 뒤 돌아와 주세요.").font(.footnote).foregroundStyle(.red) }
                            if !error.isEmpty { Text(error).font(.footnote).foregroundStyle(.red) }
                        }
                        DeadlineNotificationPreview()
                    }
                }
                .background {
                    GeometryReader { proxy in
                        Color.clear.preference(key: DeadlineSheetContentHeightKey.self, value: proxy.size.height)
                    }
                }
            }
            .frame(maxHeight: contentHeight)
            if !checking {
                VStack(spacing: 0) {
                    if completed {
                        Button("닫기") { dismiss() }
                            .frame(maxWidth: .infinity).buttonStyle(KlasInverseButtonStyle(enabled: true))
                            .accessibilityIdentifier("deadline_settings_close")
                    } else {
                        Button {
                            if denied { awaitingSettings = true; AcademicReminderHost.shared.openSettings() }
                            else { requestPermission() }
                        } label: { Text(denied ? "시스템 설정으로 이동" : "권한 허용하기").frame(maxWidth: .infinity) }
                        .buttonStyle(KlasInverseButtonStyle(enabled: !busy)).disabled(busy)
                        Button("나중에") { dismiss() }.frame(maxWidth: .infinity).padding(.top, 16).disabled(busy)
                    }
                }
                .fixedSize(horizontal: false, vertical: true)
                .background {
                    GeometryReader { proxy in
                        Color.clear.preference(key: DeadlineSheetActionsHeightKey.self, value: proxy.size.height)
                    }
                }
            }
        }
        .padding(28)
        .background { ZStack(alignment: .top) { KlasTheme.background; FunnelGlow() } }
        .presentationDetents([.height(sheetHeight)])
        .onPreferenceChange(DeadlineSheetContentHeightKey.self) { contentHeight = $0 }
        .onPreferenceChange(DeadlineSheetActionsHeightKey.self) { actionsHeight = $0 }
        .onChange(of: sheetHeight) { onHeightChange?($0) }
        .presentationDragIndicator(.visible)
        .interactiveDismissDisabled(busy || checking)
        .onAppear {
            onHeightChange?(sheetHeight)
            guard !completed else { checking = false; return }
            AcademicReminderHost.shared.permission(kind: "deadline") { permission in
                if permission == "authorized" || permission == "provisional" { complete() }
                else { checking = false; denied = permission == "denied" }
            }
        }
        .onDisappear { IosReminderRuntime.shared.cancelConsent(attempt: attempt) }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.didBecomeActiveNotification)) { _ in
            if awaitingSettings { awaitingSettings = false; complete() }
        }
    }
    private func requestPermission() {
        busy = true; error = ""
        AcademicReminderHost.shared.permission(kind: "deadline") { permission in
            if permission == "authorized" || permission == "provisional" { complete() }
            else if permission == "notDetermined" {
                AcademicReminderHost.shared.requestPermission { complete() }
            } else { denied = true; busy = false }
        }
    }
    private func complete() {
        busy = true
        IosReminderRuntime.shared.completeConsent(attempt: attempt) { status in
            busy = false
            checking = false
            switch status {
            case "COMPLETED":
                withAnimation { completed = true }
                AcademicReminderHost.shared.scheduleRefresh()
                onCompleted?()

            case "PERMISSION_DENIED": denied = true
            case "STORAGE_FAILED": denied = false; error = "알림 설정을 저장하지 못했어요. 다시 시도해 주세요."
            default: error = "설정 요청이 취소되었어요. 시트를 닫고 다시 켜 주세요."
            }
        }
    }
}

private struct DeadlineSheetContentHeightKey: SwiftUI.PreferenceKey {
    static var defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) { value = nextValue() }
}

private struct DeadlineSheetActionsHeightKey: SwiftUI.PreferenceKey {
    static var defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) { value = nextValue() }
}
