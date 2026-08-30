import Foundation
import Security
import XCTest
@testable import kw_klas_plus

final class IosRsaLoginTokenEncryptorTests: XCTestCase {
    func testEncryptsKlasSubjectPublicKeyInfoWithPkcs1Padding() throws {
        let attributes: [CFString: Any] = [
            kSecAttrKeyType: kSecAttrKeyTypeRSA,
            kSecAttrKeySizeInBits: 2048,
        ]
        var generationError: Unmanaged<CFError>?
        let privateKey = try XCTUnwrap(
            SecKeyCreateRandomKey(attributes as CFDictionary, &generationError)
        )
        let publicKey = try XCTUnwrap(SecKeyCopyPublicKey(privateKey))
        var exportError: Unmanaged<CFError>?
        let pkcs1 = try XCTUnwrap(
            SecKeyCopyExternalRepresentation(publicKey, &exportError) as Data?
        )
        let subjectPublicKeyInfo = Self.subjectPublicKeyInfo(wrapping: pkcs1)
        let payload = #"{"loginId":"2026000000","loginPwd":"encrypted","loginTp":"MST"}"#

        let encrypted = try XCTUnwrap(
            IosRsaLoginTokenEncryptor().encrypt(
                publicKey: subjectPublicKeyInfo.base64EncodedString(),
                payload: payload
            )
        )
        var decryptionError: Unmanaged<CFError>?
        let decrypted = try XCTUnwrap(
            SecKeyCreateDecryptedData(
                privateKey,
                .rsaEncryptionPKCS1,
                try XCTUnwrap(Data(base64Encoded: encrypted)) as CFData,
                &decryptionError
            ) as Data?
        )

        XCTAssertEqual(String(data: decrypted, encoding: .utf8), payload)
    }

    func testRejectsMalformedPublicKey() {
        XCTAssertNil(
            IosRsaLoginTokenEncryptor().encrypt(
                publicKey: "not-base64",
                payload: "payload"
            )
        )
    }

    private static func subjectPublicKeyInfo(wrapping pkcs1: Data) -> Data {
        let rsaAlgorithmIdentifier = Data([
            0x30, 0x0d,
            0x06, 0x09, 0x2a, 0x86, 0x48, 0x86, 0xf7, 0x0d, 0x01, 0x01, 0x01,
            0x05, 0x00,
        ])
        let bitString = derElement(tag: 0x03, content: Data([0x00]) + pkcs1)
        return derElement(tag: 0x30, content: rsaAlgorithmIdentifier + bitString)
    }

    private static func derElement(tag: UInt8, content: Data) -> Data {
        Data([tag]) + derLength(content.count) + content
    }

    private static func derLength(_ length: Int) -> Data {
        if length < 0x80 { return Data([UInt8(length)]) }
        var value = length
        var bytes: [UInt8] = []
        while value > 0 {
            bytes.insert(UInt8(value & 0xff), at: 0)
            value >>= 8
        }
        return Data([0x80 | UInt8(bytes.count)] + bytes)
    }
}
