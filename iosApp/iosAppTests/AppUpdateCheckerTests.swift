import XCTest
@testable import kw_klas_plus

final class AppStoreVersionTests: XCTestCase {
    func testNumericComparisonAndMissingComponents() throws {
        XCTAssertGreaterThan(try XCTUnwrap(AppVersion("2.10.0")), try XCTUnwrap(AppVersion("2.9.9")))
        XCTAssertGreaterThan(try XCTUnwrap(AppVersion("2.0.1")), try XCTUnwrap(AppVersion("2.0")))
        XCTAssertEqual(AppVersion("2.0"), AppVersion("2.0.0"))
        for value in ["", "2..0", "v2.0", "2.0-beta", "-1.0", " 2.0", "2.０"] {
            XCTAssertNil(AppVersion(value), value)
        }
    }

    func testUnpublishedAppReturnsNoVersion() async throws {
        let version = try await client(json: #"{"resultCount":0,"results":[]}"#).latestVersion()
        XCTAssertNil(version)
    }

    func testPublishedMatchingAppReturnsMarketingVersion() async throws {
        let version = try await client(json: #"{"results":[{"trackId":6820491776,"bundleId":"com.icecream.kwklasplus","wrapperType":"software","version":"2.10.0"}]}"#).latestVersion()
        XCTAssertEqual(version, "2.10.0")
    }

    func testMismatchedIdentityIsIgnored() async throws {
        for json in [
            #"{"results":[{"trackId":1,"bundleId":"com.icecream.kwklasplus","wrapperType":"software","version":"9.0"}]}"#,
            #"{"results":[{"trackId":6820491776,"bundleId":"other.app","wrapperType":"software","version":"9.0"}]}"#
        ] {
            let version = try await client(json: json).latestVersion()
            XCTAssertNil(version)
        }
    }

    func testHTTPAndMalformedResponsesThrow() async {
        for client in [client(json: "{}", status: 503), client(json: "not JSON")] {
            do {
                _ = try await client.latestVersion()
                XCTFail("조회 오류가 전파되어야 함")
            } catch {}
        }
    }

    private func client(json: String, status: Int = 200) -> AppStoreVersionClient {
        AppStoreVersionClient { request in
            XCTAssertEqual(request.url, AppStoreVersionClient.lookupURL)
            XCTAssertNil(request.value(forHTTPHeaderField: "Authorization"))
            XCTAssertEqual(request.timeoutInterval, 10)
            return (Data(json.utf8), HTTPURLResponse(url: request.url!, statusCode: status, httpVersion: nil, headerFields: nil)!)
        }
    }
}

@MainActor
final class AppUpdateCheckerTests: XCTestCase {
    func testNewerVersionPromptsOncePerProcess() async {
        var date = Date(timeIntervalSince1970: 100)
        var requests = 0
        let checker = AppUpdateChecker(installedVersion: "2.0.0", lookup: {
            requests += 1
            return "2.1.0"
        }, now: { date })
        await checker.check()
        XCTAssertEqual(checker.availableVersion, "2.1.0")
        checker.dismiss()
        await checker.check()
        XCTAssertEqual(requests, 1)
        date = date.addingTimeInterval(6 * 60 * 60)
        await checker.check()
        XCTAssertEqual(requests, 2)
        XCTAssertNil(checker.availableVersion)
    }

    func testEmptyResultCanDiscoverVersionAfterGoingLive() async {
        var date = Date(timeIntervalSince1970: 100)
        var storeVersion: String?
        let checker = AppUpdateChecker(installedVersion: "2.0.0", lookup: { storeVersion }, now: { date })
        await checker.check()
        XCTAssertNil(checker.availableVersion)
        storeVersion = "2.1.0"
        date = date.addingTimeInterval(6 * 60 * 60)
        await checker.check()
        XCTAssertEqual(checker.availableVersion, "2.1.0")
    }

    func testEqualOlderAndInvalidVersionsDoNotPrompt() async {
        for version in ["2.0", "1.9.9", "invalid"] {
            let checker = AppUpdateChecker(installedVersion: "2.0.0", lookup: { version })
            await checker.check()
            XCTAssertNil(checker.availableVersion)
        }
    }

    func testNetworkFailureIsQuietAndRetriesAfterFifteenMinutes() async {
        var date = Date(timeIntervalSince1970: 100)
        var requests = 0
        let checker = AppUpdateChecker(installedVersion: "2.0.0", lookup: {
            requests += 1
            if requests == 1 { throw URLError(.notConnectedToInternet) }
            return "2.1.0"
        }, now: { date })
        await checker.check()
        await checker.check()
        XCTAssertNil(checker.availableVersion)
        XCTAssertEqual(requests, 1)
        date = date.addingTimeInterval(15 * 60)
        await checker.check()
        XCTAssertEqual(checker.availableVersion, "2.1.0")
    }

    func testCancellationDoesNotThrottleNextCheck() async {
        var requests = 0
        let checker = AppUpdateChecker(installedVersion: "2.0.0", lookup: {
            requests += 1
            if requests == 1 { throw CancellationError() }
            return "2.1.0"
        })
        await checker.check()
        await checker.check()
        XCTAssertEqual(checker.availableVersion, "2.1.0")
    }
}
