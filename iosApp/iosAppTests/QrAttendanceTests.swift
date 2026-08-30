import AVFoundation
import Foundation
import Shared
import UIKit
import XCTest
@testable import kw_klas_plus

@MainActor
final class QrAttendanceTests: XCTestCase {
    func testSuccessfulCheckInShowsResultAlert() async {
        let payload = Self.samplePayload()
        let scanner = FakeQrScanPresenter(result: QrScanResultSuccess(value: "scanned"))
        let coordinator = makeCoordinator(
            scanner: scanner,
            prepare: { _, _ in QrPreparationResultSuccess(payload: payload) },
            checkIn: { _, _, _ in QrCheckInResultSuccess.shared }
        )
        defer { coordinator.dispose() }
        coordinator.handleBootstrap(Self.readyHomeResult())

        coordinator.startQrCheckIn(
            subjectId: "subj",
            subjectName: "공통 테스트",
            yearHakgi: "2026,1",
            requireParsedTerm: false
        )
        await waitUntil { coordinator.isQrAlertPresented }

        XCTAssertEqual(coordinator.qrAlertTitle, "출석 체크 성공")
        XCTAssertEqual(coordinator.qrAlertMessage, "정상적으로 출석 처리 되었습니다.")
        XCTAssertEqual(scanner.scanCount, 1)
        coordinator.dismissQrAlert()
        XCTAssertEqual(coordinator.qrPhase, .idle)
    }

    func testCancelledScanLeavesIdleWithoutAlert() async {
        let payload = Self.samplePayload()
        let scanner = FakeQrScanPresenter(result: QrScanResultCancelled())
        let coordinator = makeCoordinator(
            scanner: scanner,
            prepare: { _, _ in QrPreparationResultSuccess(payload: payload) },
            checkIn: { _, _, _ in
                XCTFail("check-in must not run after cancel")
                return QrCheckInResultSuccess.shared
            }
        )
        defer { coordinator.dispose() }
        coordinator.handleBootstrap(Self.readyHomeResult())

        coordinator.startQrCheckIn(
            subjectId: "subj",
            subjectName: "공통 테스트",
            yearHakgi: "2026,1",
            requireParsedTerm: false
        )
        await waitUntil { scanner.scanCount == 1 && coordinator.qrPhase == .idle }

        XCTAssertFalse(coordinator.isQrAlertPresented)
        XCTAssertNil(coordinator.toastMessage)
        XCTAssertEqual(coordinator.qrPhase, .idle)
        XCTAssertNotNil(coordinator.homeHolder)
    }

    func testCancelFromLectureKeepsLectureWhenCoveredViewWouldDispose() async {
        let payload = Self.samplePayload()
        let scanner = FakeQrScanPresenter(result: QrScanResultCancelled())
        scanner.holdUntilReleased = true
        let coordinator = makeCoordinator(
            scanner: scanner,
            prepare: { _, _ in QrPreparationResultSuccess(payload: payload) },
            checkIn: { _, _, _ in QrCheckInResultSuccess.shared }
        )
        defer { coordinator.dispose() }
        coordinator.handleBootstrap(Self.readyHomeResult())
        coordinator.openLecture(subjectId: "subj", subjectName: "공통 테스트")
        XCTAssertEqual(coordinator.path.count, 1)

        coordinator.startQrCheckIn(
            subjectId: "subj",
            subjectName: "공통 테스트",
            yearHakgi: "2026,1",
            requireParsedTerm: true
        )
        await waitUntil { coordinator.isPresentingQrScanner }

        if !coordinator.isPresentingQrScanner {
            coordinator.dispose()
        }
        XCTAssertNotNil(coordinator.homeHolder)
        XCTAssertEqual(coordinator.path.count, 1)

        scanner.releasePending()
        await waitUntil { !coordinator.isPresentingQrScanner && coordinator.qrPhase == .idle }

        XCTAssertEqual(coordinator.path.count, 1)
        XCTAssertNotNil(coordinator.homeHolder)
        XCTAssertEqual(coordinator.bootstrapPhase, .ready)
    }

    func testDuplicateLaunchIsIgnoredUntilReleased() async {
        let payload = Self.samplePayload()
        let scanner = FakeQrScanPresenter(result: QrScanResultCancelled())
        scanner.holdUntilReleased = true
        let coordinator = makeCoordinator(
            scanner: scanner,
            prepare: { _, _ in QrPreparationResultSuccess(payload: payload) },
            checkIn: { _, _, _ in QrCheckInResultSuccess.shared }
        )
        defer { coordinator.dispose() }
        coordinator.handleBootstrap(Self.readyHomeResult())

        coordinator.startQrCheckIn(
            subjectId: "subj",
            subjectName: "공통 테스트",
            yearHakgi: "2026,1",
            requireParsedTerm: false
        )
        await waitUntil { scanner.scanCount == 1 }

        coordinator.startQrCheckIn(
            subjectId: "subj",
            subjectName: "공통 테스트",
            yearHakgi: "2026,1",
            requireParsedTerm: false
        )
        XCTAssertEqual(scanner.scanCount, 1)

        scanner.releasePending()
        await waitUntil { coordinator.qrPhase == .idle }
        XCTAssertEqual(scanner.scanCount, 1)
    }

    func testUnsupportedSubjectShowsToast() async {
        var prepareCount = 0
        let scanner = FakeQrScanPresenter(result: QrScanResultCancelled())
        let coordinator = makeCoordinator(
            scanner: scanner,
            prepare: { _, _ in
                prepareCount += 1
                return QrPreparationResultUnsupportedSubject.shared
            },
            checkIn: { _, _, _ in QrCheckInResultSuccess.shared }
        )
        defer { coordinator.dispose() }
        coordinator.handleBootstrap(Self.readyHomeResult())

        coordinator.startQrCheckIn(
            subjectId: "subj",
            subjectName: "공통 테스트",
            yearHakgi: "2026,1",
            requireParsedTerm: false
        )
        await waitUntil { coordinator.toastMessage != nil }

        XCTAssertEqual(coordinator.toastMessage, "QR출석이 지원되지 않는 강의입니다.")
        XCTAssertEqual(prepareCount, 1)
        XCTAssertEqual(scanner.scanCount, 0)
        XCTAssertEqual(coordinator.qrPhase, .idle)
    }

    func testPermissionRequiredShowsScanFailure() async {
        let payload = Self.samplePayload()
        let scanner = FakeQrScanPresenter(result: QrScanResultPermissionRequired())
        let coordinator = makeCoordinator(
            scanner: scanner,
            prepare: { _, _ in QrPreparationResultSuccess(payload: payload) },
            checkIn: { _, _, _ in QrCheckInResultSuccess.shared }
        )
        defer { coordinator.dispose() }
        coordinator.handleBootstrap(Self.readyHomeResult())

        coordinator.startQrCheckIn(
            subjectId: "subj",
            subjectName: "공통 테스트",
            yearHakgi: "2026,1",
            requireParsedTerm: false
        )
        await waitUntil { coordinator.isQrAlertPresented }

        XCTAssertEqual(coordinator.qrAlertTitle, "QR 스캔 실패")
        XCTAssertEqual(coordinator.qrAlertMessage, "카메라 권한을 허용해주세요.")
    }

    func testPrepareSessionExpiredGoesToExpiredPhase() async {
        let coordinator = makeCoordinator(
            scanner: FakeQrScanPresenter(result: QrScanResultCancelled()),
            prepare: { _, _ in QrPreparationResultSessionExpired.shared },
            checkIn: { _, _, _ in QrCheckInResultSuccess.shared }
        )
        defer { coordinator.dispose() }
        coordinator.handleBootstrap(Self.readyHomeResult())

        coordinator.startQrCheckIn(
            subjectId: "subj",
            subjectName: "공통 테스트",
            yearHakgi: "2026,1",
            requireParsedTerm: false
        )
        await waitUntil { coordinator.bootstrapPhase == .sessionExpired }

        XCTAssertEqual(coordinator.bootstrapPhase, .sessionExpired)
        XCTAssertEqual(coordinator.qrPhase, .idle)
    }

    func testLectureMissingTermShowsToast() {
        let coordinator = makeCoordinator(
            scanner: FakeQrScanPresenter(result: QrScanResultCancelled()),
            prepare: { _, _ in
                XCTFail("prepare must not run without a parsed term")
                return QrPreparationResultUnsupportedSubject.shared
            },
            checkIn: { _, _, _ in QrCheckInResultSuccess.shared }
        )
        defer { coordinator.dispose() }
        coordinator.handleBootstrap(Self.readyHomeResult())

        coordinator.startQrCheckIn(
            subjectId: "subj",
            subjectName: "공통 테스트",
            yearHakgi: "",
            requireParsedTerm: true
        )
        XCTAssertEqual(coordinator.toastMessage, "QR출석을 위한 정보를 불러오지 못했어요. 다시 시도해주세요.")
        XCTAssertEqual(coordinator.qrPhase, .idle)
    }

    func testMapBarcodeAndAvailability() {
        XCTAssertTrue(IosQrScanner.mapBarcode(value: nil, isQr: true) is QrScanResultCancelled)
        XCTAssertTrue(IosQrScanner.mapBarcode(value: "", isQr: true) is QrScanResultCancelled)
        XCTAssertTrue(IosQrScanner.mapBarcode(value: "abc", isQr: false) is QrScanResultFailed)
        XCTAssertEqual((IosQrScanner.mapBarcode(value: "abc", isQr: true) as? QrScanResultSuccess)?.value, "abc")

        XCTAssertTrue(
            IosQrScanner.mapAvailability(
                isSupported: true,
                isAvailable: true,
                permission: .denied
            ) is QrScanResultPermissionRequired
        )
        XCTAssertTrue(
            IosQrScanner.mapAvailability(
                isSupported: false,
                isAvailable: true,
                permission: .authorized
            ) is QrScanResultFailed
        )
        XCTAssertNil(
            IosQrScanner.mapAvailability(
                isSupported: true,
                isAvailable: true,
                permission: .authorized
            )
        )
    }

    private func makeCoordinator(
        scanner: FakeQrScanPresenter,
        prepare: @escaping (QrPreparationRequest, SecretValue) async -> QrPreparationResult,
        checkIn: @escaping (QrAttendancePayload, SecretValue, SecretValue) async -> QrCheckInResult
    ) -> HomeCoordinator {
        let suite = "com.icecream.kwklasplus.test.qr.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defaults.removePersistentDomain(forName: suite)
        return HomeCoordinator(
            authRuntime: IosAuthRuntime.companion.create(defaults: defaults),
            onLogout: {},
            qrScanner: IosQrScanner(presenter: scanner),
            prepareCheckIn: prepare,
            checkIn: checkIn
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

    private static func samplePayload() -> QrAttendancePayload {
        QrAttendancePayload(
            list: [],
            selectYear: "2026",
            selectHakgi: "1",
            openMajorCode: "",
            openGrade: "",
            openGwamokNo: "",
            bunbanNo: "",
            gwamokKname: "공통 테스트",
            codeName1: "",
            hakjumNum: "",
            sisuNum: "",
            memberName: "",
            currentNum: "",
            yoil: "",
            subj: "subj",
            randomKey: "k"
        )
    }

    private func waitUntil(timeout: TimeInterval = 2, _ predicate: @escaping () -> Bool) async {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if predicate() { return }
            try? await Task.sleep(nanoseconds: 20_000_000)
        }
        XCTAssertTrue(predicate(), "condition not met before timeout")
    }
}

@MainActor
private final class FakeQrScanPresenter: QrScanPresenting {
    var result: QrScanResult
    var holdUntilReleased = false
    private(set) var scanCount = 0
    private var pending: ((QrScanResult) -> Void)?

    init(result: QrScanResult) {
        self.result = result
    }

    func scan(from presenter: UIViewController?, completion: @escaping (QrScanResult) -> Void) {
        scanCount += 1
        if holdUntilReleased {
            pending = completion
            return
        }
        completion(result)
    }

    func releasePending() {
        let completion = pending
        pending = nil
        completion?(result)
    }
}
