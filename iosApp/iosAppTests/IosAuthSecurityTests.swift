import Foundation
import Shared
import XCTest
@testable import kw_klas_plus

final class IosAuthSecurityTests: XCTestCase {
    func testKeychainSecureStoreRoundTripInAppProcess() {
        let store = IosKeychainSecureStore(
            service: "com.icecream.kwklasplus.test.keychain.\(UUID().uuidString)"
        )
        let key = SecureKey.encryptedKlasPassword
        let plaintext = "app-secret"
        let expectation = expectation(description: "keychain")

        store.remove(key: key) { removeError in
            XCTAssertNil(removeError)
            store.write(key: key, value: SecretValue.companion.of(value: plaintext)) { writeError in
                XCTAssertNil(writeError)
                store.read(key: key) { loaded, readError in
                    XCTAssertNil(readError)
                    XCTAssertEqual(loaded?.reveal(), plaintext)
                    store.remove(key: key) { cleanupError in
                        XCTAssertNil(cleanupError)
                        store.read(key: key) { afterRemove, afterReadError in
                            XCTAssertNil(afterReadError)
                            XCTAssertNil(afterRemove)
                            expectation.fulfill()
                        }
                    }
                }
            }
        }

        waitForExpectations(timeout: 5)
    }

    func testSessionTokenIsNotMirroredToUserDefaults() {
        let suite = "com.icecream.kwklasplus.test.session.security.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defaults.removePersistentDomain(forName: suite)

        let runtime = IosAuthRuntime.companion.create(defaults: defaults)
        let expectation = expectation(description: "observe")
        runtime.observeSessionToken(token: "session-for-security") { result in
            XCTAssertTrue(result is SessionResultActive)
            expectation.fulfill()
        }
        waitForExpectations(timeout: 5)

        XCTAssertNil(defaults.string(forKey: "kwSESSION"))
        XCTAssertNotNil(defaults.string(forKey: "kwSESSION_timestamp"))
        XCTAssertNil(defaults.string(forKey: "kwPWD"))
        assertUserDefaults(defaults, doesNotContain: "session-for-security")

        defaults.removePersistentDomain(forName: suite)
    }

    func testSecretDescriptionsRedactPlainPasswordAndSession() {
        let password = "plain-password-\(UUID().uuidString)"
        let token = "session-token-\(UUID().uuidString)"
        let plain = PlainPassword.companion.of(value: password)
        let secret = SecretValue.companion.of(value: token)
        let descriptions = [
            String(describing: plain),
            String(reflecting: plain),
            String(describing: secret),
            String(reflecting: secret),
            String(describing: StoredCredential(accountId: "2020123456", encryptedPassword: secret)),
        ]

        XCTAssertEqual(String(describing: plain), "[REDACTED]")
        XCTAssertEqual(String(describing: secret), "[REDACTED]")
        for description in descriptions {
            XCTAssertFalse(description.contains(password), "description leaked password: \(description)")
            XCTAssertFalse(description.contains(token), "description leaked session: \(description)")
        }
    }

    func testCredentialStoreDoesNotPersistPlainPasswordOrSessionToUserDefaults() {
        let suite = "com.icecream.kwklasplus.test.credential.security.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defaults.removePersistentDomain(forName: suite)
        let plaintext = "plain-password-\(UUID().uuidString)"
        let encrypted = "encrypted-password-\(UUID().uuidString)"
        let token = "session-token-\(UUID().uuidString)"
        let keychain = IosKeychainSecureStore(
            service: "com.icecream.kwklasplus.test.keychain.\(UUID().uuidString)"
        )
        let store = IosCredentialStore(secureStore: keychain, defaults: defaults)
        let saved = expectation(description: "save credential")

        store.save(
            credential: StoredCredential(
                accountId: "2020123456",
                encryptedPassword: SecretValue.companion.of(value: encrypted)
            )
        ) { error in
            XCTAssertNil(error)
            saved.fulfill()
        }
        wait(for: [saved], timeout: 5)

        XCTAssertEqual(defaults.string(forKey: "kwID"), "2020123456")
        XCTAssertNil(defaults.string(forKey: "kwPWD"))
        XCTAssertNil(defaults.string(forKey: "kwSESSION"))
        assertUserDefaults(defaults, doesNotContain: plaintext)
        assertUserDefaults(defaults, doesNotContain: encrypted)
        assertUserDefaults(defaults, doesNotContain: token)

        let removed = expectation(description: "remove keychain")
        keychain.remove(key: SecureKey.encryptedKlasPassword) { error in
            XCTAssertNil(error)
            removed.fulfill()
        }
        wait(for: [removed], timeout: 5)
        defaults.removePersistentDomain(forName: suite)
    }

    func testLoginUiStateValidationMatchesAndroid() {
        var state = LoginUiState(
            onboardingVisible: false,
            studentId: "202012345",
            password: "secret",
            agreementAccepted: true
        )
        XCTAssertFalse(state.passwordFieldVisible)
        XCTAssertFalse(state.loginEnabled)

        state.studentId = "2020123456"
        XCTAssertTrue(state.passwordFieldVisible)
        XCTAssertTrue(state.loginEnabled)

        state.agreementAccepted = false
        XCTAssertFalse(state.loginEnabled)
    }

    private func assertUserDefaults(
        _ defaults: UserDefaults,
        doesNotContain secret: String,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        for (key, value) in defaults.dictionaryRepresentation() {
            XCTAssertFalse(
                String(describing: value).contains(secret),
                "UserDefaults[\(key)] leaked secret",
                file: file,
                line: line
            )
        }
    }
}
