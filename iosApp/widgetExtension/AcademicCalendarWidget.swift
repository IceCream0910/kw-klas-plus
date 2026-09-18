import SwiftUI
import WidgetKit
import AppIntents

struct AcademicWidgetEntry: TimelineEntry {
    let date: Date
    let load: AcademicWidgetStore.LoadResult
}

struct AcademicCalendarProvider: TimelineProvider {
    func placeholder(in context: Context) -> AcademicWidgetEntry {
        AcademicWidgetEntry(date: AcademicWidgetPreview.calendarDate, load: .ready(AcademicWidgetPreview.calendar))
    }

    func getSnapshot(in context: Context, completion: @escaping (AcademicWidgetEntry) -> Void) {
        if context.isPreview {
            completion(AcademicWidgetEntry(date: AcademicWidgetPreview.calendarDate, load: .ready(AcademicWidgetPreview.calendar)))
            return
        }
        completion(AcademicWidgetEntry(date: Date(), load: AcademicWidgetStore.load()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<AcademicWidgetEntry>) -> Void) {
        if context.isPreview {
            completion(Timeline(
                entries: [AcademicWidgetEntry(date: AcademicWidgetPreview.calendarDate, load: .ready(AcademicWidgetPreview.calendar))],
                policy: .never
            ))
            return
        }
        let load = AcademicWidgetStore.load()
        let now = Date()
        let dates = AcademicWidgetTimeline.dates(now: now, classes: nil)
        let entries = dates.map { AcademicWidgetEntry(date: $0, load: load) }
        let policy: TimelineReloadPolicy
        if case .ready(let display) = load, display.events != nil {
            policy = .after(dates.last ?? now)
        } else {
            policy = .never
        }
        completion(Timeline(entries: entries, policy: policy))
    }
}

struct AcademicCalendarWidgetView: View {
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.widgetFamily) private var family
    let entry: AcademicWidgetEntry

    var body: some View {
        let placeholder = AcademicWidgetPlaceholder.calendar(load: entry.load, now: entry.date)
        Group {
            if placeholder.isEmpty, case .ready(let display) = entry.load {
                if family == .systemLarge || family == .systemExtraLarge {
                    monthView(display)
                } else {
                    agendaView(display)
                }
            } else {
                placeholderView(placeholder.isEmpty ? "앱을 열어 가져오기" : placeholder)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(AcademicWidgetPalette.background(colorScheme))
        .widgetURL(AcademicWidgetURL.calendar)
        .modifier(AcademicWidgetChrome())
    }

    private func placeholderView(_ text: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(AcademicWidgetCopy.monthTitle(entry.date))
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(AcademicWidgetPalette.text(colorScheme))
            Spacer()
            Text(text)
                .font(.system(size: 13))
                .foregroundStyle(AcademicWidgetPalette.secondary(colorScheme))
                .multilineTextAlignment(.leading)
            Spacer()
        }
    }

    private func agendaView(_ display: AcademicWidgetDisplay) -> some View {
        let agenda = AcademicWidgetLayoutPolicy.agenda(
            events: display.events ?? [],
            today: AcademicWidgetCopy.todayString(from: entry.date)
        )
        return VStack(alignment: .leading, spacing: 4) {
            Text(AcademicWidgetCopy.fullDate(entry.date))
                .font(.system(size: 16, weight: .bold))
                .foregroundStyle(AcademicWidgetPalette.text(colorScheme))
                .lineLimit(1)
            if agenda.todayCount == 0 {
                Text("오늘 일정이 없어요")
                    .font(.system(size: 13))
                    .foregroundStyle(AcademicWidgetPalette.secondary(colorScheme))
                    .padding(.top, 4)
            }
            if agenda.date != AcademicWidgetCopy.todayString(from: entry.date),
               let next = AcademicWidgetCopy.date(from: agenda.date) {
                Text(AcademicWidgetCopy.fullDate(next))
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(AcademicWidgetPalette.secondary(colorScheme))
                    .padding(.top, 6)
            }
            VStack(alignment: .leading, spacing: 6) {
                ForEach(agenda.events.prefix(family == .systemSmall ? 2 : 4), id: \.id) { event in
                    HStack(alignment: .center, spacing: 8) {
                        Capsule()
                            .fill(AcademicWidgetPalette.event(event.color, seed: event.id))
                            .frame(width: 3)
                        Text(AcademicWidgetLayoutPolicy.eventTime(event))
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(AcademicWidgetPalette.secondary(colorScheme))
                            .frame(width: 40, alignment: .leading)
                        Text(event.title)
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(AcademicWidgetPalette.text(colorScheme))
                            .lineLimit(1)
                    }
                    .frame(height: 22)
                }
            }
            Spacer(minLength: 0)
            footer(AcademicWidgetPlaceholder.calendarStatus(display, now: entry.date))
        }
    }

    private func monthView(_ display: AcademicWidgetDisplay) -> some View {
        let month = AcademicWidgetCopy.monthString(from: entry.date)
        let calendar = Calendar.current
        let days = calendar.range(of: .day, in: .month, for: entry.date)?.count ?? 30
        let first = calendar.date(from: calendar.dateComponents([.year, .month], from: entry.date)) ?? entry.date
        let sundayOffset = calendar.component(.weekday, from: first) - 1
        let bars = AcademicWidgetLayoutPolicy.monthBars(
            events: display.events ?? [],
            month: month,
            daysInMonth: days,
            sundayOffset: sundayOffset
        )
        let weeks = Int(ceil(Double(days + sundayOffset) / 7.0))
        let todayDay = calendar.component(.day, from: entry.date)
        let weekdayLabels = ["일", "월", "화", "수", "목", "금", "토"]
        return VStack(alignment: .leading, spacing: 6) {
            Text(AcademicWidgetCopy.monthTitle(entry.date))
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(AcademicWidgetPalette.text(colorScheme))
            HStack(spacing: 0) {
                ForEach(Array(weekdayLabels.enumerated()), id: \.offset) { index, label in
                    Text(label)
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(index == 0 ? AcademicWidgetPalette.sunday(colorScheme) : AcademicWidgetPalette.secondary(colorScheme))
                        .frame(maxWidth: .infinity)
                }
            }
            GeometryReader { proxy in
                let cellW = proxy.size.width / 7
                let cellH = proxy.size.height / CGFloat(max(weeks, 1))
                ZStack(alignment: .topLeading) {
                    ForEach(0..<days, id: \.self) { index in
                        let day = index + 1
                        let slot = index + sundayOffset
                        let x = CGFloat(slot % 7) * cellW
                        let y = CGFloat(slot / 7) * cellH
                        let isToday = day == todayDay
                        Text("\(day)")
                            .font(.system(size: 11, weight: isToday ? .bold : .regular))
                            .foregroundStyle(isToday ? AcademicWidgetPalette.background(colorScheme) : AcademicWidgetPalette.text(colorScheme))
                            .padding(.horizontal, 5)
                            .padding(.vertical, 2)
                            .background(isToday ? AcademicWidgetPalette.text(colorScheme) : Color.clear)
                            .clipShape(Capsule())
                            .position(x: x + 16, y: y + 10)
                    }
                    ForEach(Array(bars.enumerated()), id: \.offset) { _, bar in
                        let x = CGFloat(bar.column) * cellW + 2
                        let y = CGFloat(bar.week) * cellH + 20 + CGFloat(bar.lane) * 14
                        Text(bar.event.title)
                            .font(.system(size: 10, weight: .semibold))
                            .foregroundStyle(.white)
                            .lineLimit(1)
                            .padding(.horizontal, 4)
                            .frame(width: cellW * CGFloat(bar.span) - 4, height: 12, alignment: .leading)
                            .background(AcademicWidgetPalette.event(bar.event.color, seed: bar.event.id))
                            .clipShape(RoundedRectangle(cornerRadius: 3))
                            .offset(x: x, y: y)
                    }
                }
            }
            footer(AcademicWidgetPlaceholder.calendarStatus(display, now: entry.date))
        }
    }

    private func footer(_ status: String) -> some View {
        HStack {
            Text(status)
                .font(.system(size: 9))
                .foregroundStyle(AcademicWidgetPalette.secondary(colorScheme))
                .lineLimit(1)
            Spacer()
            refreshControl
        }
        .frame(height: 24)
    }

    @ViewBuilder
    private var refreshControl: some View {
        if #available(iOS 17.0, *) {
            Button(intent: AcademicCalendarRefreshIntent()) {
                refreshIcon
            }
            .buttonStyle(.plain)
            .invalidatableContent()
            .accessibilityLabel("새로고침")
        } else {
            Link(destination: AcademicWidgetURL.calendar) {
                refreshIcon
            }
            .accessibilityLabel("앱에서 새로고침")
        }
    }

    private var refreshIcon: some View {
        Image(systemName: "arrow.clockwise")
            .font(.system(size: 16, weight: .semibold))
            .foregroundStyle(AcademicWidgetPalette.secondary(colorScheme))
            .frame(width: 24, height: 24)
    }
}

struct AcademicCalendarWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: AcademicWidgetKindID.calendar, provider: AcademicCalendarProvider()) { entry in
            AcademicCalendarWidgetView(entry: entry)
        }
        .configurationDisplayName("캘린더")
        .description("이번 달 일정이 표시된 달력을 확인해보세요.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge, .systemExtraLarge])
        .contentMarginsDisabled()
    }
}

enum AcademicWidgetPreview {
    static let calendarDate: Date = {
        var components = DateComponents()
        components.year = 2026
        components.month = 9
        components.day = 10
        components.hour = 14
        components.minute = 47
        return Calendar.current.date(from: components) ?? Date()
    }()

    static let calendar = AcademicWidgetDisplay(
        schemaVersion: 1,
        owner: "preview",
        term: "2026,2",
        month: "2026-09",
        classes: nil,
        timetableFetchedAt: 0,
        events: [
            previewEvent("개강", start: "2026-09-01", end: "2026-09-01", color: "#895275"),
            previewEvent("", start: "2026-09-06", end: "2026-09-07", color: "#E57373"),
            previewEvent("프로젝트 발표회", start: "2026-09-16", end: "2026-09-18", color: "#6B2040"),
            previewEvent("중간발표", start: "2026-09-26", end: "2026-09-26", color: "#76599A"),
            previewEvent("스터디", start: "2026-09-30", end: "2026-09-30", color: "#895275"),
        ],
        calendarFetchedAt: Int64(calendarDate.timeIntervalSince1970 * 1000),
        calendarStatus: .READY
    )

    private static func previewEvent(
        _ title: String,
        start: String,
        end: String,
        color: String
    ) -> AcademicWidgetEvent {
        AcademicWidgetEvent(
            id: "\(start)|\(end)|\(title)",
            title: title,
            start: "\(start)T00:00",
            end: "\(end)T23:59",
            color: color
        )
    }

    static let timetable = AcademicWidgetDisplay(
        schemaVersion: 1,
        owner: "preview",
        term: "2026,2",
        month: AcademicWidgetCopy.monthString(),
        classes: [
            previewClass("자료구조", day: 0, start: "12:0", end: "13:0", slot: 7),
            previewClass("운영체제", day: 0, start: "13:0", end: "15:0", slot: 3),
            previewClass("컴퓨터네트워크", day: 0, start: "16:0", end: "18:0", slot: 1),
            previewClass("인공지능개론", day: 1, start: "9:0", end: "10:30", slot: 3),
            previewClass("대학영어", day: 1, start: "12:0", end: "13:0", info: "새빛301", slot: 5),
            previewClass("웹프로그래밍", day: 1, start: "15:0", end: "16:0", slot: 4),
            previewClass("선형대수", day: 2, start: "12:0", end: "13:0", slot: 6),
            previewClass("데이터베이스", day: 2, start: "15:0", end: "16:0", slot: 1),
            previewClass("컴퓨터네트워크", day: 3, start: "10:30", end: "12:0", slot: 3),
            previewClass("모바일프로그래밍", day: 3, start: "12:0", end: "13:30", slot: 0),
            previewClass("캡스톤디자인", day: 3, start: "13:30", end: "15:0", info: "새빛204", slot: 5),
            previewClass("알고리즘", day: 3, start: "16:0", end: "18:0", slot: 4),
        ],
        timetableFetchedAt: Int64(Date().timeIntervalSince1970 * 1000),
        events: nil,
        calendarFetchedAt: 0,
        calendarStatus: .READY
    )

    private static func previewClass(
        _ title: String,
        day: Int,
        start: String,
        end: String,
        info: String = "",
        slot: Int
    ) -> AcademicWidgetClass {
        AcademicWidgetClass(
            title: title,
            day: day,
            startTime: start,
            endTime: end,
            info: info,
            color: paletteColor(slot)
        )
    }

    private static func paletteColor(_ slot: Int) -> AcademicWidgetSubjectColor {
        let light = [
            ("#E0F4E8", "#16633D"),
            ("#E5EEFC", "#28569A"),
            ("#FFEFCF", "#805000"),
            ("#F2E6FC", "#713D92"),
            ("#FCE6EC", "#9C344B"),
            ("#DDF3F4", "#00656E"),
            ("#E8EAFB", "#3949AB"),
            ("#F3E9E2", "#7A4328"),
        ]
        let dark = [
            ("#103822", "#54E995"),
            ("#192C48", "#91B9FF"),
            ("#3B2C12", "#FFC773"),
            ("#31203F", "#D3A7F5"),
            ("#41222B", "#FF9EB4"),
            ("#16383C", "#78DBE4"),
            ("#292B55", "#B8C0FF"),
            ("#3A2921", "#F2B58E"),
        ]
        let index = min(max(slot, 0), light.count - 1)
        return AcademicWidgetSubjectColor(
            palette: "default-v1",
            slot: index,
            light: AcademicWidgetThemeColor(background: light[index].0, text: light[index].1),
            dark: AcademicWidgetThemeColor(background: dark[index].0, text: dark[index].1)
        )
    }
}
