import Foundation
import Shared
import XCTest
@testable import kw_klas_plus

@MainActor
final class IosVideoHostTests: XCTestCase {
    func testOpenVideoPushesDestinationWhenSessionExists() {
        let coordinator = makeCoordinator()
        defer { coordinator.dispose() }
        coordinator.handleBootstrap(Self.readyHomeResult())

        coordinator.openVideo(subjectId: "SUBJ01", yearSemester: "2026,1")
        XCTAssertEqual(coordinator.path.count, 1)
        XCTAssertNil(coordinator.toastMessage)
    }

    func testOpenVideoWithoutSessionDoesNotPush() {
        let coordinator = makeCoordinator()
        defer { coordinator.dispose() }
        coordinator.openVideo(subjectId: "SUBJ01", yearSemester: "2026,1")
        XCTAssertEqual(coordinator.path.count, 0)
    }

    func testOpenTaskOnlineContentsOpensVideo() {
        let coordinator = makeCoordinator()
        defer { coordinator.dispose() }
        coordinator.handleBootstrap(Self.readyHomeResult())

        coordinator.openTask(
            path: "/std/lis/evltn/OnlineCntntsStdPage.do",
            subjectId: "SUBJ01",
            yearSemester: "2026,1"
        )
        XCTAssertEqual(coordinator.path.count, 1)
        XCTAssertNil(coordinator.toastMessage)
    }

    func testUntrustedVideoUrlShowsToastAndKeepsList() {
        let coordinator = makeCoordinator()
        defer { coordinator.dispose() }
        coordinator.handleBootstrap(Self.readyHomeResult())
        let model = VideoScreenModel(
            subjectId: "SUBJ01",
            yearSemester: "2026,1",
            sessionToken: coordinator.sessionToken,
            coordinator: coordinator
        )

        model.receiveVideoURL("https://evil.example/player")
        XCTAssertEqual(coordinator.toastMessage, "강의 영상 주소를 확인하지 못했습니다.")
        XCTAssertFalse(model.isPlayerVisible)
        XCTAssertFalse(model.showingKlas)
    }

    func testReceivePlayerStatesAndControlsEvaluatePlayerScripts() {
        let coordinator = makeCoordinator()
        defer { coordinator.dispose() }
        coordinator.handleBootstrap(Self.readyHomeResult())
        let model = VideoScreenModel(
            subjectId: "SUBJ01",
            yearSemester: "2026,1",
            sessionToken: coordinator.sessionToken,
            coordinator: coordinator
        )

        model.receivePlayerStates(
            currentTime: "15",
            duration: "100",
            isMuted: "true",
            isPlaying: "true",
            isFullscreen: "false"
        )
        XCTAssertEqual(model.uiState.currentTime, "00:15")
        XCTAssertEqual(model.uiState.totalTime, "01:40")
        XCTAssertEqual(model.uiState.progress, 0.15, accuracy: 0.001)
        XCTAssertTrue(model.uiState.isPlaying)
        XCTAssertTrue(model.uiState.isMuted)

        model.playPause()
        XCTAssertEqual(
            model.lastVideoScriptSource,
            PlayerWebScripts.shared.playback(command: .pause).reveal()
        )

        model.move(.forward)
        XCTAssertTrue(model.lastVideoScriptSource?.contains("_seekLimit") == true)

        XCTAssertTrue(model.uiState.isMuted)
        model.toggleMute()
        XCTAssertFalse(model.uiState.isMuted)
        XCTAssertEqual(
            model.lastVideoScriptSource,
            PlayerWebScripts.shared.mute(muted: false).reveal()
        )
        model.toggleMute()
        XCTAssertTrue(model.uiState.isMuted)
        XCTAssertEqual(
            model.lastVideoScriptSource,
            PlayerWebScripts.shared.mute(muted: true).reveal()
        )

        model.selectSpeed(1.5)
        XCTAssertEqual(model.uiState.speedText, "1.5x")
        XCTAssertEqual(
            model.lastVideoScriptSource,
            PlayerWebScripts.shared.changePlaybackRate(speed: 1.5).reveal()
        )

        model.seekToProgress(0.5)
        XCTAssertEqual(
            model.lastVideoScriptSource,
            PlayerWebScripts.shared.seekTo(seconds: 50).reveal()
        )
    }

    func testRequestOnlineLectureBeforeKlasLoadedShowsToast() {
        let coordinator = makeCoordinator()
        defer { coordinator.dispose() }
        coordinator.handleBootstrap(Self.readyHomeResult())
        let model = VideoScreenModel(
            subjectId: "SUBJ01",
            yearSemester: "2026,1",
            sessionToken: coordinator.sessionToken,
            coordinator: coordinator
        )

        XCTAssertFalse(model.didStart)
        model.requestOnlineLecture(json: "{}")
        XCTAssertEqual(
            coordinator.toastMessage,
            "아직 강의 정보를 불러오는 중이에요. 몇 초 후에 다시 시도해주세요."
        )
        XCTAssertFalse(model.showingKlas)
    }

    func testStartLoadsOnceAndInstallsKlasLocalStorageUserScript() {
        let coordinator = makeCoordinator()
        defer { coordinator.dispose() }
        coordinator.handleBootstrap(Self.readyHomeResult())
        let model = VideoScreenModel(
            subjectId: "SUBJ01",
            yearSemester: "2026,1",
            sessionToken: coordinator.sessionToken,
            coordinator: coordinator
        )
        defer {
            model.listHolder.dispose()
            model.klasHolder.dispose()
            model.videoHolder.dispose()
        }

        XCTAssertFalse(model.didStart)
        let sources = model.klasHolder.webView.configuration.userContentController.userScripts.map(\.source)
        XCTAssertTrue(sources.contains(where: { $0.contains("selectYearhakgi") }))
        XCTAssertTrue(sources.contains(where: { $0.contains("selectSubj") }))
        XCTAssertTrue(sources.contains(where: { $0.contains("window.open") || $0.contains("w.open") }))
        XCTAssertTrue(sources.contains(where: { $0.contains("SUBJ01") }))
        XCTAssertTrue(sources.contains(where: { $0.contains("2026,1") }))

        model.start()
        XCTAssertTrue(model.didStart)
        model.start()
        XCTAssertTrue(model.didStart)
    }

    func testKlasNavigationCallbackAppliesReadyWithoutViewSubscription() {
        let coordinator = makeCoordinator()
        defer { coordinator.dispose() }
        coordinator.handleBootstrap(Self.readyHomeResult())
        let model = VideoScreenModel(
            subjectId: "SUBJ01",
            yearSemester: "2026,1",
            sessionToken: coordinator.sessionToken,
            coordinator: coordinator
        )

        model.klasHolder.onNavigationStateChange?(
            WebNavigationState(loadPhase: .ready(url: "https://klas.kw.ac.kr/std/lis/evltn/OnlineCntntsStdPage.do"))
        )
        model.klasHolder.onNavigationStateChange?(
            WebNavigationState(loadPhase: .ready(url: "https://klas.kw.ac.kr/std/lis/evltn/OnlineCntntsStdPage.do"))
        )

        model.requestOnlineLecture(json: "not-json")
        XCTAssertEqual(coordinator.toastMessage, "강의를 불러오는 중 오류가 발생했습니다.")
        XCTAssertFalse(model.showingKlas)
    }

    func testMalformedOnlineLectureShowsErrorToast() {
        let coordinator = makeCoordinator()
        defer { coordinator.dispose() }
        coordinator.handleBootstrap(Self.readyHomeResult())
        let model = VideoScreenModel(
            subjectId: "SUBJ01",
            yearSemester: "2026,1",
            sessionToken: coordinator.sessionToken,
            coordinator: coordinator
        )
        model.handleKlasNavigation(
            WebNavigationState(loadPhase: .ready(url: "https://klas.kw.ac.kr/std/lis/evltn/OnlineCntntsStdPage.do"))
        )
        model.handleKlasNavigation(
            WebNavigationState(loadPhase: .ready(url: "https://klas.kw.ac.kr/std/lis/evltn/OnlineCntntsStdPage.do"))
        )

        model.requestOnlineLecture(json: "not-json")
        XCTAssertEqual(coordinator.toastMessage, "강의를 불러오는 중 오류가 발생했습니다.")
    }

    func testRequestOnlineLectureSuccessShowsKlasAndEvaluatesScript() {
        let coordinator = makeCoordinator()
        defer { coordinator.dispose() }
        coordinator.handleBootstrap(Self.readyHomeResult())
        let model = VideoScreenModel(
            subjectId: "SUBJ01",
            yearSemester: "2026,1",
            sessionToken: coordinator.sessionToken,
            coordinator: coordinator
        )
        model.handleKlasNavigation(
            WebNavigationState(loadPhase: .ready(url: "https://klas.kw.ac.kr/std/lis/evltn/OnlineCntntsStdPage.do"))
        )
        model.handleKlasNavigation(
            WebNavigationState(loadPhase: .ready(url: "https://klas.kw.ac.kr/std/lis/evltn/OnlineCntntsStdPage.do"))
        )

        let sampleJson = """
        {
            "grcode": "GR01",
            "subj": "SUBJ01",
            "year": "2026",
            "hakgi": "1",
            "bunban": "01",
            "module": "M01",
            "lesson": "L01",
            "oid": "O01",
            "starting": "0",
            "contentsType": "mp4",
            "weeklyseq": 1,
            "weeklysubseq": 1,
            "width": 1280,
            "height": 720,
            "today": "20260902",
            "startdate": "20260901",
            "enddate": "20260908",
            "ptype": "1",
            "lrntime": "60",
            "prog": 50,
            "playtime": "0"
        }
        """
        model.requestOnlineLecture(json: sampleJson)
        XCTAssertTrue(model.showingKlas)
        XCTAssertFalse(model.isPlayerVisible)
        XCTAssertNotNil(model.lastKlasScriptSource)
        XCTAssertTrue(model.lastKlasScriptSource?.contains("lrnCerti.checkCerti") == true || model.lastKlasScriptSource?.contains("appModule.goViewCntnts") == true)
    }

    func testCertificationSuccessAlertTriggersDirectViewerScript() {
        let coordinator = makeCoordinator()
        defer { coordinator.dispose() }
        coordinator.handleBootstrap(Self.readyHomeResult())
        let model = VideoScreenModel(
            subjectId: "SUBJ01",
            yearSemester: "2026,1",
            sessionToken: coordinator.sessionToken,
            coordinator: coordinator
        )
        model.handleKlasNavigation(
            WebNavigationState(loadPhase: .ready(url: "https://klas.kw.ac.kr/std/lis/evltn/OnlineCntntsStdPage.do"))
        )
        model.handleKlasNavigation(
            WebNavigationState(loadPhase: .ready(url: "https://klas.kw.ac.kr/std/lis/evltn/OnlineCntntsStdPage.do"))
        )

        let sampleJson = """
        {
            "grcode": "GR01",
            "subj": "SUBJ01",
            "year": "2026",
            "hakgi": "1",
            "bunban": "01",
            "module": "M01",
            "lesson": "L01",
            "oid": "O01",
            "starting": "0",
            "contentsType": "mp4",
            "weeklyseq": 1,
            "weeklysubseq": 1,
            "width": 1280,
            "height": 720,
            "today": "20260902",
            "startdate": "20260901",
            "enddate": "20260908",
            "ptype": "1",
            "lrntime": "60",
            "prog": 50,
            "playtime": "0"
        }
        """
        model.requestOnlineLecture(json: sampleJson)
        XCTAssertTrue(model.lastKlasScriptSource?.contains("lrnCerti.checkCerti") == true)

        // 본인인증 성공 Alert 발생 시뮬레이션
        model.klasHolder.presentJavaScriptAlert(message: "인증 되었습니다.", completion: {})
        XCTAssertTrue(model.lastKlasScriptSource?.contains("appModule.goViewCntnts") == true)
    }

    func testOpenInKlasHidesPlayer() {
        let coordinator = makeCoordinator()
        defer { coordinator.dispose() }
        coordinator.handleBootstrap(Self.readyHomeResult())
        let model = VideoScreenModel(
            subjectId: "SUBJ01",
            yearSemester: "2026,1",
            sessionToken: coordinator.sessionToken,
            coordinator: coordinator
        )
        model.receiveVideoURL("https://vod.kw.ac.kr/player")
        XCTAssertTrue(model.isPlayerVisible)

        model.openInKLAS()
        XCTAssertFalse(model.isPlayerVisible)
        XCTAssertTrue(model.showingKlas)
    }

    func testReceiveInitSpeedAndLectureProgress() {
        let coordinator = makeCoordinator()
        defer { coordinator.dispose() }
        coordinator.handleBootstrap(Self.readyHomeResult())
        let model = VideoScreenModel(
            subjectId: "SUBJ01",
            yearSemester: "2026,1",
            sessionToken: coordinator.sessionToken,
            coordinator: coordinator
        )

        model.receiveInitSpeed(currentSpeed: "1.25")
        XCTAssertEqual(model.uiState.speedText, "1.25x")
        model.receiveInitSpeed(currentSpeed: "")
        XCTAssertEqual(model.uiState.speedText, "1.0x")

        model.receiveVideoData(
            progress: "<span>진도 40%</span>",
            time: "<span>학습시간 3 분</span>"
        )
        XCTAssertTrue(model.uiState.lectureTime.contains("3"))
        model.seekToLastPlaytime()
        XCTAssertEqual(
            model.lastVideoScriptSource,
            PlayerWebScripts.shared.seekTo(seconds: 180).reveal()
        )
    }

    func testViewerBackReturnsToListAndSecondBackDismisses() {
        let coordinator = makeCoordinator()
        defer { coordinator.dispose() }
        coordinator.handleBootstrap(Self.readyHomeResult())
        let model = VideoScreenModel(
            subjectId: "SUBJ01",
            yearSemester: "2026,1",
            sessionToken: coordinator.sessionToken,
            coordinator: coordinator
        )
        model.receiveVideoURL("https://vod.kw.ac.kr/player")
        XCTAssertTrue(model.isPlayerVisible)

        var dismissed = false
        model.handleBack(dismiss: { dismissed = true })
        XCTAssertFalse(dismissed)
        XCTAssertFalse(model.isPlayerVisible)
        XCTAssertEqual(
            model.lastVideoScriptSource,
            PlayerWebScripts.shared.playback(command: .pause).reveal()
        )

        model.handleBack(dismiss: { dismissed = true })
        XCTAssertTrue(dismissed)
    }

    func testConfirmCloseResetsStateAndHidesPlayer() {
        let coordinator = makeCoordinator()
        defer { coordinator.dispose() }
        coordinator.handleBootstrap(Self.readyHomeResult())
        let model = coordinator.videoModel(subjectId: "SUBJ01", yearSemester: "2026,1")
        model.receiveVideoURL("https://vod.kw.ac.kr/player")
        model.showCloseConfirm = true
        coordinator.isVideoScreenPresented = false
        XCTAssertTrue(model.isPlayerVisible)

        model.confirmClose()
        XCTAssertFalse(model.showCloseConfirm)
        XCTAssertFalse(model.isPlayerVisible)
        XCTAssertEqual(
            model.lastVideoScriptSource,
            PlayerWebScripts.shared.playback(command: .pause).reveal()
        )
        XCTAssertNil(coordinator.activeVideoModel)
    }

    func testKlasHolderJavaScriptAlertPresentsNativeAlertNonBlockingly() {
        let coordinator = makeCoordinator()
        defer { coordinator.dispose() }
        coordinator.handleBootstrap(Self.readyHomeResult())
        let model = VideoScreenModel(
            subjectId: "SUBJ01",
            yearSemester: "2026,1",
            sessionToken: coordinator.sessionToken,
            coordinator: coordinator
        )
        var completionCalled = false
        model.klasHolder.presentJavaScriptAlert(
            message: "학습기간이 아닙니다. 학습 시작일 이후에 학습이 가능합니다.",
            completion: { completionCalled = true }
        )
        XCTAssertTrue(completionCalled)
        XCTAssertEqual(
            coordinator.toastMessage,
            "학습기간이 아닙니다. 학습 시작일 이후에 학습이 가능합니다."
        )
    }

    func testListHolderJavaScriptAlertPresentsNativeAlertNonBlockingly() {
        let coordinator = makeCoordinator()
        defer { coordinator.dispose() }
        coordinator.handleBootstrap(Self.readyHomeResult())
        let model = VideoScreenModel(
            subjectId: "SUBJ01",
            yearSemester: "2026,1",
            sessionToken: coordinator.sessionToken,
            coordinator: coordinator
        )
        var completionCalled = false
        model.listHolder.presentJavaScriptAlert(
            message: "학습 시작일 이전에 강의 영상을 미리 시청할 수 있습니다.",
            completion: { completionCalled = true }
        )
        XCTAssertTrue(completionCalled)
        XCTAssertEqual(
            coordinator.toastMessage,
            "학습 시작일 이전에 강의 영상을 미리 시청할 수 있습니다."
        )
    }

    func testHandleBackDuringPictureInPictureDismissesWithoutDestroyingSession() {
        let coordinator = makeCoordinator()
        defer { coordinator.dispose() }
        coordinator.handleBootstrap(Self.readyHomeResult())
        let model = coordinator.videoModel(subjectId: "SUBJ01", yearSemester: "2026,1")
        model.receiveVideoURL("https://vod.kw.ac.kr/player")
        model.isInPictureInPicture = true
        model.isPlayerVisible = false

        var dismissed = false
        model.handleBack(dismiss: { dismissed = true })

        XCTAssertTrue(dismissed)
        XCTAssertFalse(model.isPlayerVisible)
        XCTAssertEqual(coordinator.activeVideoModel?.subjectId, "SUBJ01")
    }

    func testHandleBackFromPlayerDuringPictureInPictureReturnsToListFirst() {
        let coordinator = makeCoordinator()
        defer { coordinator.dispose() }
        coordinator.handleBootstrap(Self.readyHomeResult())
        let model = coordinator.videoModel(subjectId: "SUBJ01", yearSemester: "2026,1")
        model.receiveVideoURL("https://vod.kw.ac.kr/player")
        model.isInPictureInPicture = true
        model.isPlayerVisible = true

        var dismissed = false
        model.handleBack(dismiss: { dismissed = true })

        XCTAssertFalse(dismissed)
        XCTAssertFalse(model.isPlayerVisible)
        XCTAssertTrue(model.isInPictureInPicture)

        model.handleBack(dismiss: { dismissed = true })
        XCTAssertTrue(dismissed)
    }

    func testShowPlayerFromPipSwitchesToPlayerOverlay() {
        let coordinator = makeCoordinator()
        defer { coordinator.dispose() }
        coordinator.handleBootstrap(Self.readyHomeResult())
        let model = coordinator.videoModel(subjectId: "SUBJ01", yearSemester: "2026,1")
        model.receiveVideoURL("https://vod.kw.ac.kr/player")
        model.startPictureInPicture()
        XCTAssertTrue(model.isInPictureInPicture)
        XCTAssertFalse(model.isPlayerVisible)

        model.showPlayerFromPip()
        XCTAssertTrue(model.isPlayerVisible)
        XCTAssertFalse(model.showingKlas)
    }

    func testOpenOnlineLectureListDuringPictureInPictureGuaranteesListVisibility() {
        let coordinator = makeCoordinator()
        defer { coordinator.dispose() }
        coordinator.handleBootstrap(Self.readyHomeResult())
        let model = coordinator.videoModel(subjectId: "SUBJ01", yearSemester: "2026,1")
        model.receiveVideoURL("https://vod.kw.ac.kr/player")
        model.isInPictureInPicture = true
        model.isPlayerVisible = true

        coordinator.openOnlineLectureList(subjectId: "SUBJ01", yearSemester: "2026,1")

        XCTAssertNotNil(coordinator.activeVideoModel)
        XCTAssertFalse(model.isPlayerVisible)
        XCTAssertTrue(model.isInPictureInPicture)
    }

    func testCoordinatorPreservesAndReusesActiveVideoModel() {
        let coordinator = makeCoordinator()
        defer { coordinator.dispose() }
        coordinator.handleBootstrap(Self.readyHomeResult())
        let model1 = coordinator.videoModel(subjectId: "SUBJ01", yearSemester: "2026,1")
        let model2 = coordinator.videoModel(subjectId: "SUBJ01", yearSemester: "2026,1")
        XCTAssertTrue(model1 === model2)

        model1.isInPictureInPicture = true
        coordinator.clearActiveVideoModelIfIdle()
        XCTAssertNotNil(coordinator.activeVideoModel)

        model1.isInPictureInPicture = false
        coordinator.clearActiveVideoModelIfIdle()
        XCTAssertNil(coordinator.activeVideoModel)
    }

    func testStartPictureInPictureInvokesDismissAndRestorePipNavigatesToVideo() {
        let coordinator = makeCoordinator()
        defer { coordinator.dispose() }
        coordinator.handleBootstrap(Self.readyHomeResult())
        let model = coordinator.videoModel(subjectId: "SUBJ01", yearSemester: "2026,1")
        model.receiveVideoURL("https://vod.kw.ac.kr/player")

        var dismissed = false
        model.startPictureInPicture(dismiss: { dismissed = true })

        XCTAssertTrue(model.isInPictureInPicture)
        XCTAssertFalse(model.isPlayerVisible)
        XCTAssertEqual(
            model.lastVideoScriptSource,
            PlayerWebScripts.shared.enterPictureInPicture().reveal()
        )

        // Simulate restore while user is outside video screen (isVideoScreenPresented = false)
        coordinator.isVideoScreenPresented = false
        model.restorePlayerAfterPictureInPicture()
        XCTAssertFalse(model.isInPictureInPicture)
        XCTAssertTrue(model.isPlayerVisible)

        let expectation = expectation(description: "Restore navigates to video")
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
            if coordinator.path.count > 0 {
                expectation.fulfill()
            }
        }
        wait(for: [expectation], timeout: 1.0)
    }

    func testSystemPipCloseDismissesPlaybackAndCleansUp() {
        let coordinator = makeCoordinator()
        defer { coordinator.dispose() }
        coordinator.handleBootstrap(Self.readyHomeResult())
        let model = coordinator.videoModel(subjectId: "SUBJ01", yearSemester: "2026,1")
        model.receiveVideoURL("https://vod.kw.ac.kr/player")
        model.startPictureInPicture()
        XCTAssertTrue(model.isInPictureInPicture)

        // When system PiP close (X button) is detected:
        model.handlePictureInPictureClosedBySystem()

        XCTAssertFalse(model.isInPictureInPicture)
        XCTAssertFalse(model.isPlayerVisible)
        XCTAssertNil(coordinator.activeVideoModel)
    }

    func testOpenOnlineLectureListCleansUpIdleActiveVideoModel() {
        let coordinator = makeCoordinator()
        defer { coordinator.dispose() }
        coordinator.handleBootstrap(Self.readyHomeResult())
        let model = coordinator.videoModel(subjectId: "SUBJ01", yearSemester: "2026,1")
        model.receiveVideoURL("https://vod.kw.ac.kr/player")
        model.isInPictureInPicture = false
        coordinator.isVideoScreenPresented = false

        // When navigating to online lecture list while idle
        coordinator.openOnlineLectureList(subjectId: "SUBJ01", yearSemester: "2026,1")

        // 1. Previous active model should be cleaned up (nil)
        XCTAssertNil(coordinator.activeVideoModel)
        XCTAssertEqual(coordinator.path.count, 1)

        // 2. When VideoView creates a new model, it should start in clean list mode
        let cleanModel = coordinator.videoModel(subjectId: "SUBJ01", yearSemester: "2026,1")
        XCTAssertFalse(cleanModel.isPlayerVisible)
    }

    func testRequestOnlineLectureSameLectureWhileInPipSwitchesToPlayerDirectly() {
        let coordinator = makeCoordinator()
        defer { coordinator.dispose() }
        coordinator.handleBootstrap(Self.readyHomeResult())
        let model = coordinator.videoModel(subjectId: "SUBJ01", yearSemester: "2026,1")
        model.handleKlasNavigation(
            WebNavigationState(loadPhase: .ready(url: "https://klas.kw.ac.kr/std/lis/evltn/OnlineCntntsStdPage.do"))
        )
        model.handleKlasNavigation(
            WebNavigationState(loadPhase: .ready(url: "https://klas.kw.ac.kr/std/lis/evltn/OnlineCntntsStdPage.do"))
        )

        let sampleJson = """
        {
            "grcode": "GR01",
            "subj": "SUBJ01",
            "year": "2026",
            "hakgi": "1",
            "bunban": "01",
            "module": "M01",
            "lesson": "L01",
            "oid": "O01",
            "starting": "0",
            "contentsType": "mp4",
            "weeklyseq": 1,
            "weeklysubseq": 1,
            "width": 1280,
            "height": 720,
            "today": "20260902",
            "startdate": "20260901",
            "enddate": "20260908",
            "ptype": "1",
            "lrntime": "60",
            "prog": 50,
            "playtime": "0"
        }
        """
        model.requestOnlineLecture(json: sampleJson)
        model.receiveVideoURL("https://vod.kw.ac.kr/player")
        model.startPictureInPicture()
        XCTAssertTrue(model.isInPictureInPicture)
        XCTAssertFalse(model.isPlayerVisible)

        // Requesting the exact same lecture while in PiP
        model.requestOnlineLecture(json: sampleJson)

        // Should switch directly to player without showing raw klas view
        XCTAssertTrue(model.isPlayerVisible)
        XCTAssertFalse(model.showingKlas)
    }

    func testRequestOnlineLectureDifferentLectureWhileInPipResetsPreviousSession() {
        let coordinator = makeCoordinator()
        defer { coordinator.dispose() }
        coordinator.handleBootstrap(Self.readyHomeResult())
        let model = coordinator.videoModel(subjectId: "SUBJ01", yearSemester: "2026,1")
        model.handleKlasNavigation(
            WebNavigationState(loadPhase: .ready(url: "https://klas.kw.ac.kr/std/lis/evltn/OnlineCntntsStdPage.do"))
        )
        model.handleKlasNavigation(
            WebNavigationState(loadPhase: .ready(url: "https://klas.kw.ac.kr/std/lis/evltn/OnlineCntntsStdPage.do"))
        )

        let lecture1 = """
        {
            "grcode": "GR01",
            "subj": "SUBJ01",
            "year": "2026",
            "hakgi": "1",
            "bunban": "01",
            "module": "M01",
            "lesson": "L01",
            "oid": "O01",
            "starting": "0",
            "contentsType": "mp4",
            "weeklyseq": 1,
            "weeklysubseq": 1,
            "width": 1280,
            "height": 720,
            "today": "20260902",
            "startdate": "20260901",
            "enddate": "20260908",
            "ptype": "1",
            "lrntime": "60",
            "prog": 50,
            "playtime": "0"
        }
        """
        model.requestOnlineLecture(json: lecture1)
        model.receiveVideoURL("https://vod.kw.ac.kr/player1")
        model.startPictureInPicture()
        XCTAssertTrue(model.isInPictureInPicture)

        let lecture2 = """
        {
            "grcode": "GR01",
            "subj": "SUBJ01",
            "year": "2026",
            "hakgi": "1",
            "bunban": "01",
            "module": "M02",
            "lesson": "L02",
            "oid": "O02",
            "starting": "0",
            "contentsType": "mp4",
            "weeklyseq": 2,
            "weeklysubseq": 1,
            "width": 1280,
            "height": 720,
            "today": "20260902",
            "startdate": "20260901",
            "enddate": "20260908",
            "ptype": "1",
            "lrntime": "60",
            "prog": 0,
            "playtime": "0"
        }
        """
        var autoDismissed = false
        model.onDismissRequested = { autoDismissed = true }
        coordinator.isVideoScreenPresented = true

        let previousHolder = model.videoHolder
        model.requestOnlineLecture(json: lecture2)

        // Plan A: previous videoHolder is retained as pipVideoHolder, and new videoHolder is created
        XCTAssertNotNil(model.pipVideoHolder)
        XCTAssertTrue(model.pipVideoHolder === previousHolder)
        XCTAssertFalse(model.videoHolder === previousHolder)
        XCTAssertFalse(model.isInPictureInPicture)
        XCTAssertTrue(model.hasActivePip)
        XCTAssertTrue(model.showingKlas)
        XCTAssertFalse(model.isPlayerVisible)
        XCTAssertFalse(autoDismissed, "VideoView should NOT auto-dismiss when selecting a different lecture!")

        // When new video URL arrives, it displays in the player overlay on the main screen
        model.receiveVideoURL("https://vod.kw.ac.kr/player2")
        XCTAssertTrue(model.isPlayerVisible)
        XCTAssertFalse(model.showingKlas)
        XCTAssertTrue(model.hasActivePip)

        // When starting PiP on the new video, the previous PiP holder is cleanly disposed
        model.startPictureInPicture()
        XCTAssertNil(model.pipVideoHolder)
        XCTAssertTrue(model.isInPictureInPicture)
        XCTAssertFalse(model.isPlayerVisible)
    }

    func testRequestOnlineLectureWhileOnViewerPageQueuesScriptAndExecutesOnListReady() {
        let coordinator = makeCoordinator()
        defer { coordinator.dispose() }
        coordinator.handleBootstrap(Self.readyHomeResult())
        let model = coordinator.videoModel(subjectId: "SUBJ01", yearSemester: "2026,1")
        model.handleKlasNavigation(
            WebNavigationState(loadPhase: .ready(url: "https://klas.kw.ac.kr/std/lis/evltn/OnlineCntntsStdPage.do"))
        )
        // Simulate navigation to viewer page for lecture 1
        model.handleKlasNavigation(
            WebNavigationState(loadPhase: .ready(url: "https://klas.kw.ac.kr/spv/lis/lctre/viewer/LctreCntntsViewSpvPage.do"))
        )

        let sampleJson = """
        {
            "grcode": "GR01",
            "subj": "SUBJ01",
            "year": "2026",
            "hakgi": "1",
            "bunban": "01",
            "module": "M01",
            "lesson": "L01",
            "oid": "O01",
            "starting": "0",
            "contentsType": "mp4",
            "weeklyseq": 1,
            "weeklysubseq": 1,
            "width": 1280,
            "height": 720,
            "today": "20260902",
            "startdate": "20260901",
            "enddate": "20260908",
            "ptype": "1",
            "lrntime": "60",
            "prog": 100,
            "playtime": "0"
        }
        """
        model.requestOnlineLecture(json: sampleJson)
        // Script should be queued, not executed on viewer page
        XCTAssertFalse(model.lastKlasScriptSource?.contains("appModule.goViewCntnts") ?? false)

        // When OnlineCntntsStdPage.do finishes loading, pending script executes
        model.handleKlasNavigation(
            WebNavigationState(loadPhase: .ready(url: "https://klas.kw.ac.kr/std/lis/evltn/OnlineCntntsStdPage.do"))
        )
        XCTAssertNotNil(model.lastKlasScriptSource)
        XCTAssertTrue(model.lastKlasScriptSource!.contains("appModule.goViewCntnts"))
    }

    private func makeCoordinator() -> HomeCoordinator {
        let suite = "com.icecream.kwklasplus.test.video.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defaults.removePersistentDomain(forName: suite)
        return HomeCoordinator(
            authRuntime: IosAuthRuntime.companion.create(defaults: defaults),
            onLogout: {}
        )
    }

    private static func readyHomeResult() -> HomeBootstrapResultReady {
        HomeBootstrapResultReady(
            sessionToken: SecretValue.companion.of(value: "session"),
            yearHakgi: "2026,1",
            yearHakgiListJoined: "2026,1",
            timetableJson: "{}",
            deadlineJson: "[]",
            promptYearHakgiChange: false
        )
    }
}
