import BackgroundTasks
import CryptoKit
import Foundation
import Shared
import Security
import SwiftUI
import UIKit
import UserNotifications

final class AcademicReminderHost: NSObject, IosReminderHost, UNUserNotificationCenterDelegate {
    static let shared = AcademicReminderHost()
    static let taskId = "com.icecream.kwklasplus.academic-reminders.refresh"
    static let refreshInterval: TimeInterval = 3600
    private let center = UNUserNotificationCenter.current()
    private var root = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        .appendingPathComponent("academic_reminders_v1", isDirectory: true)
    private let namespace = "academic_reminders:"
    private var installed = false
    private var hashKey: Data?
    private var refreshScheduleVersion = 0
    #if DEBUG
    private var debugRefreshTask: IosReminderRefreshTask?
    private var debugBackgroundTask: UIBackgroundTaskIdentifier = .invalid
    private var debugLastPostedId: String?
    #endif

    func install() {
        guard !installed else { return }
        installed = true
        center.delegate = self
        center.getPendingNotificationRequests { requests in
            self.center.removePendingNotificationRequests(withIdentifiers: requests.filter {
                $0.identifier.hasPrefix(self.namespace) && $0.trigger != nil
            }.map(\.identifier))
        }
        IosReminderRuntime.shared.install(host: self, dependencies: IosSharedDependencies.companion.create(defaults: .standard, secureStore: nil, cookieStore: nil), tokenEncryptor: IosRsaLoginTokenEncryptor())
        BGTaskScheduler.shared.register(forTaskWithIdentifier: Self.taskId, using: .main) { task in
            guard let refresh = task as? BGAppRefreshTask else { task.setTaskCompleted(success: false); return }
            self.scheduleRefresh()
            var completed = false
            let run = IosReminderRuntime.shared.refresh { result in
                guard !completed else { return }
                completed = true
                self.logDeliveryState()
                refresh.setTaskCompleted(success: result == "ok")
            }
            refresh.expirationHandler = {
                run.cancel()
                DispatchQueue.main.async {
                    guard !completed else { return }
                    completed = true
                    refresh.setTaskCompleted(success: false)
                }
            }
        }
    }
    func foreground() {
        IosReminderRuntime.shared.foreground()
        scheduleRefresh()
    }
    func isBackground() -> Bool { UIApplication.shared.applicationState == .background }
    #if DEBUG
    func debugInspectNotifications() {
        center.getNotificationSettings { settings in
            NSLog("[DeadlineReminder] display settings: center=%@ alert=%@ lockScreen=%@",
                  String(describing: settings.notificationCenterSetting),
                  String(describing: settings.alertSetting),
                  String(describing: settings.lockScreenSetting))
        }
        center.getDeliveredNotifications { notifications in
            let count = notifications.filter { $0.request.identifier.hasPrefix(self.namespace) }.count
            NSLog("[DeadlineReminder] delivered reminders remaining=%ld", count)
        }
    }
    func debugRefreshInBackground() {
        guard installed, isBackground() else {
            NSLog("[DeadlineReminder] debug refresh skipped: move app to background first")
            return
        }
        guard debugRefreshTask == nil else {
            NSLog("[DeadlineReminder] debug refresh already running")
            return
        }
        debugBackgroundTask = UIApplication.shared.beginBackgroundTask(withName: "DeadlineDebugRefresh") {
            self.debugRefreshTask?.cancel()
            self.finishDebugRefresh()
            NSLog("[DeadlineReminder] debug refresh expired")
        }
        NSLog("[DeadlineReminder] debug refresh started")
        debugRefreshTask = IosReminderRuntime.shared.refresh { result in
            self.finishDebugRefresh()
            self.logDeliveryState()
            NSLog("[DeadlineReminder] debug refresh completed: %@", result)
        }
    }
    private func finishDebugRefresh() {
        debugRefreshTask = nil
        if debugBackgroundTask != .invalid {
            UIApplication.shared.endBackgroundTask(debugBackgroundTask)
            debugBackgroundTask = .invalid
        }
    }
    #endif
    private func logDeliveryState() {
        #if DEBUG
        IosReminderRuntime.shared.readSettings { settings, _ in
            guard let settings else {
                NSLog("[DeadlineReminder] settings unavailable")
                return
            }
            NSLog("[DeadlineReminder] status=%@ enabled=%@ ready=%@ permission=%@",
                  settings.status, String(settings.enabled), String(settings.ready), settings.permission)
        }
        #endif
    }
    func cancelRefresh() {
        refreshScheduleVersion += 1
        BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: Self.taskId)
    }
    func scheduleRefresh() {
        refreshScheduleVersion += 1
        let version = refreshScheduleVersion
        IosReminderRuntime.shared.isEnabled { enabled in
            guard version == self.refreshScheduleVersion else { return }
            guard enabled.boolValue else {
                BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: Self.taskId)
                return
            }
            BGTaskScheduler.shared.getPendingTaskRequests { requests in
                DispatchQueue.main.async {
                    guard version == self.refreshScheduleVersion else { return }
                    let nextDate = Date(timeIntervalSinceNow: Self.refreshInterval)
                    if let pending = requests.first(where: { $0.identifier == Self.taskId }),
                       pending.earliestBeginDate == nil || pending.earliestBeginDate! <= nextDate { return }
                    let request = BGAppRefreshTaskRequest(identifier: Self.taskId)
                    request.earliestBeginDate = nextDate
                    do { try BGTaskScheduler.shared.submit(request) }
                    catch {
                        #if DEBUG
                        NSLog("[DeadlineReminder] background request rejected: %ld", (error as NSError).code)
                        #endif
                    }
                }
            }
        }
    }
    private func prepareRoot() throws {
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true,
            attributes: [.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication])
        var url = root
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try url.setResourceValues(values)
    }
    override init() { super.init() }
    init(root: URL) { self.root = root; super.init() }

    func readLedger() -> IosReminderLedgerRead {
        let url = root.appendingPathComponent("ledger.json")
        do {
            return IosReminderLedgerRead(value: try String(contentsOf: url, encoding: .utf8), success: true)
        } catch let error as NSError {
            if error.domain == NSCocoaErrorDomain && error.code == NSFileReadNoSuchFileError {
                return IosReminderLedgerRead(value: nil, success: true)
            }
            return IosReminderLedgerRead(value: nil, success: false)
        }
    }
    func writeLedger(value: String) -> Bool {
        do {
            try prepareRoot()
            try Data(value.utf8).write(to: root.appendingPathComponent("ledger.json"), options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
            return true
        } catch { return false }
    }
    func hash(value: String) -> String {
        if hashKey == nil {
            let url = root.appendingPathComponent("hash-key")
            if FileManager.default.fileExists(atPath: url.path) {
                hashKey = try? Data(contentsOf: url)
            } else {
                do {
                    try prepareRoot()
                    var bytes = [UInt8](repeating: 0, count: 32)
                    guard SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes) == errSecSuccess else { return "" }
                    let key = Data(bytes)
                    try key.write(to: url, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
                    hashKey = key
                } catch { return "" }
            }
        }
        guard let hashKey, hashKey.count == 32 else { return "" }
        return HMAC<SHA256>.authenticationCode(for: Data(value.utf8), using: SymmetricKey(data: hashKey)).map { String(format: "%02x", $0) }.joined()
    }
    func permission(kind: String, done: @escaping (String) -> Void) {
        center.getNotificationSettings { settings in
            let value: String
            switch settings.authorizationStatus {
            case .authorized: value = settings.alertSetting == .enabled ? "authorized" : "denied"
            case .provisional: value = "provisional"
            case .notDetermined: value = "notDetermined"
            case .ephemeral: value = "ephemeral"
            default: value = "denied"
            }
            DispatchQueue.main.async { done(value) }
        }
    }
    func postDetailed(id: String, kind: String, generation: Int64, additional: Bool, title: String, body: String, done: @escaping (String) -> Void) {
        guard !ReminderTime.shared.isQuietHours(now: Int64(Date().timeIntervalSince1970 * 1000)), isBackground() else {
            done("failed")
            return
        }
        #if DEBUG
        debugLastPostedId = id
        #endif
        center.add(UNNotificationRequest(identifier: namespace + id,
            content: content(generation: generation, title: title, body: body), trigger: nil)) { error in
            #if DEBUG
            NSLog("[DeadlineReminder] OS registration=%@", error == nil ? "accepted" : "failed")
            #endif
            DispatchQueue.main.async { done(error == nil ? "ok" : "failed") }
        }
    }
    private func content(generation: Int64, title: String, body: String) -> UNMutableNotificationContent {
        let c = UNMutableNotificationContent()
        c.title = title
        c.body = body
        c.sound = .default
        c.userInfo = ["kind": "deadline", "generation": generation]
        return c
    }
    func cancel(id: String) {
        #if DEBUG
        NSLog("[DeadlineReminder] cancel requested: matches latest=%@", String(id == debugLastPostedId))
        #endif
        center.removePendingNotificationRequests(withIdentifiers: [namespace + id])
        center.removeDeliveredNotifications(withIdentifiers: [namespace + id])
    }
    func cancelAll(done: @escaping (String) -> Void) {
        #if DEBUG
        NSLog("[DeadlineReminder] cancel all requested")
        #endif
        let group = DispatchGroup()
        group.enter()
        group.enter()
        center.getPendingNotificationRequests { requests in
            self.center.removePendingNotificationRequests(withIdentifiers: requests.filter { $0.identifier.hasPrefix(self.namespace) }.map(\.identifier))
            group.leave()
        }
        center.getDeliveredNotifications { notifications in
            self.center.removeDeliveredNotifications(withIdentifiers: notifications.filter { $0.request.identifier.hasPrefix(self.namespace) }.map { $0.request.identifier })
            group.leave()
        }
        group.notify(queue: .main) { done("ok") }
    }
    func preferencesChanged() { scheduleRefresh() }
    func openDeadlineSettings(attempt: Int64) -> Bool {
        guard let presenter = presenter(), !(presenter is UIHostingController<DeadlineNotificationSettingsView>) else { return false }
        let controller = UIHostingController(rootView: DeadlineNotificationSettingsView(attempt: attempt))
        controller.modalPresentationStyle = .pageSheet
        controller.sheetPresentationController?.detents = [.custom { _ in 256 }]
        controller.rootView.onHeightChange = { [weak controller] height in
            guard let sheet = controller?.sheetPresentationController else { return }
            sheet.animateChanges {
                sheet.detents = [.custom(identifier: .init("deadlineContent")) { context in
                    min(height, context.maximumDetentValue)
                }]
            }
        }
        controller.sheetPresentationController?.prefersGrabberVisible = true
        presenter.present(controller, animated: true)
        return true
    }
    func requestPermission(done: @escaping () -> Void) {
        center.requestAuthorization(options: [.alert, .sound]) { _, _ in
            DispatchQueue.main.async { done() }
        }
    }
    func openSettings() {
        DispatchQueue.main.async { if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) } }
    }
    private func presenter() -> UIViewController? {
        guard let scene = UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }).first(where: { $0.activationState == .foregroundActive }),
              var controller = scene.windows.first(where: \.isKeyWindow)?.rootViewController else { return nil }
        while let presented = controller.presentedViewController { controller = presented }
        return controller
    }
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([])
    }
    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void) {
        completionHandler()
    }

}

#if DEBUG
@objc(KlasDeadlineDebug)
final class KlasDeadlineDebug: NSObject {
    @objc static func inspectNotifications() {
        DispatchQueue.main.async { AcademicReminderHost.shared.debugInspectNotifications() }
    }
    @objc static func refreshInBackground() {
        DispatchQueue.main.async { AcademicReminderHost.shared.debugRefreshInBackground() }
    }
}
#endif

final class ReminderAppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        AcademicReminderHost.shared.install()
        return true
    }
    func applicationDidBecomeActive(_ application: UIApplication) { AcademicReminderHost.shared.foreground() }
    func applicationDidEnterBackground(_ application: UIApplication) { AcademicReminderHost.shared.scheduleRefresh() }
}
