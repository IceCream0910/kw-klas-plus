import AppIntents
import Foundation
import Shared
import WidgetKit

@available(iOS 17.0, *)
struct AcademicCalendarRefreshIntent: AppIntent {
    static var title: LocalizedStringResource = "캘린더 새로고침"
    static var description = IntentDescription("KLAS 일정을 가져와 캘린더 위젯을 갱신합니다.")
    static var openAppWhenRun: Bool = false

    func perform() async throws -> some IntentResult {
        await AcademicCalendarRefreshBridge.refresh()
        return .result()
    }
}

enum AcademicCalendarRefreshBridge {
    static func refresh() async {
        await withCheckedContinuation { continuation in
            let reloader = AcademicCalendarIntentReloader()
            IosAcademicWidgetCalendarRefresh.companion.create(
                dependencies: IosSharedDependencies.companion.createForWidgetExtension(),
                tokenEncryptor: IosRsaLoginTokenEncryptor(),
                reloader: reloader
            ).refresh(userAgent: userAgent) {
                continuation.resume()
            }
        }
    }

    private static var userAgent: String {
        let build = Bundle.main.object(forInfoDictionaryKey: kCFBundleVersionKey as String) as? String ?? "1"
        let digits = build.split { !$0.isNumber }.first.flatMap { Int($0) } ?? 1
        return "Mozilla/5.0 iOSApp_v\(max(digits, 1))"
    }
}

final class AcademicCalendarIntentReloader: AcademicWidgetTimelineReloader {
    func reload() {
        WidgetCenter.shared.reloadTimelines(ofKind: AcademicWidgetKindID.calendar)
    }
}
