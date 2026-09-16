import SwiftUI
import WidgetKit

@main
struct KlasWidgetBundle: WidgetBundle {
    var body: some Widget {
        LibraryQRWidget()
        AcademicCalendarWidget()
        AcademicTimetableWidget()
    }
}
