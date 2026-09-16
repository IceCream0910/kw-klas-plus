import Foundation

struct AcademicWidgetThemeColor: Codable, Equatable {
    var background: String
    var text: String
}

struct AcademicWidgetSubjectColor: Codable, Equatable {
    var palette: String
    var slot: Int
    var light: AcademicWidgetThemeColor
    var dark: AcademicWidgetThemeColor
}

struct AcademicWidgetClass: Codable, Equatable {
    var title: String
    var day: Int
    var startTime: String
    var endTime: String
    var info: String
    var color: AcademicWidgetSubjectColor
}

struct AcademicWidgetEvent: Codable, Equatable {
    var id: String
    var title: String
    var start: String
    var end: String
    var color: String
}

enum AcademicWidgetSyncStatus: String, Codable {
    case READY
    case NEEDS_LOGIN
    case RETRY
}

struct AcademicWidgetDisplay: Codable, Equatable {
    var schemaVersion: Int
    var owner: String
    var term: String
    var month: String
    var classes: [AcademicWidgetClass]?
    var timetableFetchedAt: Int64
    var events: [AcademicWidgetEvent]?
    var calendarFetchedAt: Int64
    var calendarStatus: AcademicWidgetSyncStatus
}

enum AcademicWidgetDisplayReader {
    static let schemaVersion = 1

    static func decode(_ json: String, ownerMarker: String) -> AcademicWidgetDisplay? {
        guard !ownerMarker.isEmpty,
              let data = json.data(using: .utf8),
              let display = try? JSONDecoder().decode(AcademicWidgetDisplay.self, from: data),
              display.schemaVersion == schemaVersion,
              !display.owner.isEmpty,
              display.owner == ownerMarker
        else { return nil }
        return display
    }
}
