import Foundation
import Security
import Shared

final class IosRsaLoginTokenEncryptor: LoginTokenEncryptor {
    func encrypt(publicKey: String, payload: String) -> String? {
        guard
            let subjectPublicKeyInfo = Data(base64Encoded: publicKey),
            let plainText = payload.data(using: .utf8)
        else {
            return nil
        }

        let candidates = [
            Self.pkcs1PublicKey(from: subjectPublicKeyInfo),
            subjectPublicKeyInfo,
        ].compactMap { $0 }

        for keyData in candidates {
            let attributes: [CFString: Any] = [
                kSecAttrKeyType: kSecAttrKeyTypeRSA,
                kSecAttrKeyClass: kSecAttrKeyClassPublic,
            ]
            var keyError: Unmanaged<CFError>?
            guard let key = SecKeyCreateWithData(
                keyData as CFData,
                attributes as CFDictionary,
                &keyError
            ) else {
                _ = keyError?.takeRetainedValue()
                continue
            }
            guard SecKeyIsAlgorithmSupported(key, .encrypt, .rsaEncryptionPKCS1) else {
                continue
            }

            var encryptionError: Unmanaged<CFError>?
            if let encrypted = SecKeyCreateEncryptedData(
                key,
                .rsaEncryptionPKCS1,
                plainText as CFData,
                &encryptionError
            ) as Data? {
                return encrypted.base64EncodedString()
            }
            _ = encryptionError?.takeRetainedValue()
        }
        return nil
    }

    private static func pkcs1PublicKey(from subjectPublicKeyInfo: Data) -> Data? {
        let bytes = [UInt8](subjectPublicKeyInfo)
        var outerOffset = 0
        guard let outer = readElement(bytes, offset: &outerOffset, tag: 0x30) else {
            return nil
        }

        var contentOffset = outer.lowerBound
        guard readElement(bytes, offset: &contentOffset, tag: 0x30) != nil,
              let bitString = readElement(bytes, offset: &contentOffset, tag: 0x03),
              bitString.count > 1,
              bytes[bitString.lowerBound] == 0
        else {
            return nil
        }

        let keyStart = bitString.lowerBound + 1
        guard bytes[keyStart] == 0x30 else { return nil }
        return Data(bytes[keyStart..<bitString.upperBound])
    }

    private static func readElement(
        _ bytes: [UInt8],
        offset: inout Int,
        tag: UInt8
    ) -> Range<Int>? {
        guard offset < bytes.count, bytes[offset] == tag else { return nil }
        offset += 1
        guard offset < bytes.count else { return nil }

        let firstLength = Int(bytes[offset])
        offset += 1
        let length: Int
        if firstLength & 0x80 == 0 {
            length = firstLength
        } else {
            let byteCount = firstLength & 0x7f
            guard (1...4).contains(byteCount), offset + byteCount <= bytes.count else { return nil }
            var parsedLength = 0
            for _ in 0..<byteCount {
                parsedLength = (parsedLength << 8) | Int(bytes[offset])
                offset += 1
            }
            length = parsedLength
        }

        guard length >= 0, offset + length <= bytes.count else { return nil }
        let range = offset..<(offset + length)
        offset = range.upperBound
        return range
    }
}
