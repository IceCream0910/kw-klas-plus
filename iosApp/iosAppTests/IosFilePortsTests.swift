import Foundation
import Shared
import UIKit
import WebKit
import XCTest
@testable import kw_klas_plus

final class IosFilePortsTests: XCTestCase {
    func testExternalNavigationAllowsMailtoTelHttpsAndRejectsMaliciousSchemes() {
        let opener = RecordingUrlOpener()
        let holder = WebViewHolder(navigator: IosExternalNavigator(opener: opener))
        defer { holder.dispose() }

        XCTAssertFalse(holder.handleDecidePolicy(urlString: "mailto:help@example.com", isMainFrame: true))
        XCTAssertEqual(holder.lastExternalURL, "mailto:help@example.com")
        XCTAssertEqual(opener.opened, ["mailto:help@example.com"])

        XCTAssertFalse(holder.handleDecidePolicy(urlString: "tel:+82-2-123-4567", isMainFrame: true))
        XCTAssertEqual(holder.lastExternalURL, "tel:+82-2-123-4567")

        XCTAssertFalse(holder.handleDecidePolicy(urlString: "https://example.com/help", isMainFrame: true))
        XCTAssertEqual(holder.lastExternalURL, "https://example.com/help")

        XCTAssertFalse(holder.handleDecidePolicy(urlString: "javascript:alert(1)", isMainFrame: true))
        XCTAssertFalse(holder.handleDecidePolicy(urlString: "intent://settings", isMainFrame: true))
        XCTAssertFalse(holder.handleDecidePolicy(urlString: "file:///tmp/secret", isMainFrame: true))
        XCTAssertFalse(holder.handleDecidePolicy(urlString: "https://user@example.com", isMainFrame: true))
        XCTAssertEqual(holder.lastExternalURL, "https://example.com/help")
        XCTAssertEqual(
            opener.opened,
            [
                "mailto:help@example.com",
                "tel:+82-2-123-4567",
                "https://example.com/help",
            ]
        )

        XCTAssertTrue(holder.handleDecidePolicy(urlString: "https://klasplus.yuntae.in/feed", isMainFrame: true))
    }

    func testInAppWebHolderLoadsUniversityNoticeInsteadOfSafari() {
        let opener = RecordingUrlOpener()
        let holder = WebViewHolder(
            navigator: IosExternalNavigator(opener: opener),
            allowsInAppWeb: true
        )
        defer { holder.dispose() }
        let notice = "https://www.kw.ac.kr/ko/life/notice.jsp"

        XCTAssertTrue(holder.handleDecidePolicy(urlString: notice, isMainFrame: true))
        XCTAssertNil(holder.lastExternalURL)
        XCTAssertTrue(opener.opened.isEmpty)
        holder.handleCreateWindow(urlString: notice)
        XCTAssertNil(holder.lastExternalURL)
        XCTAssertTrue(opener.opened.isEmpty)

        XCTAssertFalse(holder.handleDecidePolicy(urlString: "mailto:help@example.com", isMainFrame: true))
        XCTAssertEqual(holder.lastExternalURL, "mailto:help@example.com")
        XCTAssertEqual(opener.opened, ["mailto:help@example.com"])

        XCTAssertFalse(holder.handleDecidePolicy(urlString: "javascript:alert(1)", isMainFrame: true))
        XCTAssertFalse(holder.handleDecidePolicy(urlString: "intent://settings", isMainFrame: true))
        XCTAssertFalse(holder.handleDecidePolicy(urlString: "file:///tmp/secret", isMainFrame: true))
        XCTAssertEqual(holder.lastExternalURL, "mailto:help@example.com")
        XCTAssertEqual(opener.opened, ["mailto:help@example.com"])
    }

    func testLinkViewBridgeHolderAllowsInAppWebAndNoticeScrollScript() {
        let holder = WebViewHolder.withLegacyBridge(
            surface: .linkView,
            handler: AcceptingBridgeCommandHandler()
        )
        defer { holder.dispose() }

        XCTAssertTrue(
            holder.handleDecidePolicy(
                urlString: "https://www.kw.ac.kr/ko/life/notice.jsp",
                isMainFrame: true
            )
        )
        XCTAssertNil(holder.lastExternalURL)
        XCTAssertEqual(
            holder.pageReadyScript(for: "https://www.kw.ac.kr/ko/life/notice.jsp")?.reveal(),
            KlasWebAutomationScripts.shared.makeNoticeScrollable().reveal()
        )
        XCTAssertNil(holder.pageReadyScript(for: "https://www.kw.ac.kr/ko/life/facility11.jsp"))
        let defaultHolder = WebViewHolder()
        defer { defaultHolder.dispose() }
        XCTAssertNil(defaultHolder.pageReadyScript(for: "https://www.kw.ac.kr/ko/life/notice.jsp"))
    }

    func testDownloadDispatchIsSingleFlightAndInlinePdfHandling() throws {
        let transfer = RecordingFileTransfer()
        let dispatched = expectation(description: "downloads dispatched to FileTransfer")
        transfer.onDownload = { dispatched.fulfill() }
        let holder = WebViewHolder(
            navigator: IosExternalNavigator(opener: RecordingUrlOpener()),
            fileTransfer: transfer
        )
        defer { holder.dispose() }
        let url = URL(string: "https://klas.kw.ac.kr/std/file")!

        let attachmentPdf = try XCTUnwrap(
            HTTPURLResponse(
                url: url,
                statusCode: 200,
                httpVersion: nil,
                headerFields: [
                    "Content-Type": "application/octet-stream",
                    "Content-Disposition": "attachment; filename=\"컴퓨터구조_15주차_강의자료.pdf\"",
                ]
            )
        )
        XCTAssertEqual(
            holder.handleNavigationResponse(attachmentPdf, isMainFrame: true, canShowMIMEType: false),
            .cancel
        )

        let binary = try XCTUnwrap(
            HTTPURLResponse(url: url, statusCode: 200, httpVersion: nil, headerFields: nil)
        )
        XCTAssertEqual(
            holder.handleNavigationResponse(binary, isMainFrame: true, canShowMIMEType: false),
            .cancel
        )
        XCTAssertEqual(
            holder.handleNavigationResponse(binary, isMainFrame: true, canShowMIMEType: true),
            .allow
        )

        let inlinePdf = try XCTUnwrap(
            HTTPURLResponse(
                url: url,
                statusCode: 200,
                httpVersion: nil,
                headerFields: [
                    "Content-Type": "application/pdf",
                    "Content-Disposition": "inline; filename=\"week15.pdf\"",
                ]
            )
        )
        XCTAssertEqual(
            holder.handleNavigationResponse(inlinePdf, isMainFrame: true, canShowMIMEType: true),
            .allow
        )

        wait(for: [dispatched], timeout: 2)
        XCTAssertEqual(transfer.requests.map { $0.url }, [url.absoluteString])
    }

    func testLocalDownloadedPdfIsAllowedInWebView() throws {
        let holder = WebViewHolder(
            navigator: IosExternalNavigator(opener: RecordingUrlOpener()),
            fileTransfer: RecordingFileTransfer()
        )
        defer { holder.dispose() }
        let fileURL = try XCTUnwrap(IosDownloadFileStore.makeDestination(fileName: "week15.pdf"))
        defer { try? FileManager.default.removeItem(at: fileURL.deletingLastPathComponent()) }
        try Data("%PDF-1.4".utf8).write(to: fileURL)
        let response = try XCTUnwrap(
            HTTPURLResponse(url: fileURL, statusCode: 200, httpVersion: nil, headerFields: [
                "Content-Type": "application/pdf",
            ])
        )
        XCTAssertEqual(
            holder.handleNavigationResponse(response, isMainFrame: true, canShowMIMEType: true),
            .allow
        )
    }

    func testRejectedDownloadUrlDoesNotBecomeDownload() throws {
        let transfer = RecordingFileTransfer()
        let holder = WebViewHolder(
            navigator: IosExternalNavigator(opener: RecordingUrlOpener()),
            fileTransfer: transfer
        )
        defer { holder.dispose() }
        let fileURL = URL(string: "file:///tmp/secret.pdf")!
        let response = try XCTUnwrap(
            HTTPURLResponse(
                url: fileURL,
                statusCode: 200,
                httpVersion: nil,
                headerFields: ["Content-Disposition": "attachment; filename=\"secret.pdf\""]
            )
        )
        XCTAssertFalse(holder.isDownloadCandidate(response, canShowMIMEType: true))
        XCTAssertFalse(holder.isAcceptedDownload(response))
        XCTAssertEqual(
            holder.handleNavigationResponse(response, isMainFrame: true, canShowMIMEType: true),
            .cancel
        )
        XCTAssertTrue(transfer.requests.isEmpty)
    }

    func testDownloadRequestIncludesCookieHeaderAndRejectsNonWebUrl() async {
        let request = FileTransferRequest(
            url: "https://klas.kw.ac.kr/std/file",
            suggestedFileName: "a.pdf",
            mimeType: "application/pdf",
            userAgent: "iOSApp_v1",
            contentDisposition: nil
        )
        let urlRequest = IosFileTransfer.makeURLRequest(request: request, cookieHeader: "SESSION=token")
        XCTAssertEqual(urlRequest?.value(forHTTPHeaderField: "Cookie"), "SESSION=token")
        XCTAssertEqual(urlRequest?.value(forHTTPHeaderField: "User-Agent"), "iOSApp_v1")

        let transfer = IosFileTransfer(cookies: StaticCookieProvider(header: "SESSION=token"))
        let rejected = await transfer.download(
            request: FileTransferRequest(
                url: "javascript:download()",
                suggestedFileName: nil,
                mimeType: nil,
                userAgent: nil,
                contentDisposition: nil
            )
        )
        XCTAssertTrue(rejected is PlatformActionResultFailed)
    }

    func testKlasMatchesCookieDomainPathAndSecure() {
        let downloadURL = URL(string: "https://klas.kw.ac.kr/std/file")!
        let httpURL = URL(string: "http://klas.kw.ac.kr/std/file")!
        let otherHost = URL(string: "https://example.com/std/file")!
        let adminURL = URL(string: "https://klas.kw.ac.kr/admin/file")!

        let session = makeTestCookie(name: "SESSION", value: "klas-session", domain: ".kw.ac.kr", path: "/", secure: true)
        let otherSite = makeTestCookie(name: "OTHER", value: "nope", domain: "example.com", path: "/")
        let adminOnly = makeTestCookie(name: "ADMIN", value: "nope", domain: "klas.kw.ac.kr", path: "/admin")

        let cases: [(HTTPCookie, URL, Bool)] = [
            (session, downloadURL, true),
            (session, httpURL, false),
            (session, otherHost, false),
            (otherSite, downloadURL, false),
            (adminOnly, downloadURL, false),
            (adminOnly, adminURL, true),
        ]
        for (cookie, url, expected) in cases {
            let matched = cookie.klas_matches(url)
            print(
                "[download-cookie] match=\(matched) expected=\(expected) " +
                "\(cookie.name)=\(cookie.value) domain=\(cookie.domain) path=\(cookie.path) " +
                "secure=\(cookie.isSecure) url=\(url.absoluteString)"
            )
            XCTAssertEqual(matched, expected, "\(cookie.name) vs \(url.absoluteString)")
        }
    }

    @MainActor
    func testCookieHeaderIncludesOnlyMatchingStoreCookies() async {
        let url = URL(string: "https://klas.kw.ac.kr/std/file")!
        let cookies = [
            makeTestCookie(name: "SESSION", value: "klas-session", domain: ".kw.ac.kr", path: "/", secure: true),
            makeTestCookie(name: "OTHER", value: "nope", domain: "example.com", path: "/"),
            makeTestCookie(name: "ADMIN", value: "nope", domain: "klas.kw.ac.kr", path: "/admin"),
        ]
        let header = WKWebsiteCookieProvider.header(from: cookies, for: url)
        logDownloadCookies(cookies, url: url, header: header)
        XCTAssertEqual(header, "SESSION=klas-session")

        let dataStore = WKWebsiteDataStore.nonPersistent()
        let store = dataStore.httpCookieStore
        for cookie in cookies {
            await setCookie(cookie, on: store)
        }
        let storedHeader = await WKWebsiteCookieProvider(store: store).cookieHeader(for: url)
        print("[download-cookie] store header=\(storedHeader ?? "<nil>")")
        XCTAssertEqual(storedHeader, "SESSION=klas-session")

        let request = FileTransferRequest(
            url: url.absoluteString,
            suggestedFileName: "a.pdf",
            mimeType: "application/pdf",
            userAgent: "iOSApp_v1",
            contentDisposition: nil
        )
        let urlRequest = IosFileTransfer.makeURLRequest(request: request, cookieHeader: storedHeader)
        print("[download-cookie] URLRequest Cookie=\(urlRequest?.value(forHTTPHeaderField: "Cookie") ?? "<nil>")")
        XCTAssertEqual(urlRequest?.value(forHTTPHeaderField: "Cookie"), "SESSION=klas-session")
    }

    func testDownloadSendsMatchedCookieHeader() async {
        let url = URL(string: "https://klas.kw.ac.kr/std/file")!
        let cookies = [
            makeTestCookie(name: "SESSION", value: "klas-session", domain: ".kw.ac.kr", path: "/", secure: true),
            makeTestCookie(name: "OTHER", value: "nope", domain: "example.com", path: "/"),
        ]
        RecordingCookieURLProtocol.reset()
        let started = expectation(description: "download request started")
        RecordingCookieURLProtocol.started = started
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [RecordingCookieURLProtocol.self]
        let session = URLSession(configuration: configuration)
        let transfer = IosFileTransfer(
            cookies: FilteringCookieProvider(cookies: cookies),
            urlSession: session
        )
        let download = Task {
            await transfer.download(
                request: FileTransferRequest(
                    url: url.absoluteString,
                    suggestedFileName: "a.pdf",
                    mimeType: "application/pdf",
                    userAgent: "iOSApp_v1",
                    contentDisposition: nil
                )
            )
        }
        await fulfillment(of: [started], timeout: 3)
        let sent = RecordingCookieURLProtocol.cookieHeader
        print("[download-cookie] sent Cookie=\(sent ?? "<nil>")")
        logDownloadCookies(cookies, url: url, header: sent)
        XCTAssertEqual(sent, "SESSION=klas-session")
        transfer.cancel()
        _ = await download.value
        RecordingCookieURLProtocol.reset()
    }

    func testFileTransferCancelReturnsCancelled() async {
        let started = expectation(description: "request started")
        HangingURLProtocol.started = started
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [HangingURLProtocol.self]
        let session = URLSession(configuration: configuration)
        let transfer = IosFileTransfer(
            cookies: StaticCookieProvider(header: "SESSION=token"),
            urlSession: session
        )
        let download = Task {
            await transfer.download(
                request: FileTransferRequest(
                    url: "https://klas.kw.ac.kr/std/file",
                    suggestedFileName: "a.pdf",
                    mimeType: "application/pdf",
                    userAgent: nil,
                    contentDisposition: nil
                )
            )
        }
        await fulfillment(of: [started], timeout: 3)
        transfer.cancel()
        let result = await download.value
        XCTAssertTrue(result is PlatformActionResultCancelled)
        HangingURLProtocol.started = nil
    }

    func testFileTransferRejectsOverlappingDownloadUntilFirstCompletes() async {
        let started = expectation(description: "first request started")
        HangingURLProtocol.started = started
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [HangingURLProtocol.self]
        let session = URLSession(configuration: configuration)
        let transfer = IosFileTransfer(
            cookies: StaticCookieProvider(header: "SESSION=token"),
            urlSession: session
        )
        let request = FileTransferRequest(
            url: "https://klas.kw.ac.kr/std/file",
            suggestedFileName: "a.pdf",
            mimeType: "application/pdf",
            userAgent: nil,
            contentDisposition: nil
        )
        let firstDownload = Task {
            await transfer.download(request: request)
        }

        await fulfillment(of: [started], timeout: 3)
        let overlappingResult = await transfer.download(request: request)
        XCTAssertTrue(overlappingResult is PlatformActionResultFailed)

        transfer.cancel()
        let firstResult = await firstDownload.value
        XCTAssertTrue(firstResult is PlatformActionResultCancelled)
        HangingURLProtocol.started = nil
    }

    func testFilePickerCancelSingleAndMultiple() {
        let presenter = RecordingFilePickerPresenter()
        let picker = IosFilePicker(presenter: presenter)

        presenter.result = FilePickerResultCancelled()
        let cancelled = expectation(description: "cancelled")
        picker.pickForWeb(allowMultiple: false, from: nil) { urls in
            XCTAssertNil(urls)
            cancelled.fulfill()
        }
        wait(for: [cancelled], timeout: 1)
        XCTAssertEqual(presenter.allowMultiple, false)

        presenter.result = FilePickerResultSelected(references: ["file:///tmp/a", "file:///tmp/b"])
        let selected = expectation(description: "selected")
        picker.pickForWeb(allowMultiple: true, from: nil) { urls in
            XCTAssertEqual(urls?.map(\.absoluteString), ["file:///tmp/a", "file:///tmp/b"])
            selected.fulfill()
        }
        wait(for: [selected], timeout: 1)
        XCTAssertEqual(presenter.allowMultiple, true)
    }

    func testCancelDownloadWithoutActiveDownloadIsSafe() {
        let holder = WebViewHolder(navigator: IosExternalNavigator(opener: RecordingUrlOpener()))
        holder.cancelDownload()
        XCTAssertNil(holder.downloadProgress)
        XCTAssertNil(holder.shareableFileURL)
        holder.shareCurrentFile()
        holder.dispose()
    }
}

final class RecordingUrlOpener: IosUrlOpener {
    private(set) var opened: [String] = []

    func open(url: URL) -> Bool {
        opened.append(url.absoluteString)
        return true
    }
}

final class RecordingFilePickerPresenter: FilePickerPresenting {
    var allowMultiple: Bool?
    var result: FilePickerResult = FilePickerResultCancelled()

    func present(
        allowMultiple: Bool,
        from presenter: UIViewController?,
        completion: @escaping (FilePickerResult) -> Void
    ) {
        self.allowMultiple = allowMultiple
        completion(result)
    }
}

struct StaticCookieProvider: DownloadCookieProviding {
    let header: String?
    func cookieHeader(for url: URL) async -> String? { header }
}

struct FilteringCookieProvider: DownloadCookieProviding {
    let cookies: [HTTPCookie]
    func cookieHeader(for url: URL) async -> String? {
        WKWebsiteCookieProvider.header(from: cookies, for: url)
    }
}

private func makeTestCookie(
    name: String,
    value: String,
    domain: String,
    path: String = "/",
    secure: Bool = false
) -> HTTPCookie {
    var properties: [HTTPCookiePropertyKey: Any] = [
        .name: name,
        .value: value,
        .domain: domain,
        .path: path,
    ]
    if secure {
        properties[.secure] = "TRUE"
    }
    return HTTPCookie(properties: properties)!
}

@MainActor
private func setCookie(_ cookie: HTTPCookie, on store: WKHTTPCookieStore) async {
    await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
        store.setCookie(cookie) {
            continuation.resume()
        }
    }
}

private func logDownloadCookies(_ cookies: [HTTPCookie], url: URL, header: String?) {
    print("[download-cookie] url=\(url.absoluteString)")
    for cookie in cookies {
        print(
            "[download-cookie] \(cookie.name)=\(cookie.value) " +
            "domain=\(cookie.domain) path=\(cookie.path) secure=\(cookie.isSecure) " +
            "match=\(cookie.klas_matches(url))"
        )
    }
    print("[download-cookie] Cookie header=\(header ?? "<nil>")")
}

final class RecordingFileTransfer: IosFileTransfer {
    var onDownload: (() -> Void)?
    var result: PlatformActionResult = PlatformActionResultSuccess()

    private let lock = NSLock()
    private var storedRequests: [FileTransferRequest] = []
    var requests: [FileTransferRequest] {
        lock.lock()
        defer { lock.unlock() }
        return storedRequests
    }

    override func download(request: FileTransferRequest) async -> PlatformActionResult {
        lock.lock()
        storedRequests.append(request)
        lock.unlock()
        onDownload?()
        return result
    }

    override func cancel() {}
}

final class HangingURLProtocol: URLProtocol {
    static var started: XCTestExpectation?

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        DispatchQueue.main.async {
            HangingURLProtocol.started?.fulfill()
        }
    }

    override func stopLoading() {}
}

final class RecordingCookieURLProtocol: URLProtocol {
    static var started: XCTestExpectation?
    static var cookieHeader: String?

    static func reset() {
        started = nil
        cookieHeader = nil
    }

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        Self.cookieHeader = request.value(forHTTPHeaderField: "Cookie")
        DispatchQueue.main.async {
            Self.started?.fulfill()
        }
    }

    override func stopLoading() {}
}
