import Shared
import SwiftUI

struct HomeRootView: View {
    @StateObject private var coordinator: HomeCoordinator
    private let onSessionExpired: () -> Void

    init(
        authRuntime: IosAuthRuntime,
        onLogout: @escaping () -> Void,
        onSessionExpired: @escaping () -> Void
    ) {
        _coordinator = StateObject(
            wrappedValue: HomeCoordinator(authRuntime: authRuntime, onLogout: onLogout)
        )
        self.onSessionExpired = onSessionExpired
    }

    var body: some View {
        NavigationStack(path: $coordinator.path) {
            homeContent
                .navigationDestination(for: HomeDestination.self) { destination in
                    pushedScreen(destination)
                }
        }
        .preferredColorScheme(coordinator.colorScheme)
        .tint(KlasTheme.primary)
        .onAppear { coordinator.start() }
        .onDisappear { coordinator.dispose() }
        .homeOverlays(coordinator)
    }

    @ViewBuilder
    private var homeContent: some View {
        switch coordinator.bootstrapPhase {
        case .loading:
            KlasLoadingView(message: "불러오는 중")
        case .ready:
            if let holder = coordinator.homeHolder {
                HomeView(holder: holder, coordinator: coordinator)
            } else {
                KlasLoadingView(message: "불러오는 중")
            }
        case .emptyTerms:
            LinkView(
                url: ProductWebUrls.shared.notReady(),
                sessionToken: coordinator.sessionToken,
                coordinator: coordinator
            )
        case .sessionExpired:
            HomeSessionExpiredView(onExit: onSessionExpired)
        case .failed(let message):
            HomeFailureView(message: message, onRetry: {
                coordinator.reloadHome()
            })
        }
    }

    @ViewBuilder
    private func pushedScreen(_ destination: HomeDestination) -> some View {
        switch destination {
        case let .lecture(subjectId, subjectName, yearSemester):
            LectureView(
                subjectId: subjectId,
                subjectName: subjectName,
                yearSemester: yearSemester,
                sessionToken: coordinator.sessionToken,
                coordinator: coordinator
            )
        case let .boardList(path, title, subjectId, yearSemester):
            BoardView(
                mode: .list(title: title),
                path: path,
                subjectId: subjectId,
                yearSemester: yearSemester,
                sessionToken: coordinator.sessionToken,
                coordinator: coordinator
            )
        case let .boardView(path, boardNumber, masterNumber, subjectId, yearSemester):
            BoardView(
                mode: .view(boardNumber: boardNumber, masterNumber: masterNumber),
                path: path,
                subjectId: subjectId,
                yearSemester: yearSemester,
                sessionToken: coordinator.sessionToken,
                coordinator: coordinator
            )
        case let .task(path, subjectId, yearSemester):
            TaskView(
                path: path,
                subjectId: subjectId,
                yearSemester: yearSemester,
                sessionToken: coordinator.sessionToken,
                coordinator: coordinator
            )
        case let .lecturePlan(subjectId):
            LecturePlanView(
                subjectId: subjectId,
                sessionToken: coordinator.sessionToken,
                coordinator: coordinator
            )
        case let .link(url):
            LinkView(
                url: url,
                sessionToken: coordinator.sessionToken,
                coordinator: coordinator
            )
        case .settings:
            SettingsView(coordinator: coordinator)
        }
    }
}

struct KlasLoadingView: View {
    var message: String

    var body: some View {
        ZStack {
            KlasTheme.background.ignoresSafeArea()
            VStack(spacing: 12) {
                ProgressView()
                    .controlSize(.large)
                    .tint(KlasTheme.primary)
                Text(message)
                    .font(.body)
                    .foregroundStyle(KlasTheme.onBackground)
            }
            .accessibilityElement(children: .combine)
        }
        .accessibilityIdentifier("klas_loading")
    }
}

struct HomeFailureView: View {
    var message: String
    var onRetry: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            Text(message)
                .multilineTextAlignment(.center)
                .foregroundStyle(KlasTheme.onBackground)
            Button("다시 시도", action: onRetry)
                .buttonStyle(KlasInverseButtonStyle())
                .padding(.horizontal, 24)
        }
        .padding()
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(KlasTheme.background.ignoresSafeArea())
    }
}

struct HomeSessionExpiredView: View {
    var onExit: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            Text("인증 오류")
                .font(.title3.weight(.semibold))
            Text("로그인 후 일정 시간이 지나 세션이 만료되었어요. 앱을 재시작하면 정상적으로 정보가 표시될 거예요.")
                .multilineTextAlignment(.center)
                .foregroundStyle(KlasTheme.onSurfaceVariant)
            Button("종료", action: onExit)
                .buttonStyle(KlasInverseButtonStyle())
                .padding(.horizontal, 24)
        }
        .padding()
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(KlasTheme.background.ignoresSafeArea())
    }
}
