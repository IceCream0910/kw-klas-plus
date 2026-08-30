import AVFoundation
import Shared
import SwiftUI
import UIKit
import VisionKit

@MainActor
protocol QrScanPresenting: AnyObject {
    func scan(from presenter: UIViewController?, completion: @escaping (QrScanResult) -> Void)
}

@MainActor
final class IosQrScanner {
    private let presenter: QrScanPresenting

    init(presenter: QrScanPresenting) {
        self.presenter = presenter
    }

    func scan(from viewController: UIViewController? = nil) async -> QrScanResult {
        await withCheckedContinuation { continuation in
            presenter.scan(from: viewController) { result in
                continuation.resume(returning: result)
            }
        }
    }

    static func mapBarcode(value: String?, isQr: Bool) -> QrScanResult {
        guard let value, !value.isEmpty else { return QrScanResultCancelled() }
        guard isQr else { return QrScanResultFailed(reason: "unsupported_barcode_format") }
        return QrScanResultSuccess(value: value)
    }

    static func mapAvailability(
        isSupported: Bool,
        isAvailable: Bool,
        permission: AVAuthorizationStatus
    ) -> QrScanResult? {
        switch permission {
        case .denied, .restricted:
            return QrScanResultPermissionRequired()
        default:
            break
        }
        if !isSupported || !isAvailable {
            return QrScanResultFailed(reason: "scanner_unavailable")
        }
        return nil
    }
}

struct QrDataScannerView: UIViewControllerRepresentable {
    var onResult: (QrScanResult) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(onResult: onResult)
    }

    func makeUIViewController(context: Context) -> QrScannerHostController {
        let host = QrScannerHostController()
        host.onReady = { [weak host] in
            guard let host else { return }
            context.coordinator.start(in: host)
        }
        return host
    }

    func updateUIViewController(_ host: QrScannerHostController, context: Context) {
        context.coordinator.onResult = onResult
        host.onReady = { [weak host] in
            guard let host else { return }
            context.coordinator.start(in: host)
        }
    }

    final class Coordinator: NSObject, DataScannerViewControllerDelegate {
        var onResult: (QrScanResult) -> Void
        private var started = false
        private var finished = false
        weak var scanner: DataScannerViewController?

        init(onResult: @escaping (QrScanResult) -> Void) {
            self.onResult = onResult
        }

        func start(in host: UIViewController) {
            guard !started else { return }
            started = true
            let permission = AVCaptureDevice.authorizationStatus(for: .video)
            if permission == .notDetermined {
                AVCaptureDevice.requestAccess(for: .video) { [weak self, weak host] granted in
                    DispatchQueue.main.async {
                        guard let self, let host else { return }
                        if granted {
                            self.showScanner(in: host)
                        } else {
                            self.finish(QrScanResultPermissionRequired())
                        }
                    }
                }
                return
            }
            showScanner(in: host)
        }

        private func showScanner(in host: UIViewController) {
            if let blocked = IosQrScanner.mapAvailability(
                isSupported: DataScannerViewController.isSupported,
                isAvailable: DataScannerViewController.isAvailable,
                permission: AVCaptureDevice.authorizationStatus(for: .video)
            ) {
                finish(blocked)
                return
            }
            let scanner = DataScannerViewController(
                recognizedDataTypes: [.barcode(symbologies: [.qr])],
                qualityLevel: .balanced,
                recognizesMultipleItems: false,
                isHighFrameRateTrackingEnabled: false,
                isHighlightingEnabled: true
            )
            scanner.delegate = self
            host.addChild(scanner)
            scanner.view.frame = host.view.bounds
            scanner.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
            host.view.addSubview(scanner.view)
            scanner.didMove(toParent: host)
            self.scanner = scanner

            let cancel = UIButton(type: .system)
            cancel.setTitle("취소", for: .normal)
            cancel.titleLabel?.font = .preferredFont(forTextStyle: .body)
            cancel.addTarget(self, action: #selector(cancelScan), for: .touchUpInside)
            cancel.translatesAutoresizingMaskIntoConstraints = false
            host.view.addSubview(cancel)
            NSLayoutConstraint.activate([
                cancel.leadingAnchor.constraint(equalTo: host.view.safeAreaLayoutGuide.leadingAnchor, constant: 16),
                cancel.topAnchor.constraint(equalTo: host.view.safeAreaLayoutGuide.topAnchor, constant: 8),
            ])

            do {
                try scanner.startScanning()
            } catch {
                finish(QrScanResultFailed(reason: "scanner_start_failed"))
            }
        }

        func dataScanner(
            _ dataScanner: DataScannerViewController,
            didAdd addedItems: [RecognizedItem],
            allItems: [RecognizedItem]
        ) {
            guard case let .barcode(barcode) = addedItems.first else { return }
            dataScanner.stopScanning()
            finish(IosQrScanner.mapBarcode(value: barcode.payloadStringValue, isQr: true))
        }

        func dataScanner(
            _ dataScanner: DataScannerViewController,
            becameUnavailableWithError error: DataScannerViewController.ScanningUnavailable
        ) {
            if case .cameraRestricted = error {
                finish(QrScanResultPermissionRequired())
            } else {
                finish(QrScanResultFailed(reason: "scanner_unavailable"))
            }
        }

        @objc func cancelScan() {
            scanner?.stopScanning()
            finish(QrScanResultCancelled())
        }

        func finish(_ result: QrScanResult) {
            guard !finished else { return }
            finished = true
            onResult(result)
        }
    }
}

final class QrScannerHostController: UIViewController {
    var onReady: (() -> Void)?

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        onReady?()
    }
}
