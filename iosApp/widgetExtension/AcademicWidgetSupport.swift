import Foundation
import SwiftUI

struct AcademicCalendarBar {
    let event: AcademicWidgetEvent
    let week: Int
    let column: Int
    let span: Int
    let lane: Int
}

enum AcademicWidgetLayoutPolicy {
    static func minutes(_ time: String) -> Int {
        let parts = time.split(separator: ":")
        let hour = Int(parts.first ?? "0") ?? 0
        let minute = Int(parts.dropFirst().first ?? "0") ?? 0
        return hour * 60 + minute
    }

    static func weekdays(_ classes: [AcademicWidgetClass]) -> [AcademicWidgetClass] {
        classes.filter { $0.day >= 0 && $0.day <= 4 }
    }

    static func weekends(_ classes: [AcademicWidgetClass]) -> [AcademicWidgetClass] {
        classes.filter { $0.day >= 5 && $0.day <= 6 }
            .sorted {
                if $0.day != $1.day { return $0.day < $1.day }
                let left = minutes($0.startTime)
                let right = minutes($1.startTime)
                if left != right { return left < right }
                return $0.title < $1.title
            }
    }

    static func agenda(
        events: [AcademicWidgetEvent],
        today: String
    ) -> (date: String, todayCount: Int, events: [AcademicWidgetEvent]) {
        let month = String(today.prefix(7))
        let monthEvents = events.filter {
            String($0.start.prefix(7)) <= month && String($0.end.prefix(7)) >= month
        }
        func on(_ date: String) -> [AcademicWidgetEvent] {
            monthEvents.filter {
                String($0.start.prefix(10)) <= date && String($0.end.prefix(10)) >= date
            }
            .sorted {
                if $0.start != $1.start { return $0.start < $1.start }
                return $0.title < $1.title
            }
        }
        let current = on(today)
        if !current.isEmpty { return (today, current.count, current) }
        let upcoming = monthEvents
            .map { String($0.start.prefix(10)) }
            .filter { $0 > today }
            .min()
        let previous = monthEvents
            .map { String($0.end.prefix(10)) }
            .filter { $0 < today }
            .max()
        let date = upcoming ?? previous ?? today
        return (date, 0, on(date))
    }

    static func monthBars(
        events: [AcademicWidgetEvent],
        month: String,
        daysInMonth: Int,
        sundayOffset: Int
    ) -> [AcademicCalendarBar] {
        let first = "\(month)-01"
        let last = "\(month)-\(String(format: "%02d", daysInMonth))"
        var bars: [AcademicCalendarBar] = []
        var occupied: [String: Int] = [:]
        let filtered = events.filter {
            String($0.start.prefix(10)) <= last && String($0.end.prefix(10)) >= first
        }
        .sorted {
            if $0.start != $1.start { return $0.start < $1.start }
            if $0.end != $1.end { return $0.end < $1.end }
            return $0.id < $1.id
        }
        for event in filtered {
            let fromDay = max(first, String(event.start.prefix(10)))
            let toDay = min(last, String(event.end.prefix(10)))
            let from = (Int(fromDay.suffix(2)) ?? 1) - 1 + sundayOffset
            let to = (Int(toDay.suffix(2)) ?? 1) - 1 + sundayOffset
            guard to >= from else { continue }
            for week in (from / 7)...(to / 7) {
                let column = max(from, week * 7) % 7
                let endColumn = min(to, week * 7 + 6) % 7
                let mask = ((1 << (endColumn - column + 1)) - 1) << column
                var lane = 0
                while (occupied["\(week)-\(lane)"] ?? 0) & mask != 0 {
                    lane += 1
                }
                occupied["\(week)-\(lane)"] = (occupied["\(week)-\(lane)"] ?? 0) | mask
                bars.append(
                    AcademicCalendarBar(
                        event: event,
                        week: week,
                        column: column,
                        span: endColumn - column + 1,
                        lane: lane
                    )
                )
            }
        }
        return bars
    }

    static func eventTime(_ event: AcademicWidgetEvent) -> String {
        let start = timeOf(event.start)
        let end = timeOf(event.end)
        if start == "00:00" && end == "23:59" { return "종일" }
        return start
    }

    private static func timeOf(_ value: String) -> String {
        guard value.count >= 16 else { return value }
        return String(value.dropFirst(11).prefix(5))
    }
}

enum AcademicWidgetCopy {
    private static let korean: Locale = Locale(identifier: "ko_KR")

    static func monthTitle(_ date: Date) -> String {
        formatted(date, "M월")
    }

    static func fullDate(_ date: Date) -> String {
        formatted(date, "M월 d일 EEEE")
    }

    static func updated(_ millis: Int64) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(millis) / 1000)
        return "\(formatted(date, "M/d HH:mm")) 업데이트"
    }

    static func term(_ value: String) -> String {
        value.replacingOccurrences(of: ",", with: "년 ") + "학기"
    }

    static func date(from yyyyMMdd: String) -> Date? {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = .current
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.date(from: String(yyyyMMdd.prefix(10)))
    }

    static func todayString(from date: Date = Date()) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = .current
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: date)
    }

    static func monthString(from date: Date = Date()) -> String {
        String(todayString(from: date).prefix(7))
    }

    private static func formatted(_ date: Date, _ format: String) -> String {
        let formatter = DateFormatter()
        formatter.locale = korean
        formatter.timeZone = .current
        formatter.dateFormat = format
        return formatter.string(from: date)
    }
}

enum AcademicWidgetPalette {
    static func background(_ scheme: ColorScheme) -> Color {
        Color(hex: scheme == .dark ? "#111214" : "#FFFFFF")
    }

    static func text(_ scheme: ColorScheme) -> Color {
        Color(hex: scheme == .dark ? "#F7F7F8" : "#191A1C")
    }

    static func secondary(_ scheme: ColorScheme) -> Color {
        Color(hex: scheme == .dark ? "#ADAEB5" : "#62646B")
    }

    static func sunday(_ scheme: ColorScheme) -> Color {
        Color(hex: scheme == .dark ? "#FF858C" : "#B3353F")
    }

    static func event(_ value: String, seed: String) -> Color {
        if value.range(of: "^#[0-9a-fA-F]{6}$", options: .regularExpression) != nil {
            return Color(hex: value)
        }
        let colors = ["#895275", "#426C95", "#467D6A", "#9E633D", "#76599A"]
        let slot = abs(seed.hashValue) % colors.count
        return Color(hex: colors[slot])
    }

    static func fill(_ color: AcademicWidgetSubjectColor, dark: Bool) -> Color {
        Color(hex: dark ? color.dark.background : color.light.background)
    }

    static func ink(_ color: AcademicWidgetSubjectColor, dark: Bool) -> Color {
        Color(hex: dark ? color.dark.text : color.light.text)
    }
}

extension Color {
    init(hex: String) {
        var value = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        if value.count == 6 { value = "FF" + value }
        var int: UInt64 = 0
        Scanner(string: value).scanHexInt64(&int)
        self.init(
            .sRGB,
            red: Double((int >> 16) & 0xFF) / 255,
            green: Double((int >> 8) & 0xFF) / 255,
            blue: Double(int & 0xFF) / 255,
            opacity: Double((int >> 24) & 0xFF) / 255
        )
    }
}

enum AcademicWidgetPlaceholder {
    static func calendar(load: AcademicWidgetStore.LoadResult, now: Date = Date()) -> String {
        switch load {
        case .unreadable:
            return "데이터를 열 수 없음"
        case .missing:
            return "앱을 열어 가져오기"
        case .ready(let display):
            if display.calendarStatus == .NEEDS_LOGIN {
                return "로그인 후 다시 확인해주세요"
            }
            if display.calendarStatus == .RETRY {
                return "일정을 불러오지 못했어요.\n앱을 열어 다시 확인해주세요."
            }
            if display.events == nil || display.month != AcademicWidgetCopy.monthString(from: now) {
                return "이번 달 일정을 불러오는 중이에요"
            }
            return ""
        }
    }

    static func timetable(load: AcademicWidgetStore.LoadResult) -> String {
        switch load {
        case .unreadable:
            return "데이터를 열 수 없음"
        case .missing:
            return "앱을 열어 가져오기"
        case .ready(let display):
            if display.classes == nil {
                return "앱에서 시간표를 불러와주세요"
            }
            return ""
        }
    }

    static func calendarStatus(_ display: AcademicWidgetDisplay, now: Date = Date()) -> String {
        let delayed = now.timeIntervalSince1970 * 1000 - Double(display.calendarFetchedAt) > 2 * 60 * 60 * 1000
        let label: String
        switch display.calendarStatus {
        case .NEEDS_LOGIN: label = "로그인 필요"
        case .RETRY: label = "갱신 지연"
        case .READY: label = delayed ? "갱신 지연" : ""
        }
        let updated = display.calendarFetchedAt > 0 ? AcademicWidgetCopy.updated(display.calendarFetchedAt) : ""
        return [label, updated].filter { !$0.isEmpty }.joined(separator: " ")
    }
}

enum AcademicWidgetTimeline {
    static func dates(now: Date, classes: [AcademicWidgetClass]?) -> [Date] {
        var dates = [now]
        let calendar = Calendar.current
        if let classes {
            let mondayBased = ((calendar.component(.weekday, from: now) + 5) % 7)
            for item in classes where item.day == mondayBased {
                if let start = timeToday(item.startTime, now: now, calendar: calendar),
                   start.timeIntervalSince(now) >= 300 {
                    dates.append(start)
                }
                if let end = timeToday(item.endTime, now: now, calendar: calendar),
                   end.timeIntervalSince(now) >= 300 {
                    dates.append(end)
                }
            }
        }
        if let midnight = calendar.nextDate(
            after: now,
            matching: DateComponents(hour: 0, minute: 0, second: 0),
            matchingPolicy: .nextTime
        ) {
            dates.append(midnight)
        }
        return Array(Set(dates)).sorted()
    }

    private static func timeToday(_ value: String, now: Date, calendar: Calendar) -> Date? {
        let minutes = AcademicWidgetLayoutPolicy.minutes(value)
        return calendar.date(bySettingHour: minutes / 60, minute: minutes % 60, second: 0, of: now)
    }
}

struct AcademicWidgetChrome: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 17.0, *) {
            content.containerBackground(for: .widget) {
                Color.clear
            }
        } else {
            content
        }
    }
}
