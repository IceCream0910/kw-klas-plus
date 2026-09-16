import Shared
import WidgetKit

final class IosAcademicWidgetReloader: AcademicWidgetTimelineReloader {
    func reload() {
        WidgetCenter.shared.reloadTimelines(ofKind: AcademicWidgetKind.calendar)
        WidgetCenter.shared.reloadTimelines(ofKind: AcademicWidgetKind.timetable)
    }
}

enum AcademicWidgetKind {
    static let calendar = "AcademicCalendarWidget"
    static let timetable = "AcademicTimetableWidget"
}
