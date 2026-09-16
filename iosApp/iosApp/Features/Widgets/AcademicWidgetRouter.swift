import Foundation

enum AcademicWidgetRouter {
    static let timetableURL = URL(string: "kwklasplus://widget/timetable")!
    static let calendarURL = URL(string: "kwklasplus://widget/calendar")!

    static func tab(from url: URL) -> String? {
        guard url.scheme == "kwklasplus",
              url.host == "widget",
              url.user == nil,
              url.password == nil,
              url.query == nil,
              url.fragment == nil
        else { return nil }
        switch url.path {
        case "/timetable": return "timetable"
        case "/calendar": return "calendar"
        default: return nil
        }
    }
}
