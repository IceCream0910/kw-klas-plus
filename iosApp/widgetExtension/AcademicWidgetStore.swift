import Foundation

enum AcademicWidgetStore {
    static let appGroup = "group.com.icecream.kwklasplus"
    static let fileName = "academic_widget_display_v1.json"
    static let ownerKey = "academic_widget_owner"

    enum LoadResult {
        case missing
        case unreadable
        case ready(AcademicWidgetDisplay)
    }

    static func load() -> LoadResult {
        guard let defaults = UserDefaults(suiteName: appGroup),
              let marker = defaults.string(forKey: ownerKey),
              !marker.isEmpty,
              let container = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroup)
        else { return .missing }
        let file = container.appendingPathComponent(fileName)
        guard FileManager.default.fileExists(atPath: file.path) else { return .missing }
        do {
            let json = try String(contentsOf: file, encoding: .utf8)
            guard let display = AcademicWidgetDisplayReader.decode(json, ownerMarker: marker) else {
                return .unreadable
            }
            return .ready(display)
        } catch {
            return .unreadable
        }
    }
}

enum AcademicWidgetKindID {
    static let calendar = "AcademicCalendarWidget"
    static let timetable = "AcademicTimetableWidget"
}

enum AcademicWidgetURL {
    static let timetable = URL(string: "kwklasplus://widget/timetable")!
    static let calendar = URL(string: "kwklasplus://widget/calendar")!
}
