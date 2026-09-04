import Foundation

/// ADR-006에 따른 위젯 공유 스냅샷 데이터 모델
public struct LibraryWidgetSnapshot: Codable, Equatable {
    public let studentNumber: String
    public let userName: String
    public let department: String
    public let userCategory: String
    public let isConfigured: Bool
    public let isAppLockEnabled: Bool
    public let updatedAtMillis: Int64

    public init(
        studentNumber: String,
        userName: String,
        department: String,
        userCategory: String,
        isConfigured: Bool,
        isAppLockEnabled: Bool,
        updatedAtMillis: Int64
    ) {
        self.studentNumber = studentNumber
        self.userName = userName
        self.department = department
        self.userCategory = userCategory
        self.isConfigured = isConfigured
        self.isAppLockEnabled = isAppLockEnabled
        self.updatedAtMillis = updatedAtMillis
    }

    /// 앱 잠금 활성화 상태일 때 마스킹된 사용자 이름 반환 (예: "홍길동" -> "홍*동", "김철" -> "김*")
    public var maskedUserName: String {
        guard isAppLockEnabled else { return userName }
        let count = userName.count
        if count <= 1 { return userName }
        if count == 2 {
            let first = userName.prefix(1)
            return "\(first)*"
        }
        let first = userName.prefix(1)
        let last = userName.suffix(1)
        let middle = String(repeating: "*", count: count - 2)
        return "\(first)\(middle)\(last)"
    }

    /// 앱 잠금 활성화 상태일 때 마스킹된 학번 반환 (예: "2020202020" -> "2020******")
    public var maskedStudentNumber: String {
        guard isAppLockEnabled else { return studentNumber }
        if studentNumber.count <= 4 { return studentNumber }
        let prefix = studentNumber.prefix(4)
        let mask = String(repeating: "*", count: studentNumber.count - 4)
        return "\(prefix)\(mask)"
    }
}

/// ADR-006 위젯 및 도서관 출입증 정책 검증을 위한 Fixture 데이터셋
public enum LibraryQrWidgetFixtures {
    public static let appGroupId = "group.com.icecream.kwklasplus"
    public static let snapshotDefaultsKey = "klas_library_widget_snapshot"

    public static let deepLinkQr = URL(string: "kwklasplus://library-qr")!
    public static let deepLinkSettings = URL(string: "kwklasplus://library-qr/settings")!

    /// 정상 설정 완료된 학생증 스냅샷
    public static let normalConfiguredSnapshot = LibraryWidgetSnapshot(
        studentNumber: "2020202020",
        userName: "홍길동",
        department: "소프트웨어학부",
        userCategory: "학부생",
        isConfigured: true,
        isAppLockEnabled: false,
        updatedAtMillis: 1725379200000
    )

    /// 앱 잠금이 걸려 있어 정보 마스킹이 필요한 학생증 스냅샷
    public static let lockedSnapshot = LibraryWidgetSnapshot(
        studentNumber: "2020202020",
        userName: "홍길동",
        department: "소프트웨어학부",
        userCategory: "학부생",
        isConfigured: true,
        isAppLockEnabled: true,
        updatedAtMillis: 1725379200000
    )

    /// 미설정 상태의 학생증 스냅샷
    public static let unconfiguredSnapshot = LibraryWidgetSnapshot(
        studentNumber: "",
        userName: "",
        department: "",
        userCategory: "",
        isConfigured: false,
        isAppLockEnabled: false,
        updatedAtMillis: 0
    )

    /// KLAS 로그아웃 후 마스킹/초기화된 fallback 스냅샷
    public static let loggedOutFallbackSnapshot = LibraryWidgetSnapshot(
        studentNumber: "",
        userName: "",
        department: "",
        userCategory: "",
        isConfigured: false,
        isAppLockEnabled: false,
        updatedAtMillis: 1725379200000
    )

    /// ADR-006에 정의된 스냅샷 JSON 문자열
    public static let validSnapshotJson = """
    {
      "studentNumber": "2020202020",
      "userName": "홍길동",
      "department": "소프트웨어학부",
      "userCategory": "학부생",
      "isConfigured": true,
      "isAppLockEnabled": false,
      "updatedAtMillis": 1725379200000
    }
    """
}
