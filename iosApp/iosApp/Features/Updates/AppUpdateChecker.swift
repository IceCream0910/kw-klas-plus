import Foundation
import SwiftUI

struct AppVersion: Comparable {
    private let parts: [Int]

    init?(_ value: String) {
        let components = value.split(separator: ".", omittingEmptySubsequences: false)
        guard !components.isEmpty, components.allSatisfy({ !$0.isEmpty && $0.allSatisfy { $0.isASCII && $0.isNumber } }) else { return nil }
        let numbers = components.compactMap { Int($0) }
        guard numbers.count == components.count else { return nil }
        parts = numbers
    }

    static func == (lhs: Self, rhs: Self) -> Bool {
        !(lhs < rhs) && !(rhs < lhs)
    }

    static func < (lhs: Self, rhs: Self) -> Bool {
        for index in 0..<max(lhs.parts.count, rhs.parts.count) {
            let left = index < lhs.parts.count ? lhs.parts[index] : 0
            let right = index < rhs.parts.count ? rhs.parts[index] : 0
            if left != right { return left < right }
        }
        return false
    }
}

struct AppStoreVersionClient {
    static let appleID = 6_820_491_776
    static let bundleID = "com.icecream.kwklasplus"
    static let storeURL = URL(string: "itms-apps://itunes.apple.com/app/id6820491776")!
    static let webURL = URL(string: "https://apps.apple.com/app/id6820491776")!
    static let lookupURL = URL(string: "https://itunes.apple.com/lookup?id=6820491776&country=kr&entity=software")!

    private let load: (URLRequest) async throws -> (Data, URLResponse)

    init(load: @escaping (URLRequest) async throws -> (Data, URLResponse) = {
        try await URLSession.shared.data(for: $0)
    }) {
        self.load = load
    }

    func latestVersion() async throws -> String? {
        let request = URLRequest(url: Self.lookupURL, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 10)
        let (data, response) = try await load(request)
        guard let response = response as? HTTPURLResponse, response.statusCode == 200 else {
            throw URLError(.badServerResponse)
        }
        let lookup = try JSONDecoder().decode(Lookup.self, from: data)
        return lookup.results.first {
            $0.trackId == Self.appleID && $0.bundleId == Self.bundleID && $0.wrapperType == "software"
        }?.version
    }

    private struct Lookup: Decodable {
        let results: [App]
    }

    private struct App: Decodable {
        let trackId: Int?
        let bundleId: String?
        let wrapperType: String?
        let version: String?
    }
}

@MainActor
final class AppUpdateChecker: ObservableObject {
    @Published private(set) var availableVersion: String?
    private let installedVersion: String
    private let lookup: () async throws -> String?
    private let now: () -> Date
    private var nextCheck = Date.distantPast
    private var checking = false
    private var dismissedVersions: Set<String> = []

    init(
        installedVersion: String = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "",
        lookup: @escaping () async throws -> String? = { try await AppStoreVersionClient().latestVersion() },
        now: @escaping () -> Date = Date.init
    ) {
        self.installedVersion = installedVersion
        self.lookup = lookup
        self.now = now
    }

    func check() async {
        guard !checking, now() >= nextCheck, let installed = AppVersion(installedVersion) else { return }
        checking = true
        defer { checking = false }
        do {
            let version = try await lookup()
            try Task.checkCancellation()
            nextCheck = now().addingTimeInterval(6 * 60 * 60)
            guard let version, let latest = AppVersion(version), installed < latest,
                  !dismissedVersions.contains(version) else {
                availableVersion = nil
                return
            }
            availableVersion = version
        } catch is CancellationError {
            return
        } catch {
            if !Task.isCancelled { nextCheck = now().addingTimeInterval(15 * 60) }
        }
    }

    func dismiss() {
        if let availableVersion { dismissedVersions.insert(availableVersion) }
        availableVersion = nil
    }
}

struct AppUpdatePrompt: View {
    let enabled: Bool
    @StateObject private var checker = AppUpdateChecker()
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.openURL) private var openURL

    private var canPresent: Bool { enabled && scenePhase == .active }

    var body: some View {
        Color.clear
            .frame(width: 0, height: 0)
            .task(id: canPresent) {
                if canPresent && NSClassFromString("XCTestCase") == nil { await checker.check() }
            }
            .alert("업데이트가 있어요", isPresented: Binding(
                get: { canPresent && checker.availableVersion != nil },
                set: { presented in if !presented && canPresent { checker.dismiss() } }
            )) {
                Button("나중에", role: .cancel) { checker.dismiss() }
                Button("업데이트") {
                    checker.dismiss()
                    openURL(AppStoreVersionClient.storeURL) { accepted in
                        if !accepted { openURL(AppStoreVersionClient.webURL) }
                    }
                }
            } message: {
                Text("새로운 버전이 출시되었어요. 앱스토어에서 최신 버전으로 업데이트해주세요.")
            }
    }
}
