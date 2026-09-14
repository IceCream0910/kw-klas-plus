import SwiftUI
import WidgetKit

struct AcademicTimetableProvider: TimelineProvider {
    func placeholder(in context: Context) -> AcademicWidgetEntry {
        AcademicWidgetEntry(date: Date(), load: .ready(AcademicWidgetPreview.timetable))
    }

    func getSnapshot(in context: Context, completion: @escaping (AcademicWidgetEntry) -> Void) {
        if context.isPreview {
            completion(AcademicWidgetEntry(date: Date(), load: .ready(AcademicWidgetPreview.timetable)))
            return
        }
        completion(AcademicWidgetEntry(date: Date(), load: AcademicWidgetStore.load()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<AcademicWidgetEntry>) -> Void) {
        let load = context.isPreview ? AcademicWidgetStore.LoadResult.ready(AcademicWidgetPreview.timetable)
            : AcademicWidgetStore.load()
        let now = Date()
        let classes: [AcademicWidgetClass]?
        if case .ready(let display) = load {
            classes = display.classes
        } else {
            classes = nil
        }
        let dates = AcademicWidgetTimeline.dates(now: now, classes: classes)
        let entries = dates.map { AcademicWidgetEntry(date: $0, load: load) }
        let policy: TimelineReloadPolicy
        if classes != nil {
            policy = .after(dates.last ?? now)
        } else {
            policy = .never
        }
        completion(Timeline(entries: entries, policy: policy))
    }
}

struct AcademicTimetableWidgetView: View {
    @Environment(\.colorScheme) private var colorScheme
    let entry: AcademicWidgetEntry

    var body: some View {
        let placeholder = AcademicWidgetPlaceholder.timetable(load: entry.load)
        Group {
            if placeholder.isEmpty, case .ready(let display) = entry.load, let classes = display.classes {
                timetableView(display, classes: classes)
            } else {
                placeholderView(placeholder.isEmpty ? "앱을 열어 가져오기" : placeholder)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(AcademicWidgetPalette.background(colorScheme))
        .widgetURL(AcademicWidgetURL.timetable)
        .modifier(AcademicWidgetChrome())
    }

    private func placeholderView(_ text: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("시간표")
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(AcademicWidgetPalette.text(colorScheme))
            Spacer()
            Text(text)
                .font(.system(size: 13))
                .foregroundStyle(AcademicWidgetPalette.secondary(colorScheme))
            Spacer()
        }
    }

    private func timetableView(_ display: AcademicWidgetDisplay, classes: [AcademicWidgetClass]) -> some View {
        let weekdays = AcademicWidgetLayoutPolicy.weekdays(classes)
        let weekends = AcademicWidgetLayoutPolicy.weekends(classes)
        return VStack(alignment: .leading, spacing: 8) {
            Text("시간표")
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(AcademicWidgetPalette.text(colorScheme))
            if weekdays.isEmpty {
                Spacer()
                Text("평일 수업이 없어요.")
                    .font(.system(size: 12))
                    .foregroundStyle(AcademicWidgetPalette.text(colorScheme))
                    .frame(maxWidth: .infinity)
                Spacer()
            } else {
                grid(weekdays)
            }
            if !weekends.isEmpty {
                VStack(alignment: .leading, spacing: 4) {
                    ForEach(weekends.prefix(3), id: \.title) { item in
                        Text(item.title)
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(AcademicWidgetPalette.secondary(colorScheme))
                            .lineLimit(1)
                    }
                }
            }
            Text(AcademicWidgetCopy.term(display.term))
                .font(.system(size: 11))
                .foregroundStyle(AcademicWidgetPalette.secondary(colorScheme))
                .frame(height: 24, alignment: .leading)
        }
    }

    private func grid(_ entries: [AcademicWidgetClass]) -> some View {
        let firstHour = max(0, min(23, (entries.map { AcademicWidgetLayoutPolicy.minutes($0.startTime) / 60 }.min() ?? 9)))
        let lastHour = max(firstHour + 1, min(24, (entries.map { (AcademicWidgetLayoutPolicy.minutes($0.endTime) + 59) / 60 }.max() ?? 17)))
        let labels = ["월", "화", "수", "목", "금"]
        let dark = colorScheme == .dark
        return GeometryReader { proxy in
            let left: CGFloat = 22
            let top: CGFloat = 18
            let cellW = (proxy.size.width - left) / 5
            let minuteH = (proxy.size.height - top) / CGFloat((lastHour - firstHour) * 60)
            ZStack(alignment: .topLeading) {
                ForEach(Array(labels.enumerated()), id: \.offset) { index, label in
                    Text(label)
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(AcademicWidgetPalette.text(colorScheme))
                        .frame(width: cellW, alignment: .leading)
                        .offset(x: left + CGFloat(index) * cellW + 4, y: 0)
                }
                ForEach(firstHour...lastHour, id: \.self) { hour in
                    let y = top + CGFloat((hour - firstHour) * 60) * minuteH
                    Path { path in
                        path.move(to: CGPoint(x: left, y: y))
                        path.addLine(to: CGPoint(x: proxy.size.width, y: y))
                    }
                    .stroke(AcademicWidgetPalette.secondary(colorScheme).opacity(0.35), lineWidth: 0.5)
                    if y < proxy.size.height - 10 {
                        Text("\(hour)")
                            .font(.system(size: 9))
                            .foregroundStyle(AcademicWidgetPalette.secondary(colorScheme))
                            .offset(x: 1, y: y)
                    }
                }
                ForEach(0...5, id: \.self) { day in
                    Path { path in
                        path.move(to: CGPoint(x: left + CGFloat(day) * cellW, y: top))
                        path.addLine(to: CGPoint(x: left + CGFloat(day) * cellW, y: proxy.size.height))
                    }
                    .stroke(AcademicWidgetPalette.secondary(colorScheme).opacity(0.35), lineWidth: 0.5)
                }
                ForEach(Array(entries.enumerated()), id: \.offset) { _, item in
                    let x = left + CGFloat(item.day) * cellW + 1
                    let y = top + CGFloat(AcademicWidgetLayoutPolicy.minutes(item.startTime) - firstHour * 60) * minuteH
                    let end = top + CGFloat(AcademicWidgetLayoutPolicy.minutes(item.endTime) - firstHour * 60) * minuteH
                    let height = max(end - y, 14)
                    VStack(alignment: .leading, spacing: 1) {
                        Text(item.title)
                            .font(.system(size: 10, weight: .bold))
                            .lineLimit(height >= 32 ? 2 : 1)
                        if height >= 28 {
                            Text(item.info.split(separator: "/").first.map(String.init) ?? item.info)
                                .font(.system(size: 9))
                                .lineLimit(1)
                        }
                    }
                    .foregroundStyle(AcademicWidgetPalette.ink(item.color, dark: dark))
                    .padding(4)
                    .frame(width: cellW - 2, height: height, alignment: .topLeading)
                    .background(AcademicWidgetPalette.fill(item.color, dark: dark))
                    .clipShape(RoundedRectangle(cornerRadius: 5))
                    .offset(x: x, y: y)
                }
            }
        }
    }
}

struct AcademicTimetableWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: AcademicWidgetKindID.timetable, provider: AcademicTimetableProvider()) { entry in
            AcademicTimetableWidgetView(entry: entry)
        }
        .configurationDisplayName("주간 시간표")
        .description("선택된 학기의 전체 시간표를 확인해보세요.")
        .supportedFamilies([.systemLarge, .systemExtraLarge])
        .contentMarginsDisabled()
    }
}
