import Foundation
import Shared
import WebKit

protocol DownloadCookieProviding {
    func cookieHeader(for url: URL) async -> String?
}

struct WKWebsiteCookieProvider: DownloadCookieProviding {
    let store: WKHTTPCookieStore

    init(store: WKHTTPCookieStore = WKWebsiteDataStore.default().httpCookieStore) {
        self.store = store
    }

    func cookieHeader(for url: URL) async -> String? {
        let cookies: [HTTPCookie] = await withCheckedContinuation { continuation in
            store.getAllCookies { continuation.resume(returning: $0) }
        }
        return Self.header(from: cookies, for: url)
    }

    static func header(from cookies: [HTTPCookie], for url: URL) -> String? {
        let matching = cookies.filter { $0.klas_matches(url) }
        let header = HTTPCookie.requestHeaderFields(with: matching)["Cookie"]
        return header?.isEmpty == false ? header : nil
    }
}

enum IosDownloadFileStore {
    static var root: URL {
        FileManager.default.temporaryDirectory.appendingPathComponent("klas-downloads", isDirectory: true)
    }

    static func makeDestination(fileName: String) -> URL? {
        let directory = root.appendingPathComponent(UUID().uuidString, isDirectory: true)
        do {
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            return directory.appendingPathComponent(fileName, isDirectory: false)
        } catch {
            return nil
        }
    }

    static func isStoredFile(_ url: URL) -> Bool {
        url.isFileURL && url.standardizedFileURL.path.hasPrefix(root.standardizedFileURL.path)
    }
}

extension HTTPCookie {
    func klas_matches(_ url: URL) -> Bool {
        guard let host = url.host?.lowercased() else { return false }
        let cookieDomain = domain.lowercased().trimmingCharacters(in: CharacterSet(charactersIn: "."))
        let hostMatches = host == cookieDomain || host.hasSuffix(".\(cookieDomain)")
        guard hostMatches else { return false }
        if isSecure && url.scheme?.lowercased() != "https" { return false }
        let cookiePath = path.isEmpty ? "/" : path
        let urlPath = url.path.isEmpty ? "/" : url.path
        return urlPath.hasPrefix(cookiePath)
    }
}

class IosFileTransfer: NSObject, URLSessionDownloadDelegate {
    var onProgress: ((String, Double) -> Void)?
    var onCompletedFile: ((URL) -> Void)?

    private let policy = FileTransferPolicy.companion.create()
    private let cookies: DownloadCookieProviding
    private let injectedSession: URLSession?
    private var session: URLSession?
    private var fileName = "download"
    private var destinationURL: URL?
    private var continuation: CheckedContinuation<PlatformActionResult, Never>?
    private var downloadTask: URLSessionDownloadTask?

    init(
        cookies: DownloadCookieProviding = WKWebsiteCookieProvider(),
        urlSession: URLSession? = nil
    ) {
        self.cookies = cookies
        self.injectedSession = urlSession
        super.init()
    }

    func download(request: FileTransferRequest) async -> PlatformActionResult {
        let validated = policy.validate(request: request)
        guard let accepted = validated as? FileTransferValidationAccepted else {
            return PlatformActionResultFailed(reason: "invalid_download_request")
        }
        guard let url = URL(string: accepted.request.url) else {
            return PlatformActionResultFailed(reason: "invalid_download_request")
        }
        fileName = DownloadMetadata.shared.resolvedFileName(request: accepted.request)
        destinationURL = IosDownloadFileStore.makeDestination(fileName: fileName)
        guard let destinationURL else {
            return PlatformActionResultFailed(reason: "download_enqueue_failed")
        }
        let cookieHeader = await cookies.cookieHeader(for: url)
        guard let urlRequest = Self.makeURLRequest(request: accepted.request, cookieHeader: cookieHeader) else {
            return PlatformActionResultFailed(reason: "invalid_download_request")
        }
        // URLSession(delegate:)는 invalidate 전까지 delegate를 강하게 잡으므로 다운로드마다 세션을 만들고 finish에서 정리한다.
        let activeSession = injectedSession ?? URLSession(
            configuration: .ephemeral,
            delegate: self,
            delegateQueue: .main
        )
        session = activeSession
        return await withCheckedContinuation { continuation in
            self.continuation = continuation
            let task = activeSession.downloadTask(with: urlRequest)
            self.downloadTask = task
            DispatchQueue.main.async {
                self.onProgress?(self.fileName, 0)
            }
            task.resume()
        }
    }

    func cancel() {
        downloadTask?.cancel()
        downloadTask = nil
        finish(PlatformActionResultCancelled())
    }

    func urlSession(
        _ session: URLSession,
        downloadTask: URLSessionDownloadTask,
        didWriteData bytesWritten: Int64,
        totalBytesWritten: Int64,
        totalBytesExpectedToWrite: Int64
    ) {
        guard totalBytesExpectedToWrite > 0 else { return }
        let fraction = Double(totalBytesWritten) / Double(totalBytesExpectedToWrite)
        onProgress?(fileName, fraction)
    }

    func urlSession(
        _ session: URLSession,
        downloadTask: URLSessionDownloadTask,
        didFinishDownloadingTo location: URL
    ) {
        guard let destinationURL else {
            finish(PlatformActionResultFailed(reason: "download_enqueue_failed"))
            return
        }
        do {
            if FileManager.default.fileExists(atPath: destinationURL.path) {
                try FileManager.default.removeItem(at: destinationURL)
            }
            try FileManager.default.moveItem(at: location, to: destinationURL)
            onCompletedFile?(destinationURL)
            finish(PlatformActionResultSuccess())
        } catch {
            finish(PlatformActionResultFailed(reason: "download_enqueue_failed"))
        }
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        guard let error else { return }
        let nsError = error as NSError
        if nsError.domain == NSURLErrorDomain, nsError.code == NSURLErrorCancelled {
            finish(PlatformActionResultCancelled())
            return
        }
        finish(PlatformActionResultFailed(reason: "download_enqueue_failed"))
    }

    private func finish(_ result: PlatformActionResult) {
        guard let continuation else { return }
        self.continuation = nil
        downloadTask = nil
        if injectedSession == nil {
            session?.finishTasksAndInvalidate()
        }
        session = nil
        // 성공 시 완료 파일은 소비자(공유 시트)가 정리한다. 실패/취소는 임시 파일이 남지 않게 여기서 지운다.
        if !(result is PlatformActionResultSuccess), let destinationURL {
            try? FileManager.default.removeItem(at: destinationURL.deletingLastPathComponent())
        }
        destinationURL = nil
        continuation.resume(returning: result)
    }

    static func makeURLRequest(request: FileTransferRequest, cookieHeader: String?) -> URLRequest? {
        guard let url = URL(string: request.url) else { return nil }
        var urlRequest = URLRequest(url: url)
        if let cookieHeader, !cookieHeader.isEmpty {
            urlRequest.setValue(cookieHeader, forHTTPHeaderField: "Cookie")
        }
        if let userAgent = request.userAgent, !userAgent.isEmpty {
            urlRequest.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        }
        return urlRequest
    }
}
