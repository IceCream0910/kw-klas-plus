import SwiftUI
import WidgetKit

enum LibraryQRWidgetKind {
    static let id = "LibraryQRWidget"
}

struct LibraryQREntry: TimelineEntry {
    let date: Date
}

struct LibraryQRProvider: TimelineProvider {
    func placeholder(in context: Context) -> LibraryQREntry {
        LibraryQREntry(date: Date())
    }

    func getSnapshot(in context: Context, completion: @escaping (LibraryQREntry) -> Void) {
        completion(LibraryQREntry(date: Date()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<LibraryQREntry>) -> Void) {
        completion(Timeline(entries: [LibraryQREntry(date: Date())], policy: .never))
    }
}

struct LibraryQRWidgetView: View {
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        Image(colorScheme == .dark ? "QrWidgetDark" : "QrWidgetLight")
            .resizable()
            .scaledToFit()
            .padding(4)
            .widgetURL(URL(string: "kwklasplus://library-qr"))
            .modifier(LibraryQRWidgetBackground())
    }
}

private struct LibraryQRWidgetBackground: ViewModifier {
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

struct LibraryQRWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: LibraryQRWidgetKind.id, provider: LibraryQRProvider()) { _ in
            LibraryQRWidgetView()
        }
        .configurationDisplayName("도서관 출입증")
        .description("중앙도서관 출입증 QR 코드를 홈 화면에서 빠르게 열 수 있어요.")
        .supportedFamilies([.systemSmall])
    }
}

@main
struct LibraryQRWidgetBundle: WidgetBundle {
    var body: some Widget {
        LibraryQRWidget()
    }
}
