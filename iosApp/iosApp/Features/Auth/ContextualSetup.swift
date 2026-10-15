import Shared
import SwiftUI

@MainActor
final class ContextualSetup: ObservableObject {
    enum Suggestion: String, CaseIterable, Identifiable {
        case notifications, library, appLock
        var id: String { rawValue }
        var title: String {
            switch self {
            case .notifications: "마감 알림을 받을까요?"
            case .library: "출입증을 설정할까요?"
            case .appLock: "보안을 강화해보세요."
            }
        }
        var message: String {
            switch self {
            case .notifications: "곧 마감되는 할 일 알림을 보내줄게요."
            case .library: "중앙도서관 출입증 QR을 설정할 수 있어요."
            case .appLock: "PIN과 생체인증으로 앱을 잠글 수 있어요."
            }
        }
        var action: String {
            switch self {
            case .notifications: "알림 켜기"
            case .library: "출입증 설정"
            case .appLock: "앱 잠금 설정"
            }
        }
    }
    struct NotificationAttempt: Identifiable { let id: Int64 }
    @Published private(set) var suggestion: Suggestion?
    @Published var notificationAttempt: NotificationAttempt?
    private let defaults: UserDefaults
    private var pending: [Suggestion] = []
    private static var launchRecorded = false
    static let launchCountKey = "contextual_setup_launch_count"

    init(defaults: UserDefaults = .standard) { self.defaults = defaults }
    static func recordLaunch(defaults: UserDefaults = .standard) {
        if defaults === UserDefaults.standard {
            guard !launchRecorded else { return }
            launchRecorded = true
        }
        defaults.set(min(2, defaults.integer(forKey: launchCountKey) + 1), forKey: launchCountKey)
    }
    var isSecondLaunch: Bool { defaults.integer(forKey: Self.launchCountKey) >= 2 }

    func offer(_ kind: Suggestion, configured: Bool, replaceCurrent: Bool = false) {
        guard !configured, !defaults.bool(forKey: "setup_prompt_seen_\(kind.rawValue)") else { return }
        if !pending.contains(kind) { pending.append(kind) }
        if suggestion == nil || kind == .library || replaceCurrent { suggestion = kind }
    }
    func displayed(_ kind: Suggestion) {
        defaults.set(true, forKey: "setup_prompt_seen_\(kind.rawValue)")
    }
    func dismiss() {
        pending.removeAll { $0 == suggestion }
        suggestion = nil
    }
    func configureNotifications(onFailure: @escaping (String) -> Void) {
        IosReminderRuntime.shared.beginNativeConsent { [weak self] result, error in
            let attempt = result.int64Value
            guard let self else {
                if attempt != 0 { IosReminderRuntime.shared.cancelConsent(attempt: attempt) }
                return
            }
            if attempt != 0 { self.notificationAttempt = NotificationAttempt(id: attempt) }
            else { onFailure(error.isEmpty ? "알림 설정을 시작하지 못했어요. 설정에서 다시 시도해주세요." : error) }
        }
    }
}

struct SetupSuggestionCard: View {
    let kind: ContextualSetup.Suggestion
    let onClose: () -> Void
    let onAction: () -> Void
    var horizontalInset: CGFloat = 16
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    var body: some View {
        Group {
            if dynamicTypeSize.isAccessibilitySize {
                VStack(alignment: .leading, spacing: 4) {
                    details
                    HStack { action; Spacer(); close }
                }
            } else {
                HStack(spacing: 8) { details; Spacer(minLength: 0); action; close }
            }
        }
        .padding(.leading, 16).padding(.trailing, 4).padding(.vertical, 10)
        .background(KlasTheme.surface, in: RoundedRectangle(cornerRadius: 16))
        .overlay { RoundedRectangle(cornerRadius: 16).stroke(KlasTheme.onSurfaceVariant.opacity(0.12), lineWidth: 1) }
        .foregroundStyle(KlasTheme.onBackground)
        .frame(maxWidth: 560)
        .padding(.horizontal, horizontalInset)
        .accessibilityIdentifier("contextual_setup_\(kind.rawValue)")
    }
    private var details: some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(kind.title).font(.subheadline.weight(.medium)).fixedSize(horizontal: false, vertical: true)
            Text(kind.message).font(.caption).foregroundStyle(KlasTheme.onSurfaceVariant)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
    private var action: some View {
        Button(kind.action, action: onAction)
            .font(.subheadline.weight(.medium)).fixedSize(horizontal: true, vertical: false)
            .frame(minHeight: 44).buttonStyle(.plain).foregroundStyle(KlasTheme.primary)
    }
    private var close: some View {
        Button(action: onClose) { Image(systemName: "xmark").font(.caption).frame(width: 44, height: 44) }
            .buttonStyle(.plain).foregroundStyle(KlasTheme.onSurfaceVariant)
            .accessibilityLabel("추천 설정 닫기")
    }
}
